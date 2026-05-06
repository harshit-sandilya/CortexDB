"""Abstract base class for all RAG benchmark runners."""

from abc import ABC, abstractmethod
import time
import os
from dotenv import load_dotenv


class BaseRAGRunner(ABC):
    """Interface that all RAG architectures must implement for benchmarking.

    Each runner manages its own vector store (pgvector table) and exposes
    a uniform ingest/retrieve/teardown lifecycle.
    """

    def __init__(self, name: str):
        self.name = name

    @abstractmethod
    def ingest(self, documents: list[str]) -> None:
        """Ingest a list of text chunks into the runner's vector store.

        Args:
            documents: List of text strings to embed and store.
        """

    @abstractmethod
    def retrieve(self, query: str, k: int = 5) -> list[str]:
        """Retrieve the top-K most relevant chunks for the given query.

        Args:
            query: The search query.
            k: Number of results to return.

        Returns:
            List of document text strings, ordered by relevance (best first).
        """

    @abstractmethod
    def teardown(self) -> None:
        """Clean up resources (drop pgvector tables, close connections, etc.)."""

    def generate_answer(self, query: str, retrieved_chunks: list[str]) -> str:
        """Generate an answer from retrieved chunks using the configured LLM.

        Baselines use a simple RAG prompt: stuff all retrieved chunks into
        context and ask the LLM to answer. CortexDB overrides this to use
        its enriched entity/relation context via the backend router.

        Args:
            query: The user's question.
            retrieved_chunks: List of retrieved text strings.

        Returns:
            Generated answer string.
        """
        load_dotenv()
        provider = os.getenv("LLM_PROVIDER", "GEMINI").upper()
        api_key = os.getenv("LLM_API_KEY")
        if not api_key:
            return "[ERROR: No API key for answer generation]"

        context = "\n".join(f"- {chunk}" for chunk in retrieved_chunks)
        prompt = f"""Answer the user's question using ONLY the provided context.
If the context does not contain enough information, say "I don't know based on the available context."

Context:
{context}

Question: "{query}"
"""

        try:
            if provider == "CUSTOM":
                return self._generate_with_openai_proxy(prompt, api_key)
            else:
                return self._generate_with_gemini(prompt, api_key)
        except Exception as e:
            return f"[Answer generation failed: {e}]"

    def _generate_with_openai_proxy(self, prompt: str, api_key: str) -> str:
        """Generate answer using OpenAI-compatible proxy (/v1/chat/completions)."""
        import openai

        base_url = os.getenv("LLM_BASE_URL", "http://dummy-llm-endpoint.local")
        chat_model = os.getenv("LLM_CHAT_MODEL", "devstral-2-123b")

        client = openai.OpenAI(
            api_key=api_key,
            base_url=f"{base_url}/v1",
            timeout=30.0,
        )
        response = client.chat.completions.create(
            model=chat_model,
            max_tokens=1024,
            messages=[{"role": "user", "content": prompt}],
        )
        return response.choices[0].message.content.strip()

    def _generate_with_gemini(self, prompt: str, api_key: str) -> str:
        """Generate answer using Gemini (default fallback)."""
        import google.generativeai as genai

        genai.configure(api_key=api_key)
        model = genai.GenerativeModel("gemini-2.0-flash")
        response = model.generate_content(prompt)
        return response.text.strip()

    def timed_retrieve(self, query: str, k: int = 5) -> tuple[list[str], float]:
        """Retrieve with latency measurement.

        Returns:
            Tuple of (results, latency_ms).
        """
        start = time.perf_counter()
        results = self.retrieve(query, k)
        elapsed_ms = (time.perf_counter() - start) * 1000
        return results, elapsed_ms
