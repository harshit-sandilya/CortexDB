"""Tests for the Ingest API wrapper."""

import json

import httpx
import respx

from cortexdb.client import CortexDB


class TestIngestAPI:
    @respx.mock
    def test_document_sends_correct_payload(self):
        route = respx.post("http://testserver/api/ingest/document").mock(
            return_value=httpx.Response(200, json={
                "status": "SUCCESS",
                "message": "Document ingested successfully",
                "processingTimeMs": 250,
                "embeddingTimeMs": 80,
                "knowledgeBase": {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "uid": "user-1",
                    "content": "Hello world",
                },
            })
        )

        with CortexDB("http://testserver") as db:
            resp = db.ingest.document(
                uid="user-1",
                converser="USER",
                content="Hello world",
            )

        assert route.called
        assert resp.status == "SUCCESS"
        assert resp.processing_time_ms == 250
        assert resp.knowledge_base.uid == "user-1"

        body = json.loads(route.calls[0].request.content)
        assert body["uid"] == "user-1"
        assert body["converser"] == "USER"
        assert body["content"] == "Hello world"

    @respx.mock
    def test_document_with_metadata(self):
        route = respx.post("http://testserver/api/ingest/document").mock(
            return_value=httpx.Response(200, json={
                "status": "SUCCESS",
                "message": "OK",
            })
        )

        with CortexDB("http://testserver") as db:
            db.ingest.document(
                uid="user-1",
                converser="AGENT",
                content="Some content",
                metadata={"source": "test", "priority": 1},
            )

        body = json.loads(route.calls[0].request.content)
        assert body["metadata"]["source"] == "test"
        assert body["metadata"]["priority"] == 1

    @respx.mock
    def test_document_accepts_enum_converser(self):
        from cortexdb.models import ConverserRole

        respx.post("http://testserver/api/ingest/document").mock(
            return_value=httpx.Response(200, json={"status": "SUCCESS"})
        )

        with CortexDB("http://testserver") as db:
            resp = db.ingest.document(
                uid="user-1",
                converser=ConverserRole.SYSTEM,
                content="System message",
            )

        assert resp.status == "SUCCESS"
