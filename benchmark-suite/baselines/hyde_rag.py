"""HyDE RAG baseline using LlamaIndex + pgvector.

First generates a hypothetical document based on the query using the LLM.
Then embed that hypothetical document and use its vector to retrieve from pgvector.
"""

import os
import sqlalchemy
from baselines.base import BaseRAGRunner
from dotenv import load_dotenv
import os

from llama_index.core import VectorStoreIndex, StorageContext, Document
from llama_index.core.settings import Settings
from llama_index.core.indices.query.query_transform import HyDEQueryTransform
from llama_index.core.query_engine import TransformQueryEngine
from llama_index.llms.gemini import Gemini
from llama_index.vector_stores.postgres import PGVectorStore
from baselines.embedding_utils import FixedDimensionGeminiEmbedding
import sqlalchemy
from typing import Any, List


TABLE_NAME = "benchmark_hyde_rag"


class HyDERAGRunner(BaseRAGRunner):

    def __init__(
        self,
        db_url: str,
        embed_model: str = "models/gemini-embedding-001",
        chat_model: str = "models/gemini-2.0-flash",
    ):
        super().__init__("HyDE")

        # Load environment variables
        load_dotenv()

        self.db_url = db_url

        api_key = os.getenv("LLM_API_KEY")
        if not api_key:
            raise ValueError("LLM_API_KEY environment variable is required")

        # Configure globals
        # Use our custom embedding wrapper that forces 768 dimensions
        fixed_embedding = FixedDimensionGeminiEmbedding(
            api_key=api_key,
            model_name=embed_model,
        )
        Settings.embed_model = fixed_embedding
        Settings.llm = Gemini(
            api_key=api_key,
            model=chat_model,
        )

        url = sqlalchemy.engine.url.make_url(db_url)
        self.vector_store = PGVectorStore.from_params(
            database=url.database,
            host=url.host,
            port=str(url.port or 5432),
            user=url.username,
            password=url.password,
            table_name=TABLE_NAME,
            embed_dim=768,
        )
        self.index = None

        # Setup HyDE
        self.hyde_transform = HyDEQueryTransform(include_original=True)

    def _ensure_vector_store_exists(self) -> None:
        """Ensure the vector store table exists (recreate if dropped by teardown)."""
        try:
            # Test if table exists by trying a simple query
            import sqlalchemy
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
                    embed_dim=768,
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
                # For subsequent batches, we need to ensure the table exists
                # Since teardown() drops the table, we may need to recreate the index structure
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

        # We must use query engine to utilize the transform properly.
        # But we only want retrieval, not response synthesis.
        # So we get the Retriever, query the transformed text, then get the nodes.
        
        # Actually... HyDEQueryTransform creates a hypothetical document string.
        query_bundle = self.hyde_transform(query)
        transformed_query_text = query_bundle.custom_embedding_strs[0]

        # Use the base retriever with the newly minted hypothetical document
        retriever = self.index.as_retriever(similarity_top_k=k)
        nodes = retriever.retrieve(transformed_query_text)

        return [node.get_content() for node in nodes]

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
