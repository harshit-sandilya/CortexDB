package com.vectornode.cortexdb.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vectornode.cortexdb.config.HttpClientWrapper;
import com.vectornode.cortexdb.exceptions.ApiException;
import com.vectornode.cortexdb.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueryApi with mocked HttpClientWrapper.
 */
@ExtendWith(MockitoExtension.class)
class QueryApiTest {

    @Mock
    private HttpClientWrapper http;

    private QueryApi queryApi;

    private QueryResponse mockQueryResponse() {
        QueryResponse resp = new QueryResponse();
        resp.setQuery("test");
        SearchResult result = new SearchResult();
        result.setContent("Test content");
        result.setScore(0.95);
        result.setType("CHUNK");
        resp.setResults(List.of(result));
        resp.setProcessingTimeMs(42);
        return resp;
    }

    @BeforeEach
    void setUp() {
        queryApi = new QueryApi(http);
    }

    // ── Context endpoints ────────────────────────────────────────

    @Test
    void searchContexts_default() {
        when(http.post(eq("/api/v1/memory/query/contexts"), isNull(), any(QueryRequest.class), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.searchContexts("test");
        assertNotNull(result);
        assertEquals(1, result.getResults().size());
    }

    @Test
    void searchContexts_withParams() {
        when(http.post(eq("/api/v1/memory/query/contexts"), isNull(), any(QueryRequest.class), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.searchContexts("test", 10, 0.8, null);
        assertNotNull(result);
    }

    @Test
    void getContextsByKb() {
        UUID kbId = UUID.randomUUID();
        when(http.get(eq("/api/v1/memory/query/contexts/kb/" + kbId), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.getContextsByKb(kbId);
        assertNotNull(result);
    }

    @Test
    void getRecentContexts() {
        when(http.get(eq("/api/v1/memory/query/contexts/recent"), eq(Map.of("days", "7")), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.getRecentContexts(7);
        assertNotNull(result);
    }

    @Test
    void getSiblingContexts() {
        UUID contextId = UUID.randomUUID();
        when(http.get(eq("/api/v1/memory/query/contexts/siblings/" + contextId), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.getSiblingContexts(contextId);
        assertNotNull(result);
    }

    // ── Entity endpoints ─────────────────────────────────────────

    @Test
    void searchEntities() {
        when(http.post(eq("/api/v1/memory/query/entities"), isNull(), any(QueryRequest.class), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.searchEntities("test");
        assertNotNull(result);
    }

    @Test
    void getEntityByName_found() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        when(http.objectMapper()).thenReturn(mapper);
        when(http.getRawOrNull("/api/v1/memory/query/entities/name/Google"))
                .thenReturn("{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"name\":\"Google\",\"type\":\"ORG\"}");

        Entity entity = queryApi.getEntityByName("Google");
        assertNotNull(entity);
        assertEquals("Google", entity.getName());
    }

    @Test
    void getEntityByName_notFound() {
        when(http.getRawOrNull("/api/v1/memory/query/entities/name/Unknown"))
                .thenReturn(null);

        Entity entity = queryApi.getEntityByName("Unknown");
        assertNull(entity);
    }

    @Test
    void getEntityIdByName_found() {
        UUID expectedId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        when(http.objectMapper()).thenReturn(mapper);
        when(http.getRawOrNull("/api/v1/memory/query/entities/id/Google"))
                .thenReturn("{\"id\":\"550e8400-e29b-41d4-a716-446655440000\"}");

        UUID id = queryApi.getEntityIdByName("Google");
        assertEquals(expectedId, id);
    }

    @Test
    void getEntityIdByName_notFound() {
        when(http.getRawOrNull("/api/v1/memory/query/entities/id/Unknown"))
                .thenReturn(null);

        UUID id = queryApi.getEntityIdByName("Unknown");
        assertNull(id);
    }

    @Test
    void mergeEntities() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        queryApi.mergeEntities(source, target);

        verify(http).postNoBody(eq("/api/v1/memory/query/entities/merge"),
                eq(Map.of("sourceEntityId", source.toString(),
                        "targetEntityId", target.toString())));
    }

    // ── History endpoints ────────────────────────────────────────

    @Test
    void searchHistory() {
        when(http.post(eq("/api/v1/memory/query/history"), isNull(), any(QueryRequest.class), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.searchHistory("test");
        assertNotNull(result);
    }

    @Test
    void getHistoryByUser() {
        when(http.get(eq("/api/v1/memory/query/history/user/user-1"), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.getHistoryByUser("user-1");
        assertNotNull(result);
    }

    @Test
    void getRecentKbs() {
        when(http.get(eq("/api/v1/memory/query/history/recent"), eq(Map.of("hours", "24")), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.getRecentKbs(24);
        assertNotNull(result);
    }

    // ── User data ────────────────────────────────────────────────

    @Test
    void deleteUserData() {
        queryApi.deleteUserData("user-1");
        verify(http).delete("/api/v1/memory/query/history/user/user-1");
    }

    // ── Graph endpoints ──────────────────────────────────────────

    @Test
    void getOutgoingConnections() {
        UUID entityId = UUID.randomUUID();
        when(http.get(eq("/api/v1/memory/query/graph/outgoing/" + entityId), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.getOutgoingConnections(entityId);
        assertNotNull(result);
    }

    @Test
    void getIncomingConnections() {
        UUID entityId = UUID.randomUUID();
        when(http.get(eq("/api/v1/memory/query/graph/incoming/" + entityId), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.getIncomingConnections(entityId);
        assertNotNull(result);
    }

    @Test
    void getTwoHopConnections() {
        UUID entityId = UUID.randomUUID();
        when(http.get(eq("/api/v1/memory/query/graph/2hop/" + entityId), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.getTwoHopConnections(entityId);
        assertNotNull(result);
    }

    // ── Hybrid search ────────────────────────────────────────────

    @Test
    void hybridSearch() {
        when(http.post(eq("/api/v1/memory/query/hybrid"), isNull(), any(QueryRequest.class), eq(QueryResponse.class)))
                .thenReturn(mockQueryResponse());

        QueryResponse result = queryApi.hybridSearch("test");
        assertNotNull(result);
    }
}
