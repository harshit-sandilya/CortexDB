package com.vectornode.cortexdb.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.cortexdb.config.HttpClientWrapper;
import com.vectornode.cortexdb.exceptions.CortexDBException;
import com.vectornode.cortexdb.models.*;

import java.util.*;

/**
 * Wraps all {@code /api/v1/memory/query} endpoints — contexts, entities, history, graph,
 * and hybrid search.
 */
public class QueryApi {

    private final HttpClientWrapper http;

    public QueryApi(HttpClientWrapper http) {
        this.http = http;
    }

    // ── Context endpoints ────────────────────────────────────────

    /** Semantic search on contexts. */
    public QueryResponse searchContexts(String query) {
        return searchContexts(query, 5, 0.7, null);
    }

    /** Semantic search on contexts with parameters. */
    public QueryResponse searchContexts(String query, int limit,
            double minRelevance,
            Map<String, Object> filters) {
        return postQuery("/api/v1/memory/query/contexts", query, limit, minRelevance, filters, null);
    }

    /** Get all contexts for a knowledge base. */
    public QueryResponse getContextsByKb(UUID kbId) {
        return http.get("/api/v1/memory/query/contexts/kb/" + kbId, QueryResponse.class);
    }

    /** Get recent contexts from the last N days. */
    public QueryResponse getRecentContexts(int days) {
        return http.get("/api/v1/memory/query/contexts/recent",
                Map.of("days", String.valueOf(days)), QueryResponse.class);
    }

    /** Get contexts by date range (ISO-8601 strings). */
    public QueryResponse getContextsByDateRange(String startDate, String endDate) {
        return http.get("/api/v1/memory/query/contexts/range",
                Map.of("startDate", startDate, "endDate", endDate), QueryResponse.class);
    }

    /** Search recent contexts with vector similarity. */
    public QueryResponse searchRecentContexts(String query, int days) {
        return searchRecentContexts(query, days, 5, 0.7);
    }

    /** Search recent contexts with vector similarity and parameters. */
    public QueryResponse searchRecentContexts(String query, int days,
            int limit, double minRelevance) {
        return postQuery("/api/v1/memory/query/contexts/recent", query, limit, minRelevance, null,
                Map.of("days", String.valueOf(days)));
    }

    /** Get sibling contexts (other chunks from same document). */
    public QueryResponse getSiblingContexts(UUID contextId) {
        return http.get("/api/v1/memory/query/contexts/siblings/" + contextId, QueryResponse.class);
    }

    // ── Entity endpoints ─────────────────────────────────────────

    /** Semantic search on entities. */
    public QueryResponse searchEntities(String query) {
        return searchEntities(query, 5, 0.7);
    }

    /** Semantic search on entities with parameters. */
    public QueryResponse searchEntities(String query, int limit, double minRelevance) {
        return postQuery("/api/v1/memory/query/entities", query, limit, minRelevance, null, null);
    }

    /** Find entity by exact name. Returns {@code null} if not found. */
    public Entity getEntityByName(String name) {
        String json = http.getRawOrNull("/api/v1/memory/query/entities/name/" + name);
        if (json == null)
            return null;
        return deserialize(json, Entity.class);
    }

    /**
     * Find entity by name (case-insensitive). Returns {@code null} if not found.
     */
    public Entity getEntityByNameIgnoreCase(String name) {
        String json = http.getRawOrNull("/api/v1/memory/query/entities/name-ignore-case/" + name);
        if (json == null)
            return null;
        return deserialize(json, Entity.class);
    }

    /** Get entity ID by name. Returns {@code null} if not found. */
    public UUID getEntityIdByName(String name) {
        String json = http.getRawOrNull("/api/v1/memory/query/entities/id/" + name);
        if (json == null)
            return null;
        Map<String, String> data = deserialize(json, new TypeReference<Map<String, String>>() {
        });
        String id = data.get("id");
        return id != null ? UUID.fromString(id) : null;
    }

    /** Disambiguate entity using vector similarity with context. */
    public Entity disambiguateEntity(String entityName, String contextText) {
        String json;
        try {
            // POST with plain-text body and entityName as query param
            json = http.getRawOrNull("/api/v1/memory/query/entities/disambiguate");
            // Actually need to use postPlainText with nullable handling
        } catch (Exception e) {
            // Fall through to direct call
            json = null;
        }
        // Use direct post with plain text
        try {
            return http.postPlainText("/api/v1/memory/query/entities/disambiguate",
                    Map.of("entityName", entityName),
                    contextText, Entity.class);
        } catch (com.vectornode.cortexdb.exceptions.ApiException e) {
            if (e.getStatusCode() == 404)
                return null;
            throw e;
        }
    }

    /** Get all contexts where an entity is mentioned. */
    public QueryResponse getContextsForEntity(UUID entityId) {
        return http.get("/api/v1/memory/query/entities/" + entityId + "/contexts", QueryResponse.class);
    }

    /** Get all entities mentioned in a context. */
    public QueryResponse getEntitiesForContext(UUID contextId) {
        return http.get("/api/v1/memory/query/contexts/" + contextId + "/entities", QueryResponse.class);
    }

    /** Merge two entities (source into target). */
    public void mergeEntities(UUID sourceEntityId, UUID targetEntityId) {
        http.postNoBody("/api/v1/memory/query/entities/merge",
                Map.of("sourceEntityId", sourceEntityId.toString(),
                        "targetEntityId", targetEntityId.toString()));
    }

    // ── History endpoints ────────────────────────────────────────

    /** Semantic search on knowledge bases (history). */
    public QueryResponse searchHistory(String query) {
        return searchHistory(query, 5, 0.7);
    }

    /** Semantic search on knowledge bases with parameters. */
    public QueryResponse searchHistory(String query, int limit, double minRelevance) {
        return postQuery("/api/v1/memory/query/history", query, limit, minRelevance, null, null);
    }

    /** Get all history for a user. */
    public QueryResponse getHistoryByUser(String uid) {
        return http.get("/api/v1/memory/query/history/user/" + uid, QueryResponse.class);
    }

    /** Get recent knowledge bases from the last N hours. */
    public QueryResponse getRecentKbs(int hours) {
        return http.get("/api/v1/memory/query/history/recent",
                Map.of("hours", String.valueOf(hours)), QueryResponse.class);
    }

    /** Get knowledge bases since a timestamp (ISO-8601). */
    public QueryResponse getKbsSince(String since) {
        return http.get("/api/v1/memory/query/history/since",
                Map.of("since", since), QueryResponse.class);
    }

    // ── User data ────────────────────────────────────────────────

    /** Delete all data for a user (GDPR compliance). */
    public void deleteUserData(String uid) {
        http.delete("/api/v1/memory/query/history/user/" + uid);
    }

    // ── Graph endpoints ──────────────────────────────────────────

    /** Get outgoing relations for an entity. */
    public QueryResponse getOutgoingConnections(UUID entityId) {
        return http.get("/api/v1/memory/query/graph/outgoing/" + entityId, QueryResponse.class);
    }

    /** Get incoming relations for an entity. */
    public QueryResponse getIncomingConnections(UUID entityId) {
        return http.get("/api/v1/memory/query/graph/incoming/" + entityId, QueryResponse.class);
    }

    /** Get 2-hop connections. */
    public QueryResponse getTwoHopConnections(UUID entityId) {
        return http.get("/api/v1/memory/query/graph/2hop/" + entityId, QueryResponse.class);
    }

    /** Get top/strongest relations. */
    public QueryResponse getTopRelations(int limit) {
        return http.get("/api/v1/memory/query/graph/top",
                Map.of("limit", String.valueOf(limit)), QueryResponse.class);
    }

    /** Get relations by source entity. */
    public List<Relation> getRelationsBySource(UUID sourceId) {
        String json = http.getRaw("/api/v1/memory/query/graph/source/" + sourceId);
        return deserializeList(json, new TypeReference<List<Relation>>() {
        });
    }

    /** Get relations by target entity. */
    public List<Relation> getRelationsByTarget(UUID targetId) {
        String json = http.getRaw("/api/v1/memory/query/graph/target/" + targetId);
        return deserializeList(json, new TypeReference<List<Relation>>() {
        });
    }

    /** Get relations by type. */
    public List<Relation> getRelationsByType(String relationType) {
        String json = http.getRaw("/api/v1/memory/query/graph/type/" + relationType);
        return deserializeList(json, new TypeReference<List<Relation>>() {
        });
    }

    // ── Agentic Router ───────────────────────────────────────────

    /** Route a query intelligently based on intent (PROMPT vs DOCUMENT). */
    public QueryResponse routeQuery(String query) {
        QueryRequest request = new QueryRequest(query, 5, 0.7, null);
        return http.post("/api/v1/memory/query/route", request, QueryResponse.class);
    }

    // ── Hybrid search ────────────────────────────────────────────

    /** Hybrid search combining vector and graph results. */
    public QueryResponse hybridSearch(String query) {
        return hybridSearch(query, 5, 0.7);
    }

    /** Hybrid search with parameters. */
    public QueryResponse hybridSearch(String query, int limit, double minRelevance) {
        return postQuery("/api/v1/memory/query/hybrid", query, limit, minRelevance, null, null);
    }

    // ── Internal helpers ─────────────────────────────────────────

    private QueryResponse postQuery(String url, String query, int limit,
            double minRelevance,
            Map<String, Object> filters,
            Map<String, String> params) {
        QueryRequest request = new QueryRequest(query, limit, minRelevance, filters);
        return http.post(url, params, request, QueryResponse.class);
    }

    private <T> T deserialize(String json, Class<T> clazz) {
        try {
            return http.objectMapper().readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new CortexDBException("JSON deserialization failed", e);
        }
    }

    private <T> T deserialize(String json, TypeReference<T> typeRef) {
        try {
            return http.objectMapper().readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new CortexDBException("JSON deserialization failed", e);
        }
    }

    private <T> T deserializeList(String json, TypeReference<T> typeRef) {
        return deserialize(json, typeRef);
    }
}
