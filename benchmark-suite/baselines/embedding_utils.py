"""Shared embedding utilities for RAG baselines.

Supports two embedding backends:
  - CUSTOM provider: OpenAI-compatible proxy API (bge-m3, 1024 dims)
  - Gemini (default): GeminiEmbedding with truncation to 1024 dims
"""

import os
from typing import List
from llama_index.core.embeddings import BaseEmbedding


class OpenAICompatibleProxyEmbedding(BaseEmbedding):
    """Embedding via an OpenAI-compatible proxy endpoint (/v1/embeddings).

    Used when LLM_PROVIDER=CUSTOM. The proxy exposes bge-m3 which produces
    1024-dimensional vectors natively.
    """

    def __init__(
        self,
        api_key: str,
        base_url: str,
        model_name: str = "bge-m3",
    ):
        super().__init__()
        import openai
        self._client = openai.OpenAI(api_key=api_key, base_url=f"{base_url}/v1", timeout=30.0)
        self._model_name = model_name

    def _embed(self, texts: List[str]) -> List[List[float]]:
        response = self._client.embeddings.create(input=texts, model=self._model_name)
        return [d.embedding for d in response.data]

    def _get_query_embedding(self, query: str) -> List[float]:
        return self._embed([query])[0]

    def _get_text_embedding(self, text: str) -> List[float]:
        return self._embed([text])[0]

    async def _aget_query_embedding(self, query: str) -> List[float]:
        return self._get_query_embedding(query)

    async def _aget_text_embedding(self, text: str) -> List[float]:
        return self._get_text_embedding(text)

    def _get_text_embeddings(self, texts: List[str]) -> List[List[float]]:
        return self._embed(texts)

    def _get_query_embeddings(self, queries: List[str]) -> List[List[float]]:
        return self._embed(queries)

    async def _aget_text_embeddings(self, texts: List[str]) -> List[List[float]]:
        return self._get_text_embeddings(texts)

    async def _aget_query_embeddings(self, queries: List[str]) -> List[List[float]]:
        return self._get_query_embeddings(queries)


class FixedDimensionGeminiEmbedding(BaseEmbedding):
    """Wrapper for GeminiEmbedding that forces 1024 dimensions by truncating.

    This embedding class ensures consistent vector dimensions across all RAG implementations
    by truncating Gemini embeddings to 1024 dimensions, which is compatible with pgvector.
    """

    def __init__(self, api_key: str, model_name: str = "models/gemini-embedding-001"):
        super().__init__()
        from llama_index.embeddings.gemini import GeminiEmbedding
        # Initialize the base embedding model
        self._base_embedding = GeminiEmbedding(api_key=api_key, model_name=model_name)
        self._target_dim = 1024

    def _get_query_embedding(self, query: str) -> List[float]:
        """Get query embedding and truncate to 1024 dimensions."""
        embedding = self._base_embedding._get_query_embedding(query)
        return self._truncate_embedding(embedding)

    def _get_text_embedding(self, text: str) -> List[float]:
        """Get text embedding and truncate to 1024 dimensions."""
        embedding = self._base_embedding._get_text_embedding(text)
        return self._truncate_embedding(embedding)

    async def _aget_query_embedding(self, query: str) -> List[float]:
        """Async version of get query embedding and truncate to 1024 dimensions."""
        embedding = await self._base_embedding._aget_query_embedding(query)
        return self._truncate_embedding(embedding)

    async def _aget_text_embedding(self, text: str) -> List[float]:
        """Async version of get text embedding and truncate to 1024 dimensions."""
        embedding = await self._base_embedding._aget_text_embedding(text)
        return self._truncate_embedding(embedding)

    def _truncate_embedding(self, embedding: List[float]) -> List[float]:
        """Truncate embedding to target dimension."""
        if len(embedding) <= self._target_dim:
            return embedding
        return embedding[:self._target_dim]

    def _get_text_embeddings(self, texts: List[str]) -> List[List[float]]:
        """Get multiple text embeddings and truncate each to 1024 dimensions."""
        embeddings = self._base_embedding._get_text_embeddings(texts)
        return [self._truncate_embedding(embedding) for embedding in embeddings]

    def _get_query_embeddings(self, queries: List[str]) -> List[List[float]]:
        """Get multiple query embeddings and truncate each to 1024 dimensions."""
        embeddings = self._base_embedding._get_query_embeddings(queries)
        return [self._truncate_embedding(embedding) for embedding in embeddings]

    async def _aget_text_embeddings(self, texts: List[str]) -> List[List[float]]:
        """Async version of get multiple text embeddings and truncate each to 1024 dimensions."""
        embeddings = await self._base_embedding._aget_text_embeddings(texts)
        return [self._truncate_embedding(embedding) for embedding in embeddings]

    async def _aget_query_embeddings(self, queries: List[str]) -> List[List[float]]:
        """Async version of get multiple query embeddings and truncate each to 1024 dimensions."""
        embeddings = await self._base_embedding._aget_query_embeddings(queries)
        return [self._truncate_embedding(embedding) for embedding in embeddings]


def get_embedding_model(api_key: str = None, embed_model_name: str = None) -> BaseEmbedding:
    """Factory function that returns the correct embedding model based on LLM_PROVIDER.

    - CUSTOM: OpenAI-compatible proxy (bge-m3, 1024-dim)
    - All others: Gemini embedding with dimension truncation
    """
    provider = os.getenv("LLM_PROVIDER", "GEMINI").upper()

    if provider == "CUSTOM":
        base_url = os.getenv("LLM_BASE_URL", "http://dummy-llm-endpoint.local")
        model_name = embed_model_name or "bge-m3"
        print(f"  [Embedding] Using proxy embedding: {model_name} at {base_url} (1024-dim)")
        return OpenAICompatibleProxyEmbedding(
            api_key=api_key or os.getenv("LLM_API_KEY", ""),
            base_url=base_url,
            model_name=model_name,
        )
    else:
        if not api_key:
            raise ValueError("API key required for Gemini embeddings")
        model_name = embed_model_name or "models/gemini-embedding-001"
        print(f"  [Embedding] Using Gemini embedding: {model_name}")
        return FixedDimensionGeminiEmbedding(api_key=api_key, model_name=model_name)
