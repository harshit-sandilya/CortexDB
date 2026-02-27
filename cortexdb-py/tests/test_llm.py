"""Tests for the native LLM provider.

These tests verify:
1. Provider initialization logic (mocked)
2. Error handling for missing dependencies
3. Real API calls (marked with @pytest.mark.llm, skipped by default)
"""

import os
from unittest.mock import MagicMock, patch

import pytest

from cortexdb.exceptions import LLMError, LLMNotInitializedError
from cortexdb.llm.provider import LLMProvider


class TestLLMProviderInit:
    def test_unsupported_provider_raises(self):
        with pytest.raises(ValueError, match="Unsupported provider: UNKNOWN"):
            LLMProvider(provider="UNKNOWN", api_key="test")

    @patch("cortexdb.llm.provider.LLMProvider._init_gemini")
    def test_gemini_provider_calls_init_gemini(self, mock_init):
        llm = LLMProvider.__new__(LLMProvider)
        llm.provider = "GEMINI"
        llm.api_key = "test"
        llm.chat_model = None
        llm.embed_model = None
        llm.base_url = None
        llm._chat_client = None
        llm._embed_client = None
        llm._initialize()
        mock_init.assert_called_once()

    @patch("cortexdb.llm.provider.LLMProvider._init_openai")
    def test_openai_provider_calls_init_openai(self, mock_init):
        llm = LLMProvider.__new__(LLMProvider)
        llm.provider = "OPENAI"
        llm.api_key = "test"
        llm.chat_model = None
        llm.embed_model = None
        llm.base_url = None
        llm._chat_client = None
        llm._embed_client = None
        llm._initialize()
        mock_init.assert_called_once()

    @patch("cortexdb.llm.provider.LLMProvider._init_openai")
    def test_azure_provider_calls_init_openai(self, mock_init):
        llm = LLMProvider.__new__(LLMProvider)
        llm.provider = "AZURE"
        llm.api_key = "test"
        llm.chat_model = None
        llm.embed_model = None
        llm.base_url = None
        llm._chat_client = None
        llm._embed_client = None
        llm._initialize()
        mock_init.assert_called_once()


class TestGetEmbedding:
    def test_raises_when_not_initialized(self):
        llm = LLMProvider.__new__(LLMProvider)
        llm._embed_client = None
        with pytest.raises(LLMNotInitializedError):
            llm.get_embedding("test")

    @patch("cortexdb.llm.provider.LLMProvider._initialize")
    def test_gemini_get_embedding(self, _mock_init):
        llm = LLMProvider.__new__(LLMProvider)
        llm.provider = "GEMINI"
        llm.embed_model = "gemini-embedding-001"
        llm._embed_client = True  # Not None, so it passes the guard

        # Mock the gemini client
        mock_embedding = MagicMock()
        mock_embedding.values = [0.1, 0.2, 0.3]
        mock_response = MagicMock()
        mock_response.embeddings = [mock_embedding]

        mock_client = MagicMock()
        mock_client.models.embed_content.return_value = mock_response
        llm._gemini_client = mock_client

        result = llm.get_embedding("Hello world")

        assert result == [0.1, 0.2, 0.3]
        mock_client.models.embed_content.assert_called_once_with(
            model="gemini-embedding-001",
            contents="Hello world",
        )

    @patch("cortexdb.llm.provider.LLMProvider._initialize")
    def test_openai_get_embedding(self, _mock_init):
        llm = LLMProvider.__new__(LLMProvider)
        llm.provider = "OPENAI"
        llm.embed_model = "text-embedding-ada-002"
        llm._embed_client = True

        mock_data = MagicMock()
        mock_data.embedding = [0.4, 0.5, 0.6]
        mock_response = MagicMock()
        mock_response.data = [mock_data]

        mock_client = MagicMock()
        mock_client.embeddings.create.return_value = mock_response
        llm._openai_client = mock_client

        result = llm.get_embedding("Hello world")

        assert result == [0.4, 0.5, 0.6]


class TestCallLLM:
    def test_raises_when_not_initialized(self):
        llm = LLMProvider.__new__(LLMProvider)
        llm._chat_client = None
        with pytest.raises(LLMNotInitializedError):
            llm.call_llm("test")

    @patch("cortexdb.llm.provider.LLMProvider._initialize")
    def test_gemini_call_llm(self, _mock_init):
        llm = LLMProvider.__new__(LLMProvider)
        llm.provider = "GEMINI"
        llm.chat_model = "gemini-2.0-flash"
        llm._chat_client = True

        mock_response = MagicMock()
        mock_response.text = "Hello from Gemini!"

        mock_client = MagicMock()
        mock_client.models.generate_content.return_value = mock_response
        llm._gemini_client = mock_client

        result = llm.call_llm("Say hello")

        assert result == "Hello from Gemini!"
        mock_client.models.generate_content.assert_called_once_with(
            model="gemini-2.0-flash",
            contents="Say hello",
        )

    @patch("cortexdb.llm.provider.LLMProvider._initialize")
    def test_openai_call_llm(self, _mock_init):
        llm = LLMProvider.__new__(LLMProvider)
        llm.provider = "OPENAI"
        llm.chat_model = "gpt-4"
        llm._chat_client = True

        mock_message = MagicMock()
        mock_message.content = "Hello from OpenAI!"
        mock_choice = MagicMock()
        mock_choice.message = mock_message
        mock_response = MagicMock()
        mock_response.choices = [mock_choice]

        mock_client = MagicMock()
        mock_client.chat.completions.create.return_value = mock_response
        llm._openai_client = mock_client

        result = llm.call_llm("Say hello")

        assert result == "Hello from OpenAI!"

    @patch("cortexdb.llm.provider.LLMProvider._initialize")
    def test_call_llm_wraps_exception(self, _mock_init):
        llm = LLMProvider.__new__(LLMProvider)
        llm.provider = "GEMINI"
        llm.chat_model = "gemini-2.0-flash"
        llm._chat_client = True

        mock_client = MagicMock()
        mock_client.models.generate_content.side_effect = RuntimeError("API quota exceeded")
        llm._gemini_client = mock_client

        with pytest.raises(LLMError, match="LLM call failed"):
            llm.call_llm("test prompt")


# ── Real API tests (skipped by default) ──────────────────────────────


@pytest.mark.skipif(
    not os.environ.get("GEMINI_API_KEY"),
    reason="GEMINI_API_KEY not set — skipping real LLM tests",
)
class TestRealGeminiLLM:
    """Integration tests that hit the real Gemini API.

    Run with: GEMINI_API_KEY=your-key pytest tests/test_llm.py -k TestRealGeminiLLM -v
    """

    @pytest.fixture(autouse=True)
    def llm(self):
        self.llm = LLMProvider(
            provider="GEMINI",
            api_key=os.environ["GEMINI_API_KEY"],
            chat_model="gemini-2.0-flash",
            embed_model="gemini-embedding-001",
        )

    def test_get_embedding_returns_vector(self):
        embedding = self.llm.get_embedding("Hello world")
        assert isinstance(embedding, list)
        assert len(embedding) > 0
        assert all(isinstance(v, float) for v in embedding)
        print(f"Embedding dimensions: {len(embedding)}")

    def test_call_llm_returns_text(self):
        response = self.llm.call_llm("What is 2 + 2? Answer with just the number.")
        assert isinstance(response, str)
        assert len(response) > 0
        assert "4" in response
        print(f"LLM response: {response}")
