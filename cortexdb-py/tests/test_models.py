"""Tests for Pydantic data models."""

from uuid import UUID

from cortexdb.models import (
    ConverserRole,
    ContradictionResult,
    Entity,
    IngestPromptRequest,
    IngestDocumentRequest,
    IngestResponse,
    IntentStats,
    LLMApiProvider,
    QueryRequest,
    QueryResponse,
    Relation,
    SearchResult,   
    SetupRequest,
    SetupResponse,
)


class TestEnums:
    def test_llm_api_provider_values(self):
        assert LLMApiProvider.GEMINI == "GEMINI"
        assert LLMApiProvider.OPENAI == "OPENAI"
        assert LLMApiProvider.AZURE == "AZURE"

    def test_converser_role_values(self):
        assert ConverserRole.USER == "USER"
        assert ConverserRole.AGENT == "AGENT"
        assert ConverserRole.SYSTEM == "SYSTEM"


class TestSetupModels:
    def test_setup_request_serialization(self):
        req = SetupRequest(
            provider=LLMApiProvider.GEMINI,
            apiKey="test-key",
            chatModelName="gemini-2.0-flash",
            embedModelName="gemini-embedding-001",
        )
        data = req.model_dump(by_alias=True, exclude_none=True)
        assert data["provider"] == "GEMINI"
        assert data["apiKey"] == "test-key"
        assert data["chatModelName"] == "gemini-2.0-flash"
        assert data["embedModelName"] == "gemini-embedding-001"

    def test_setup_response_from_json(self):
        resp = SetupResponse.model_validate({
            "message": "OK",
            "success": True,
            "configuredProvider": "GEMINI",
            "configuredChatModel": "gemini-2.0-flash",
            "configuredEmbedModel": "gemini-embedding-001",
        })
        assert resp.success is True
        assert resp.configured_provider == "GEMINI"


class TestIngestModels:
    def test_ingest_request_serialization(self):
        req = IngestPromptRequest(uid="user-1", converser=ConverserRole.USER, text="Hello")
        data = req.model_dump(exclude_none=True)
        assert data["uid"] == "user-1"
        assert data["converser"] == "USER"
        assert data["text"] == "Hello"

    def test_ingest_document_request_serialization(self):
        req = IngestDocumentRequest(uid="user-1", documentTitle="My Doc", documentText="Hello Doc")
        data = req.model_dump(by_alias=True, exclude_none=True)
        assert data["uid"] == "user-1"
        assert data["documentTitle"] == "My Doc"
        assert data["documentText"] == "Hello Doc"

    def test_ingest_response_from_json(self):
        resp = IngestResponse.model_validate({
            "status": "SUCCESS",
            "message": "Document ingested",
            "processingTimeMs": 150,
            "embeddingTimeMs": 50,
            "knowledgeBase": {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "uid": "user-1",
                "content": "Hello",
            },
        })
        assert resp.status == "SUCCESS"
        assert resp.processing_time_ms == 150
        assert resp.knowledge_base.uid == "user-1"


class TestQueryModels:
    def test_query_request_defaults(self):
        req = QueryRequest(query="test")
        assert req.limit == 5
        assert req.min_relevance == 0.7

    def test_query_request_serialization_uses_aliases(self):
        req = QueryRequest(query="hello", limit=10, minRelevance=0.5)
        data = req.model_dump(by_alias=True)
        assert data["minRelevance"] == 0.5
        assert data["limit"] == 10

    def test_query_response_with_results(self):
        resp = QueryResponse.model_validate({
            "query": "test",
            "results": [
                {"id": "550e8400-e29b-41d4-a716-446655440000", "content": "Result 1", "score": 0.9, "type": "CHUNK"},
                {"id": "660e8400-e29b-41d4-a716-446655440000", "content": "Result 2", "score": 0.8, "type": "ENTITY"},
            ],
            "processingTimeMs": 25,
        })
        assert len(resp.results) == 2
        assert resp.results[0].score == 0.9
        assert resp.results[1].type == "ENTITY"

    def test_query_response_empty_results(self):
        resp = QueryResponse.model_validate({"query": "nothing", "results": []})
        assert resp.results == []


class TestEntityModel:
    def test_entity_from_json(self):
        entity = Entity.model_validate({
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "name": "Google",
            "type": "ORGANIZATION",
            "description": "Tech company",
        })
        assert entity.name == "Google"
        assert entity.type == "ORGANIZATION"
        assert isinstance(entity.id, UUID)


class TestRelationModel:
    def test_relation_from_json(self):
        rel = Relation.model_validate({
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "sourceEntityId": "660e8400-e29b-41d4-a716-446655440000",
            "targetEntityId": "770e8400-e29b-41d4-a716-446655440000",
            "relationType": "WORKS_FOR",
            "weight": 0.95,
        })
        assert rel.relation_type == "WORKS_FOR"
        assert rel.weight == 0.95
        assert isinstance(rel.source_entity_id, UUID)


class TestIntentStatsModel:
    def test_intent_stats_from_json(self):
        stats = IntentStats.model_validate({
            "contextId": "550e8400-e29b-41d4-a716-446655440000",
            "totalRetrievals": 15,
            "weightedSuccesses": 8.5,
            "estimatedBoost": "0.2500"
        })
        assert isinstance(stats.context_id, UUID)
        assert stats.total_retrievals == 15
        assert stats.weighted_successes == 8.5
        assert stats.estimated_boost == "0.2500"

    def test_intent_stats_zero_values(self):
        stats = IntentStats.model_validate({
            "contextId": "660e8400-e29b-41d4-a716-446655440000",
            "totalRetrievals": 0,
            "weightedSuccesses": 0.0,
            "estimatedBoost": "0.0000"
        })
        assert stats.total_retrievals == 0
        assert stats.weighted_successes == 0.0
        assert stats.estimated_boost == "0.0000"


class TestContradictionResultModel:
    def test_contradiction_result_from_json(self):
        contradiction = ContradictionResult.model_validate({
            "id": "770e8400-e29b-41d4-a716-446655440000",
            "content": "Fact A ⚡ Fact B",
            "score": 0.0,
            "type": "CONTRADICTION",
            "metadata": {
                "contextIdA": "550e8400-e29b-41d4-a716-446655440000",
                "contextIdB": "660e8400-e29b-41d4-a716-446655440000",
                "summary": "Direct factual conflict",
                "severity": "HIGH"
            }
        })
        assert isinstance(contradiction.id, UUID)
        assert contradiction.type == "CONTRADICTION"
        assert contradiction.metadata["severity"] == "HIGH"
        assert contradiction.metadata["summary"] == "Direct factual conflict"
        # Metadata fields remain as strings (not automatically converted to UUID)
        assert isinstance(contradiction.metadata["contextIdA"], str)
        assert isinstance(contradiction.metadata["contextIdB"], str)

    def test_contradiction_result_with_full_fields(self):
        contradiction = ContradictionResult.model_validate({
            "id": "880e8400-e29b-41d4-a716-446655440000",
            "content": "Contradiction content",
            "score": 0.0,
            "type": "CONTRADICTION",
            "metadata": {
                "contextIdA": "550e8400-e29b-41d4-a716-446655440000",
                "contextIdB": "660e8400-e29b-41d4-a716-446655440000",
                "summary": "Partial conflict",
                "severity": "MODERATE"
            },
            "contextIdA": "550e8400-e29b-41d4-a716-446655440000",
            "contextIdB": "660e8400-e29b-41d4-a716-446655440000",
            "summary": "Partial conflict",
            "severity": "MODERATE",
            "resolved": False
        })
        assert contradiction.severity == "MODERATE"
        assert contradiction.summary == "Partial conflict"
        assert contradiction.resolved is False
