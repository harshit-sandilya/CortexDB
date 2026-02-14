"""Tests for the Setup API wrapper."""

import httpx
import respx

from cortexdb.client import CortexDB


class TestSetupAPI:
    @respx.mock
    def test_configure_sends_correct_payload(self):
        route = respx.post("http://testserver/api/setup").mock(
            return_value=httpx.Response(200, json={
                "message": "LLM configured successfully",
                "success": True,
                "configuredProvider": "GEMINI",
                "configuredChatModel": "gemini-2.0-flash",
                "configuredEmbedModel": "gemini-embedding-001",
                "baseUrl": None,
            })
        )

        with CortexDB("http://testserver") as db:
            resp = db.setup.configure(
                provider="GEMINI",
                api_key="test-key",
                chat_model="gemini-2.0-flash",
                embed_model="gemini-embedding-001",
            )

        assert route.called
        assert resp.success is True
        assert resp.configured_provider == "GEMINI"
        assert resp.configured_chat_model == "gemini-2.0-flash"

        # Verify the request body
        request = route.calls[0].request
        import json
        body = json.loads(request.content)
        assert body["provider"] == "GEMINI"
        assert body["apiKey"] == "test-key"
        assert body["chatModelName"] == "gemini-2.0-flash"
        assert body["embedModelName"] == "gemini-embedding-001"

    @respx.mock
    def test_configure_with_base_url(self):
        route = respx.post("http://testserver/api/setup").mock(
            return_value=httpx.Response(200, json={
                "message": "OK",
                "success": True,
                "configuredProvider": "OPENAI",
                "baseUrl": "https://custom.openai.com",
            })
        )

        with CortexDB("http://testserver") as db:
            resp = db.setup.configure(
                provider="OPENAI",
                api_key="sk-test",
                chat_model="gpt-4",
                embed_model="text-embedding-ada-002",
                base_url="https://custom.openai.com",
            )

        assert resp.success is True
        import json
        body = json.loads(route.calls[0].request.content)
        assert body["baseUrl"] == "https://custom.openai.com"

    @respx.mock
    def test_configure_string_provider_is_uppercased(self):
        respx.post("http://testserver/api/setup").mock(
            return_value=httpx.Response(200, json={"message": "OK", "success": True})
        )

        with CortexDB("http://testserver") as db:
            resp = db.setup.configure(
                provider="gemini",
                chat_model="gemini-2.0-flash",
                embed_model="gemini-embedding-001",
            )

        assert resp.success is True
