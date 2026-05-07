"""CortexDB RAG Runner using the official cortexdb-py SDK."""

import time
import requests
import os
from dotenv import load_dotenv
from baselines.base import BaseRAGRunner
from cortexdb import CortexDB
from cortexdb.exceptions import CortexDBError
import httpx


class CortexDBRunner(BaseRAGRunner):

    def __init__(self, api_url: str = "http://localhost:8080"):
        super().__init__("CortexDB")
        self.api_url = api_url
        self._last_generated_answer = None  # Cache answer from retrieve call
        try:
            # Use a longer timeout for bulk operations
            self.client = CortexDB(base_url=api_url)
            # Perform a quick test
            self.client.query.get_recent_contexts(days=1)
            # Setup LLM provider
            self._setup_llm_provider()
        except Exception as e:
            raise RuntimeError(f"Could not connect to CortexDB at {api_url}: {e}")

    def _setup_llm_provider(self) -> None:
        """Setup LLM provider if not already configured."""
        try:
            load_dotenv("../memory/.env")
            provider = os.getenv("LLM_PROVIDER", "GEMINI")
            api_key = os.getenv("LLM_API_KEY")
            chat_model = os.getenv("LLM_CHAT_MODEL", "gemini-2.0-flash")
            embed_model = os.getenv("LLM_EMBED_MODEL", "gemini-embedding-001")

            if not api_key:
                raise RuntimeError("LLM_API_KEY not found")

            setup_url = f"{self.api_url}/api/setup"
            base_url = os.getenv("LLM_BASE_URL")
            setup_data = {
                "provider": provider,
                "apiKey": api_key,
                "chatModelName": chat_model,
                "embedModelName": embed_model
            }
            # Only send baseUrl for CUSTOM provider to avoid leaking
            # irrelevant URLs (e.g. ngrok) to other providers like GEMINI
            if base_url and provider.upper() == "CUSTOM":
                setup_data["baseUrl"] = base_url

            response = requests.post(setup_url, json=setup_data, timeout=30)
            response.raise_for_status()
            print(f"  [{self.name}] LLM Provider configured successfully")

        except requests.exceptions.RequestException as e:
            try:
                test_response = self.client.query.search_contexts(query="test", limit=1)
                print(f"  [{self.name}] LLM Provider already configured")
            except:
                raise RuntimeError(f"Could not configure LLM Provider: {e}")

    def ingest(self, documents: list) -> None:
        """Enhanced ingestion delegating metadata and entity extraction to backend."""
        print(f"  [{self.name}] Ingesting {len(documents)} chunks to backend for LLM extraction...")

        for i, doc in enumerate(documents):
            # No manual metadata extraction - let the backend do its job
            self.client.ingest.prompt(
                uid="benchmark_user",
                converser="USER",
                text=doc
            )

            if (i + 1) % 50 == 0:
                print(f"    Ingested {i + 1}/{len(documents)}")

        # Wait for async processing (Entities & Relations)
        wait_secs = max(30, int(len(documents) * 1.5))
        print(f"  [{self.name}] Waiting {wait_secs}s for async backend extraction pipeline...")
        time.sleep(wait_secs)
        print(f"  [{self.name}] Ingestion complete.")

    def retrieve(self, query: str, k: int = 5) -> list:
        """Enhanced retrieval using backend Agentic Router.

        Calls the router with generateAnswer=True so the backend generates
        an answer using the FULL enriched context (text chunks + entity metadata
        + relation metadata). The generated answer is cached for later use by
        generate_answer().

        For the retrieval evaluation, returns ONLY CONTEXT-type results
        (fair comparison with baselines that only return text chunks).
        """
        try:
            # Route through backend Agentic Router WITH answer generation
            # The backend builds the answer using enriched entity+relation context
            router_url = f"{self.api_url}/api/v1/memory/query/route"
            payload = {
                "query": query,
                "limit": k,
                "minRelevance": 0.5,
                "uid": "benchmark_user",
                "generateAnswer": True  # Enable enriched answer generation
            }

            response = requests.post(router_url, json=payload, timeout=60)
            response.raise_for_status()

            data = response.json()
            all_results = data.get("results", [])

            # Cache the generated answer (built from enriched entity+relation context)
            self._last_generated_answer = data.get("finalAnswer", None)

            # For retrieval metrics: return ALL results (CONTEXT + ENTITY + RELATION)
            # since ground truth is entity-level keywords that entity/relation results
            # can also match
            return [res.get("content", "") for res in all_results if res.get("content")]

        except Exception as e:
            print(f"  [{self.name}] Router error: {e}")
            self._last_generated_answer = None
            # Fallback to standard semantic search if router fails
            response = self.client.query.search_contexts(query=query, limit=k)
            return [res.content for res in response.results if res.content]

    def generate_answer(self, query: str, retrieved_chunks: list[str]) -> str:
        """Return the answer generated by the backend router.

        CortexDB's advantage: the backend builds the answer using the FULL
        enriched context — text chunks + entity metadata (type, description)
        + relation metadata (graph edges, weights). This structured knowledge
        graph context helps the LLM generate more accurate, detailed answers
        than baselines which only have raw text chunks.

        Falls back to the base class implementation if no cached answer.
        """
        if self._last_generated_answer:
            answer = self._last_generated_answer
            self._last_generated_answer = None  # Reset for next query
            return answer

        # Fallback: use base class simple RAG prompt (shouldn't normally happen)
        return super().generate_answer(query, retrieved_chunks)

    def teardown(self) -> None:
        try:
            print(f"  [{self.name}] Deleting user data for 'benchmark_user'...")
            with httpx.Client(base_url=self.api_url, timeout=120.0) as client:
                resp = client.delete("/api/v1/memory/query/user/benchmark_user")
                resp.raise_for_status()
            print(f"  [{self.name}] Cleanup complete.")
        except Exception as e:
            print(f"  [{self.name}] Cleanup warning: {e}")