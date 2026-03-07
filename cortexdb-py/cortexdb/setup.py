"""Setup API wrapper for CortexDB."""

from __future__ import annotations

from typing import TYPE_CHECKING

from cortexdb.models import LLMApiProvider, SetupRequest, SetupResponse

if TYPE_CHECKING:
    import httpx


class SetupAPI:
    """Wraps the ``/api/setup`` endpoint."""

    def __init__(self, http: httpx.Client) -> None:
        self._http = http

    def configure(
        self,
        provider: str | LLMApiProvider,
        chat_model: str,
        embed_model: str,
        api_key: str | None = None,
        base_url: str | None = None,
    ) -> SetupResponse:
        """Configure the LLM provider on the CortexDB server.

        Args:
            provider: LLM provider name (e.g. "GEMINI", "OPENAI", "AZURE", "ANTHROPIC", "OPENROUTER").
            chat_model: Name of the chat model.
            embed_model: Name of the embedding model.
            api_key: API key for the provider.
            base_url: Custom base URL (optional).

        Returns:
            SetupResponse with configuration details.
        """
        if isinstance(provider, str):
            provider = LLMApiProvider(provider.upper())

        request = SetupRequest(
            provider=provider,
            apiKey=api_key,
            chatModelName=chat_model,
            embedModelName=embed_model,
            baseUrl=base_url,
        )
        response = self._http.post(
            "/api/setup",
            json=request.model_dump(by_alias=True, exclude_none=True),
        )
        response.raise_for_status()
        return SetupResponse.model_validate(response.json())
