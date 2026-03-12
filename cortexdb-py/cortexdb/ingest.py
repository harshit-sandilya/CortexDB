"""Ingest API wrapper for CortexDB."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from cortexdb.models import ConverserRole, IngestPromptRequest, IngestDocumentRequest, IngestResponse

if TYPE_CHECKING:
    import httpx


class IngestAPI:
    """Wraps the ``/api/v1/memory/ingest`` endpoints."""

    def __init__(self, http: httpx.Client) -> None:
        self._http = http

    def prompt(
        self,
        uid: str,
        converser: str | ConverserRole,
        text: str,
        metadata: dict[str, Any] | None = None,
    ) -> IngestResponse:
        """Ingest a prompt payload into CortexDB.

        The server will perform semantic compression and online synthesis.

        Args:
            uid: User identifier.
            converser: Role of the converser (USER, AGENT, or SYSTEM).
            text: The text content to ingest.
            metadata: Optional metadata dict.

        Returns:
            IngestResponse with processing info.
        """
        if isinstance(converser, str):
            converser = ConverserRole(converser.upper())

        request = IngestPromptRequest(
            uid=uid,
            converser=converser,
            text=text,
            metadata=metadata,
        )
        response = self._http.post(
            "/api/v1/memory/ingest/prompt",
            json=request.model_dump(exclude_none=True),
        )
        response.raise_for_status()
        return IngestResponse.model_validate(response.json())

    def document(
        self,
        uid: str,
        document_title: str,
        document_text: str,
    ) -> IngestResponse:
        """Ingest a large document into CortexDB.

        The server will extract a hierarchical page index (document tree).

        Args:
            uid: User identifier.
            document_title: Title of the document.
            document_text: The full text content to ingest.

        Returns:
            IngestResponse with processing info.
        """
        request = IngestDocumentRequest(
            uid=uid,
            document_title=document_title,
            document_text=document_text,
        )
        response = self._http.post(
            "/api/v1/memory/ingest/document",
            json=request.model_dump(by_alias=True, exclude_none=True),
        )
        response.raise_for_status()
        return IngestResponse.model_validate(response.json())
