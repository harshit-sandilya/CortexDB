"""Native LLM provider — Python re-implementation of callLLM() and getEmbedding()."""

from __future__ import annotations

import logging
from typing import Any

from cortexdb.exceptions import LLMError, LLMNotInitializedError

logger = logging.getLogger(__name__)


class LLMProvider:
    """LLM provider that directly calls the Gemini / OpenAI APIs from Python.

    This is a native re-implementation of the Java ``LLMProvider`` class,
    using the ``google-genai`` SDK for Gemini and the ``openai`` SDK for
    OpenAI/Azure providers.

    Usage::

        from cortexdb.llm import LLMProvider

        llm = LLMProvider(
            provider="GEMINI",
            api_key="your-api-key",
            chat_model="gemini-2.0-flash",
            embed_model="gemini-embedding-001",
        )

        embedding = llm.get_embedding("Hello world")
        response = llm.call_llm("Explain quantum computing")
    """

    def __init__(
        self,
        provider: str,
        api_key: str,
        chat_model: str | None = None,
        embed_model: str | None = None,
        base_url: str | None = None,
    ) -> None:
        """Initialize the LLM provider.

        Args:
            provider: Provider name — "GEMINI", "OPENAI", or "AZURE".
            api_key: API key for authentication.
            chat_model: Name of the chat model.
            embed_model: Name of the embedding model.
            base_url: Custom base URL (optional, mainly for Azure).
        """
        self.provider = provider.upper()
        self.api_key = api_key
        self.chat_model = chat_model
        self.embed_model = embed_model
        self.base_url = base_url

        self._chat_client: Any = None
        self._embed_client: Any = None

        self._initialize()

    def _initialize(self) -> None:
        """Set up the underlying SDK clients based on the provider."""
        logger.info(
            "Initializing LLMProvider with provider=%s, chat_model=%s, embed_model=%s",
            self.provider,
            self.chat_model,
            self.embed_model,
        )

        if self.provider == "GEMINI":
            self._init_gemini()
        elif self.provider in ("OPENAI", "AZURE"):
            self._init_openai()
        else:
            raise ValueError(f"Unsupported provider: {self.provider}")

    def _init_gemini(self) -> None:
        """Initialize using the google-genai SDK."""
        try:
            from google import genai

            self._gemini_client = genai.Client(api_key=self.api_key)
            self._chat_client = self._gemini_client
            self._embed_client = self._gemini_client
            logger.info("Gemini client initialized successfully")
        except ImportError:
            raise LLMError(
                "google-genai is required for Gemini provider. "
                "Install it with: pip install google-genai"
            )

    def _init_openai(self) -> None:
        """Initialize using the openai SDK (works for OpenAI and Azure)."""
        try:
            import openai

            if self.provider == "AZURE":
                self._openai_client = openai.AzureOpenAI(
                    api_key=self.api_key,
                    azure_endpoint=self.base_url or "",
                    api_version="2024-02-01",
                )
            else:
                self._openai_client = openai.OpenAI(
                    api_key=self.api_key,
                    base_url=self.base_url,
                )
            self._chat_client = self._openai_client
            self._embed_client = self._openai_client
            logger.info("OpenAI client initialized successfully")
        except ImportError:
            raise LLMError(
                "openai is required for OpenAI/Azure provider. "
                "Install it with: pip install openai"
            )

    def get_embedding(self, text: str) -> list[float]:
        """Generate an embedding vector for the given text.

        Args:
            text: Input text to embed.

        Returns:
            A list of floats representing the embedding vector.

        Raises:
            LLMNotInitializedError: If the provider is not initialized.
            LLMError: If embedding generation fails.
        """
        if self._embed_client is None:
            raise LLMNotInitializedError()

        logger.debug("Generating embedding for text (%d chars)", len(text))

        try:
            if self.provider == "GEMINI":
                response = self._gemini_client.models.embed_content(
                    model=self.embed_model or "gemini-embedding-001",
                    contents=text,
                )
                return list(response.embeddings[0].values)
            else:
                response = self._openai_client.embeddings.create(
                    model=self.embed_model or "text-embedding-ada-002",
                    input=text,
                )
                return response.data[0].embedding
        except Exception as e:
            logger.error("Embedding generation failed: %s", e)
            raise LLMError(f"Embedding generation failed: {e}") from e

    def call_llm(self, prompt: str) -> str:
        """Send a prompt to the LLM and return the response text.

        Args:
            prompt: The prompt to send.

        Returns:
            The LLM's text response.

        Raises:
            LLMNotInitializedError: If the provider is not initialized.
            LLMError: If the LLM call fails.
        """
        if self._chat_client is None:
            raise LLMNotInitializedError()

        logger.debug("Calling LLM with prompt (%d chars)", len(prompt))

        try:
            if self.provider == "GEMINI":
                response = self._gemini_client.models.generate_content(
                    model=self.chat_model or "gemini-2.0-flash",
                    contents=prompt,
                )
                return response.text
            else:
                response = self._openai_client.chat.completions.create(
                    model=self.chat_model or "gpt-4",
                    messages=[{"role": "user", "content": prompt}],
                )
                return response.choices[0].message.content
        except Exception as e:
            logger.error("LLM call failed: %s", e)
            raise LLMError(f"LLM call failed: {e}") from e
