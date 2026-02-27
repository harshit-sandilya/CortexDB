"""Main CortexDB client — entry point for the Python SDK."""

from __future__ import annotations

import httpx

from cortexdb.ingest import IngestAPI
from cortexdb.query import QueryAPI
from cortexdb.setup import SetupAPI


class CortexDB:
    """Client for interacting with a CortexDB server.

    Usage::

        from cortexdb import CortexDB

        db = CortexDB("http://localhost:8080")

        # Configure LLM
        db.setup.configure(
            provider="GEMINI",
            api_key="...",
            chat_model="gemini-2.0-flash",
            embed_model="gemini-embedding-001",
        )

        # Ingest content
        db.ingest.document(uid="user-1", converser="USER", content="Hello world")

        # Query
        results = db.query.search_contexts("greeting")
    """

    def __init__(
        self,
        base_url: str = "http://localhost:8080",
        timeout: float = 30.0,
        headers: dict[str, str] | None = None,
    ) -> None:
        """Initialize the CortexDB client.

        Args:
            base_url: Base URL of the CortexDB server.
            timeout: Request timeout in seconds.
            headers: Optional default headers for all requests.
        """
        self._http = httpx.Client(
            base_url=base_url,
            timeout=timeout,
            headers=headers or {},
        )

        self.setup = SetupAPI(self._http)
        self.ingest = IngestAPI(self._http)
        self.query = QueryAPI(self._http)

    def close(self) -> None:
        """Close the underlying HTTP client."""
        self._http.close()

    def __enter__(self) -> CortexDB:
        return self

    def __exit__(self, *args: object) -> None:
        self.close()

    def __repr__(self) -> str:
        return f"CortexDB(base_url={self._http.base_url!r})"
