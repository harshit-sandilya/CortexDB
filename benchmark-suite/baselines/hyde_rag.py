"""HyDE RAG baseline using LlamaIndex + pgvector.

First generates a hypothetical document based on the query using the LLM.
Then embed that hypothetical document and use its vector to retrieve from pgvector.
"""

import os
import sqlalchemy
from baselines.base import BaseRAGRunner
from dotenv import load_dotenv

from llama_index.core import VectorStoreIndex, StorageContext, Document
from llama_index.core.settings import Settings
from llama_index.core.indices.query.query_transform import HyDEQueryTransform
from llama_index.core.query_engine import TransformQueryEngine
from llama_index.vector_stores.postgres import PGVectorStore
from baselines.embedding_utils import get_embedding_model
from typing import Any, List


TABLE_NAME = "benchmark_hyde_rag"


def _get_llm():
    """Return the correct LlamaIndex LLM based on LLM_PROVIDER."""
    provider = os.getenv("LLM_PROVIDER", "GEMINI").upper()

    if provider == "CUSTOM":
        from llama_index.llms.openai import OpenAI

        # Patch model-name validation so non-OpenAI models (e.g. devstral)
        # served behind an OpenAI-compatible proxy don't raise ValueError.
        # Must patch BOTH utils AND base, because base.py does a direct
        # "from ... import openai_modelname_to_contextsize".
        import llama_index.llms.openai.utils as _oai_utils
        import llama_index.llms.openai.base as _oai_base
        _orig_contextsize = _oai_utils.openai_modelname_to_contextsize
        def _safe_contextsize(modelname: str) -> int:
            try:
                return _orig_contextsize(modelname)
            except ValueError:
                return 32768  # sensible default for custom models
        _oai_utils.openai_modelname_to_contextsize = _safe_contextsize
        _oai_base.openai_modelname_to_contextsize = _safe_contextsize

        # Also patch tiktoken so it falls back to cl100k_base for unknown models
        import tiktoken
        _orig_encoding_for_model = tiktoken.encoding_for_model
        def _safe_encoding_for_model(model_name: str):
            try:
                return _orig_encoding_for_model(model_name)
            except KeyError:
                return tiktoken.get_encoding("cl100k_base")
        tiktoken.encoding_for_model = _safe_encoding_for_model
        _oai_base.tiktoken.encoding_for_model = _safe_encoding_for_model

        # Patch is_chat_model so unknown models use /v1/chat/completions
        # (not the legacy /v1/completions which most proxies don't serve).
        def _safe_is_chat(model: str) -> bool:
            return True  # Custom proxy models are always chat models
        _oai_utils.is_chat_model = _safe_is_chat
        _oai_base.is_chat_model = _safe_is_chat

        base_url = os.getenv("LLM_BASE_URL", "http://dummy-llm-endpoint.local")
        api_key = os.getenv("LLM_API_KEY")
        chat_model = os.getenv("LLM_CHAT_MODEL", "devstral-2-123b")

        return OpenAI(
            api_key=api_key,
            model=chat_model,
            api_base=f"{base_url}/v1",
            max_tokens=4096,
        )
    else:
        from llama_index.llms.gemini import Gemini

        api_key = os.getenv("LLM_API_KEY")
        chat_model = os.getenv("LLM_CHAT_MODEL", "models/gemini-2.0-flash")

        return Gemini(
            api_key=api_key,
            model=chat_model,
        )


class HyDERAGRunner(BaseRAGRunner):

    def __init__(
        self,
        db_url: str,
        embed_model: str = None,
        chat_model: str = None,
    ):
        super().__init__("HyDE")

        # Load environment variables
        load_dotenv()

        self.db_url = db_url
        # Use explicit param, then env var, then let the factory decide
        embed_model = embed_model or os.getenv("LLM_EMBED_MODEL")

        api_key = os.getenv("LLM_API_KEY")
        if not api_key:
            raise ValueError("LLM_API_KEY environment variable is required")

        # Configure globals — use provider-aware factories
        Settings.embed_model = get_embedding_model(api_key=api_key, embed_model_name=embed_model)
        Settings.llm = _get_llm()

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
