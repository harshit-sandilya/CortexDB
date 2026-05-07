"""Advanced RAG baseline using LlamaIndex + pgvector + Cohere Re-ranker.

Same pgvector retrieval as Naive RAG, but adds a Cohere re-ranker as a
node post-processor. Retrieves an initial top-20, then re-ranks to top-K.
"""

import os
from baselines.base import BaseRAGRunner
from dotenv import load_dotenv

from llama_index.core import VectorStoreIndex, StorageContext, Document
from llama_index.core.settings import Settings
from llama_index.core.postprocessor import SentenceTransformerRerank
from llama_index.vector_stores.postgres import PGVectorStore
from baselines.embedding_utils import get_embedding_model
import sqlalchemy
from typing import Any, List


TABLE_NAME = "benchmark_advanced_rag"

# How many raw results to fetch before re-ranking
INITIAL_RETRIEVAL_K = 20

class AdvancedRAGRunner(BaseRAGRunner):

    def __init__(
        self,
        db_url: str,
        embed_model: str = None,
    ):
        super().__init__("Advanced RAG (Re-ranker)")

        # Load environment variables
        load_dotenv()

        self.db_url = db_url
        # Use explicit param, then env var, then let the factory decide
        embed_model = embed_model or os.getenv("LLM_EMBED_MODEL")

        # Configure LlamaIndex global settings
        api_key = os.getenv("LLM_API_KEY")
        if not api_key:
            raise ValueError("LLM_API_KEY environment variable is required")

        # Use the factory to pick the right embedding model
        Settings.embed_model = get_embedding_model(api_key=api_key, embed_model_name=embed_model)

        url = sqlalchemy.engine.url.make_url(db_url)
        self.vector_store = PGVectorStore.from_params(
            database=url.database,
            host=url.host,
            port=str(url.port or 5432),
            user=url.username,
            password=url.password,
            table_name=TABLE_NAME,
            embed_dim=1024,
        )
        self.index = None

        # Local SentenceTransformer re-ranker
        self.reranker = SentenceTransformerRerank(
            model="cross-encoder/ms-marco-MiniLM-L-2-v2",
            top_n=5,  # Will be overridden per-query by k
        )

    def _ensure_vector_store_exists(self) -> None:
        """Ensure the vector store table exists (recreate if dropped by teardown)."""
        try:
            # Test if table exists by trying a simple query
            engine = sqlalchemy.create_engine(self.db_url)
            with engine.connect() as conn:
                # Check if our table exists
                table_name = f"data_{TABLE_NAME}"
                result = conn.execute(sqlalchemy.text(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = :table_name)",
                ), {"table_name": table_name})
                table_exists = result.scalar()

            if not table_exists:
                # Table was dropped, recreate the vector store
                print(f"    Recreating vector store table...")
                url = sqlalchemy.engine.url.make_url(self.db_url)
                self.vector_store = PGVectorStore.from_params(
                    database=url.database,
                    host=url.host,
                    port=str(url.port or 5432),
                    user=url.username,
                    password=url.password,
                    table_name=TABLE_NAME,
                    embed_dim=1024,
                )
                print(f"    Vector store recreated")
        except Exception as e:
            print(f"    Warning: Could not verify vector store table: {e}")
            # Continue anyway - LlamaIndex will create the table if needed

    def ingest(self, documents: list) -> None:
        BATCH_SIZE = 50
        print(f"  [{self.name}] Ingesting {len(documents)} chunks (batch_size={BATCH_SIZE})...")

        # Ensure we have a fresh vector store (in case it was dropped by teardown)
        self._ensure_vector_store_exists()
        storage_context = StorageContext.from_defaults(vector_store=self.vector_store)

        for i in range(0, len(documents), BATCH_SIZE):
            batch = documents[i:i + BATCH_SIZE]
            docs = [Document(text=text) for text in batch]

            if self.index is None:
                self.index = VectorStoreIndex.from_documents(
                    docs,
                    storage_context=storage_context,
                    show_progress=True,
                )
            else:
                try:
                    for doc in docs:
                        self.index.insert(doc)
                except Exception as e:
                    if "does not exist" in str(e):
                        # Table was dropped (e.g., by teardown after safety check)
                        # Recreate index with current batch
                        print(f"    Recreating index structure for batch {i // BATCH_SIZE + 1}...")
                        self.index = VectorStoreIndex.from_documents(
                            docs,
                            storage_context=storage_context,
                            show_progress=False,
                        )
                    else:
                        raise

            print(f"    Batch {i // BATCH_SIZE + 1}: inserted {min(i + BATCH_SIZE, len(documents))}/{len(documents)}")

        print(f"  [{self.name}] Ingestion complete.")


    def retrieve(self, query: str, k: int = 5) -> list[str]:
        if self.index is None:
            raise RuntimeError("Must call ingest() before retrieve()")

        # Update re-ranker top_n to match requested k
        self.reranker.top_n = k

        # Build query engine with re-ranker post-processor
        retriever = self.index.as_retriever(similarity_top_k=INITIAL_RETRIEVAL_K)
        nodes = retriever.retrieve(query)

        # Apply Local re-ranking
        reranked = self.reranker.postprocess_nodes(nodes, query_str=query)
        return [node.get_content() for node in reranked]

    def teardown(self) -> None:
        try:
            engine = sqlalchemy.create_engine(self.db_url)
            with engine.connect() as conn:
                conn.execute(sqlalchemy.text(f"DROP TABLE IF EXISTS data_{TABLE_NAME} CASCADE"))
                conn.commit()
            engine.dispose()
            print(f"  [{self.name}] Cleaned up table data_{TABLE_NAME}")
        except Exception as e:
            print(f"  [{self.name}] Cleanup warning: {e}")
