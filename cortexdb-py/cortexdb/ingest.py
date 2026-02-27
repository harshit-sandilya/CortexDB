"""Ingest API wrapper for CortexDB."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from cortexdb.models import ConverserRole, IngestRequest, IngestResponse

if TYPE_CHECKING:
    import httpx


class IngestAPI:
    """Wraps the ``/api/ingest`` endpoints."""

    def __init__(self, http: httpx.Client) -> None:
        self._http = http

    def document(
        self,
        uid: str,
        converser: str | ConverserRole,
        content: str,
        metadata: dict[str, Any] | None = None,
    ) -> IngestResponse:
        """Ingest a document into CortexDB.

        The server will chunk the content, generate embeddings,
        and extract entities/relations automatically.

        Args:
            uid: User identifier.
            converser: Role of the converser (USER, AGENT, or SYSTEM).
            content: The text content to ingest.
            metadata: Optional metadata dict.

        Returns:
            IngestResponse with the created KnowledgeBase and processing info.
        """
        if isinstance(converser, str):
            converser = ConverserRole(converser.upper())

        request = IngestRequest(
            uid=uid,
            converser=converser,
            content=content,
            metadata=metadata,
        )
        response = self._http.post(
            "/api/ingest/document",
            json=request.model_dump(exclude_none=True),
        )
        response.raise_for_status()
        return IngestResponse.model_validate(response.json())
