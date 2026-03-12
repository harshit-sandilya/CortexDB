"""Tests for the Query API wrapper."""

import json
from uuid import UUID

import httpx
import respx

from cortexdb.client import CortexDB


SAMPLE_QUERY_RESPONSE = {
    "query": "test query",
    "results": [
        {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "content": "Test content",
            "score": 0.85,
            "type": "CHUNK",
            "metadata": {"chunkIndex": 0},
        }
    ],
    "processingTimeMs": 25,
}

EMPTY_QUERY_RESPONSE = {"query": "empty", "results": [], "processingTimeMs": 5}


class TestContextEndpoints:
    @respx.mock
    def test_search_contexts(self):
        route = respx.post("http://testserver/api/v1/memory/query/contexts").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.search_contexts("test query", limit=10, min_relevance=0.5)

        assert route.called
        assert len(resp.results) == 1
        assert resp.results[0].content == "Test content"
        assert resp.results[0].score == 0.85

        body = json.loads(route.calls[0].request.content)
        assert body["query"] == "test query"
        assert body["limit"] == 10
        assert body["minRelevance"] == 0.5

    @respx.mock
    def test_get_recent_contexts(self):
        route = respx.get("http://testserver/api/v1/memory/query/contexts/recent").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.get_recent_contexts(days=14)

        assert route.called
        assert resp.results[0].content == "Test content"

    @respx.mock
    def test_get_contexts_by_kb(self):
        kb_id = "550e8400-e29b-41d4-a716-446655440000"
        respx.get(f"http://testserver/api/v1/memory/query/contexts/kb/{kb_id}").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.get_contexts_by_kb(kb_id)

        assert len(resp.results) == 1

    @respx.mock
    def test_get_sibling_contexts(self):
        ctx_id = "550e8400-e29b-41d4-a716-446655440000"
        respx.get(f"http://testserver/api/v1/memory/query/contexts/{ctx_id}/siblings").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.get_sibling_contexts(ctx_id)

        assert len(resp.results) == 1


class TestEntityEndpoints:
    @respx.mock
    def test_search_entities(self):
        respx.post("http://testserver/api/v1/memory/query/entities").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.search_entities("machine learning")

        assert len(resp.results) == 1

    @respx.mock
    def test_get_entity_by_name_found(self):
        respx.get("http://testserver/api/v1/memory/query/entities/name/Google").mock(
            return_value=httpx.Response(200, json={
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "name": "Google",
                "type": "ORGANIZATION",
                "description": "Tech company",
            })
        )

        with CortexDB("http://testserver") as db:
            entity = db.query.get_entity_by_name("Google")

        assert entity is not None
        assert entity.name == "Google"
        assert entity.type == "ORGANIZATION"

    @respx.mock
    def test_get_entity_by_name_not_found(self):
        respx.get("http://testserver/api/v1/memory/query/entities/name/NonExistent").mock(
            return_value=httpx.Response(404)
        )

        with CortexDB("http://testserver") as db:
            entity = db.query.get_entity_by_name("NonExistent")

        assert entity is None

    @respx.mock
    def test_get_entity_id_by_name(self):
        entity_id = "550e8400-e29b-41d4-a716-446655440000"
        respx.get("http://testserver/api/v1/memory/query/entities/id/Google").mock(
            return_value=httpx.Response(200, json={"id": entity_id})
        )

        with CortexDB("http://testserver") as db:
            result = db.query.get_entity_id_by_name("Google")

        assert result == UUID(entity_id)

    @respx.mock
    def test_get_entity_id_by_name_not_found(self):
        respx.get("http://testserver/api/v1/memory/query/entities/id/Missing").mock(
            return_value=httpx.Response(404)
        )

        with CortexDB("http://testserver") as db:
            result = db.query.get_entity_id_by_name("Missing")

        assert result is None

    @respx.mock
    def test_merge_entities(self):
        route = respx.post("http://testserver/api/v1/memory/query/entities/merge").mock(
            return_value=httpx.Response(200)
        )

        with CortexDB("http://testserver") as db:
            db.query.merge_entities(
                "550e8400-e29b-41d4-a716-446655440000",
                "660e8400-e29b-41d4-a716-446655440000",
            )

        assert route.called


class TestHistoryEndpoints:
    @respx.mock
    def test_search_history(self):
        respx.post("http://testserver/api/v1/memory/query/history").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.search_history("previous conversation")

        assert len(resp.results) == 1

    @respx.mock
    def test_get_history_by_user(self):
        respx.get("http://testserver/api/v1/memory/query/history/user/user-1").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.get_history_by_user("user-1")

        assert len(resp.results) == 1

    @respx.mock
    def test_get_history_by_user_empty(self):
        respx.get("http://testserver/api/v1/memory/query/history/user/unknown").mock(
            return_value=httpx.Response(200, json=EMPTY_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.get_history_by_user("unknown")

        assert resp.results == []

    @respx.mock
    def test_delete_user_data(self):
        route = respx.delete("http://testserver/api/v1/memory/query/user/user-1").mock(
            return_value=httpx.Response(200)
        )

        with CortexDB("http://testserver") as db:
            db.query.delete_user_data("user-1")

        assert route.called


class TestGraphEndpoints:
    @respx.mock
    def test_get_outgoing_connections(self):
        entity_id = "550e8400-e29b-41d4-a716-446655440000"
        respx.get(f"http://testserver/api/v1/memory/query/graph/outgoing/{entity_id}").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.get_outgoing_connections(entity_id)

        assert len(resp.results) == 1

    @respx.mock
    def test_get_incoming_connections(self):
        entity_id = "550e8400-e29b-41d4-a716-446655440000"
        respx.get(f"http://testserver/api/v1/memory/query/graph/incoming/{entity_id}").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.get_incoming_connections(entity_id)

        assert len(resp.results) == 1

    @respx.mock
    def test_get_two_hop_connections(self):
        entity_id = "550e8400-e29b-41d4-a716-446655440000"
        respx.get(f"http://testserver/api/v1/memory/query/graph/two-hop/{entity_id}").mock(
            return_value=httpx.Response(200, json=["Entity A", "Entity B"])
        )

        with CortexDB("http://testserver") as db:
            result = db.query.get_two_hop_connections(entity_id)

        assert result == ["Entity A", "Entity B"]

    @respx.mock
    def test_get_top_relations(self):
        respx.get("http://testserver/api/v1/memory/query/graph/top-relations").mock(
            return_value=httpx.Response(200, json=[
                {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "sourceEntityId": "660e8400-e29b-41d4-a716-446655440000",
                    "targetEntityId": "770e8400-e29b-41d4-a716-446655440000",
                    "relationType": "WORKS_FOR",
                    "weight": 0.95,
                }
            ])
        )

        with CortexDB("http://testserver") as db:
            relations = db.query.get_top_relations(limit=5)

        assert len(relations) == 1
        assert relations[0].relation_type == "WORKS_FOR"

    @respx.mock
    def test_get_relations_by_type(self):
        respx.get("http://testserver/api/v1/memory/query/graph/relations/type/WORKS_FOR").mock(
            return_value=httpx.Response(200, json=[])
        )

        with CortexDB("http://testserver") as db:
            relations = db.query.get_relations_by_type("WORKS_FOR")

        assert relations == []


class TestHybridSearch:
    @respx.mock
    def test_hybrid_search(self):
        respx.post("http://testserver/api/v1/memory/query/hybrid").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.hybrid_search("combined search")

        assert len(resp.results) == 1

class TestPreRoutingEndpoints:
    @respx.mock
    def test_route_query(self):
        respx.post("http://testserver/api/v1/memory/query/route").mock(
            return_value=httpx.Response(200, json=SAMPLE_QUERY_RESPONSE)
        )

        with CortexDB("http://testserver") as db:
            resp = db.query.route_query("routing test")

        assert len(resp.results) == 1
