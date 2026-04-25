"""Shared embedding utilities for RAG baselines."""

from typing import Any, List
from llama_index.core.embeddings import BaseEmbedding
from llama_index.embeddings.gemini import GeminiEmbedding


class FixedDimensionGeminiEmbedding(BaseEmbedding):
    """Wrapper for GeminiEmbedding that forces 768 dimensions by truncating.

    This embedding class ensures consistent vector dimensions across all RAG implementations
    by truncating Gemini embeddings to 768 dimensions, which is compatible with pgvector.
    """

    def __init__(self, api_key: str, model_name: str = "models/gemini-embedding-001"):
        super().__init__()
        # Initialize the base embedding model
        self._base_embedding = GeminiEmbedding(api_key=api_key, model_name=model_name)
        self._target_dim = 768

    def _get_query_embedding(self, query: str) -> List[float]:
        """Get query embedding and truncate to 768 dimensions."""
        embedding = self._base_embedding._get_query_embedding(query)
        return self._truncate_embedding(embedding)

    def _get_text_embedding(self, text: str) -> List[float]:
        """Get text embedding and truncate to 768 dimensions."""
        embedding = self._base_embedding._get_text_embedding(text)
        return self._truncate_embedding(embedding)

    async def _aget_query_embedding(self, query: str) -> List[float]:
        """Async version of get query embedding and truncate to 768 dimensions."""
        embedding = await self._base_embedding._aget_query_embedding(query)
        return self._truncate_embedding(embedding)

    async def _aget_text_embedding(self, text: str) -> List[float]:
        """Async version of get text embedding and truncate to 768 dimensions."""
        embedding = await self._base_embedding._aget_text_embedding(text)
        return self._truncate_embedding(embedding)

    def _truncate_embedding(self, embedding: List[float]) -> List[float]:
        """Truncate embedding to target dimension."""
        if len(embedding) <= self._target_dim:
            return embedding
        return embedding[:self._target_dim]

    def _get_text_embeddings(self, texts: List[str]) -> List[List[float]]:
        """Get multiple text embeddings and truncate each to 768 dimensions."""
        embeddings = self._base_embedding._get_text_embeddings(texts)
        return [self._truncate_embedding(embedding) for embedding in embeddings]

    def _get_query_embeddings(self, queries: List[str]) -> List[List[float]]:
        """Get multiple query embeddings and truncate each to 768 dimensions."""
        embeddings = self._base_embedding._get_query_embeddings(queries)
        return [self._truncate_embedding(embedding) for embedding in embeddings]

    async def _aget_text_embeddings(self, texts: List[str]) -> List[List[float]]:
        """Async version of get multiple text embeddings and truncate each to 768 dimensions."""
        embeddings = await self._base_embedding._aget_text_embeddings(texts)
        return [self._truncate_embedding(embedding) for embedding in embeddings]

    async def _aget_query_embeddings(self, queries: List[str]) -> List[List[float]]:
        """Async version of get multiple query embeddings and truncate each to 768 dimensions."""
        embeddings = await self._base_embedding._aget_query_embeddings(queries)
        return [self._truncate_embedding(embedding) for embedding in embeddings]
