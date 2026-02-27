package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for data model serialization/deserialization.
 */
class ModelsTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ── SetupRequest ─────────────────────────────────────────────

    @Test
    void setupRequest_serializesCorrectly() throws Exception {
        SetupRequest req = new SetupRequest(
                LLMApiProvider.GEMINI, "test-key",
                "gemini-2.0-flash", "gemini-embedding-001", null);

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"provider\":\"GEMINI\""));
        assertTrue(json.contains("\"chatModelName\":\"gemini-2.0-flash\""));
        assertTrue(json.contains("\"embedModelName\":\"gemini-embedding-001\""));
        assertFalse(json.contains("baseUrl")); // null fields excluded
    }

    @Test
    void setupRequest_deserializesCorrectly() throws Exception {
        String json = """
                {"provider":"OPENAI","apiKey":"key","chatModelName":"gpt-4","embedModelName":"text-embedding-ada-002","baseUrl":"https://custom.api.com"}
                """;
        SetupRequest req = mapper.readValue(json, SetupRequest.class);
        assertEquals(LLMApiProvider.OPENAI, req.getProvider());
        assertEquals("key", req.getApiKey());
        assertEquals("gpt-4", req.getChatModelName());
        assertEquals("https://custom.api.com", req.getBaseUrl());
    }

    // ── SetupResponse ────────────────────────────────────────────

    @Test
    void setupResponse_deserializesCorrectly() throws Exception {
        String json = """
                {
                  "message": "Configured successfully",
                  "success": true,
                  "configuredProvider": "GEMINI",
                  "configuredChatModel": "gemini-2.0-flash",
                  "configuredEmbedModel": "gemini-embedding-001",
                  "timestamp": "2026-01-15T10:30:00Z"
                }
                """;
        SetupResponse resp = mapper.readValue(json, SetupResponse.class);
        assertTrue(resp.isSuccess());
        assertEquals("GEMINI", resp.getConfiguredProvider());
        assertEquals("gemini-2.0-flash", resp.getConfiguredChatModel());
        assertNotNull(resp.getTimestamp());
    }

    // ── IngestRequest ────────────────────────────────────────────

    @Test
    void ingestRequest_serializesCorrectly() throws Exception {
        IngestRequest req = new IngestRequest("user-1", ConverserRole.USER,
                "Hello world", Map.of("source", "test"));

        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"converser\":\"USER\""));
        assertTrue(json.contains("\"uid\":\"user-1\""));
        assertTrue(json.contains("\"content\":\"Hello world\""));
    }

    // ── IngestResponse ───────────────────────────────────────────

    @Test
    void ingestResponse_deserializesCorrectly() throws Exception {
        String json = """
                {
                  "knowledgeBase": {"id": "550e8400-e29b-41d4-a716-446655440000", "uid": "user-1", "content": "Hello"},
                  "status": "SUCCESS",
                  "message": "Ingested",
                  "processingTimeMs": 150,
                  "embeddingTimeMs": 50
                }
                """;
        IngestResponse resp = mapper.readValue(json, IngestResponse.class);
        assertEquals("SUCCESS", resp.getStatus());
        assertNotNull(resp.getKnowledgeBase());
        assertEquals("user-1", resp.getKnowledgeBase().getUid());
        assertEquals(150L, resp.getProcessingTimeMs());
    }

    // ── QueryRequest ─────────────────────────────────────────────

    @Test
    void queryRequest_defaultValues() {
        QueryRequest req = new QueryRequest();
        req.setQuery("test");
        assertEquals(5, req.getLimit());
        assertEquals(0.7, req.getMinRelevance(), 0.001);
    }

    @Test
    void queryRequest_serializesCorrectly() throws Exception {
        QueryRequest req = new QueryRequest("What is AI?", 10, 0.8, null);
        String json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"query\":\"What is AI?\""));
        assertTrue(json.contains("\"limit\":10"));
        assertTrue(json.contains("\"minRelevance\":0.8"));
    }

    // ── QueryResponse / SearchResult ─────────────────────────────

    @Test
    void queryResponse_deserializesCorrectly() throws Exception {
        String json = """
                {
                  "query": "test query",
                  "results": [
                    {"id": "550e8400-e29b-41d4-a716-446655440000", "content": "Result 1", "score": 0.95, "type": "CHUNK"},
                    {"id": "660e8400-e29b-41d4-a716-446655440001", "content": "Result 2", "score": 0.85, "type": "ENTITY"}
                  ],
                  "processingTimeMs": 42
                }
                """;
        QueryResponse resp = mapper.readValue(json, QueryResponse.class);
        assertEquals("test query", resp.getQuery());
        assertEquals(2, resp.getResults().size());
        assertEquals("Result 1", resp.getResults().get(0).getContent());
        assertEquals(0.95, resp.getResults().get(0).getScore(), 0.001);
        assertEquals("CHUNK", resp.getResults().get(0).getType());
        assertEquals(42L, resp.getProcessingTimeMs());
    }

    // ── Entity ───────────────────────────────────────────────────

    @Test
    void entity_deserializesCorrectly() throws Exception {
        String json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "name": "Google",
                  "type": "ORGANIZATION",
                  "description": "A technology company"
                }
                """;
        Entity entity = mapper.readValue(json, Entity.class);
        assertEquals("Google", entity.getName());
        assertEquals("ORGANIZATION", entity.getType());
        assertNotNull(entity.getId());
    }

    // ── Relation ─────────────────────────────────────────────────

    @Test
    void relation_deserializesCorrectly() throws Exception {
        String json = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "sourceEntityId": "660e8400-e29b-41d4-a716-446655440001",
                  "targetEntityId": "770e8400-e29b-41d4-a716-446655440002",
                  "relationType": "WORKS_FOR",
                  "edgeWeight": 5
                }
                """;
        Relation rel = mapper.readValue(json, Relation.class);
        assertEquals("WORKS_FOR", rel.getRelationType());
        assertEquals(5, rel.getEdgeWeight());
        assertNotNull(rel.getSourceEntityId());
        assertNotNull(rel.getTargetEntityId());
    }

    // ── Enums ────────────────────────────────────────────────────

    @Test
    void converserRole_valuesExist() {
        assertEquals(3, ConverserRole.values().length);
        assertNotNull(ConverserRole.valueOf("USER"));
        assertNotNull(ConverserRole.valueOf("AGENT"));
        assertNotNull(ConverserRole.valueOf("SYSTEM"));
    }

    @Test
    void llmApiProvider_valuesExist() {
        assertEquals(6, LLMApiProvider.values().length);
        assertNotNull(LLMApiProvider.valueOf("GEMINI"));
        assertNotNull(LLMApiProvider.valueOf("OPENAI"));
        assertNotNull(LLMApiProvider.valueOf("AZURE"));
    }
}
