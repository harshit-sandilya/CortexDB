package com.vectornode.memory.query.controller;

import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.entity.Relation;
import com.vectornode.memory.query.dto.request.QueryRequest;
import com.vectornode.memory.query.dto.response.QueryResponse;
import com.vectornode.memory.query.service.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueryController using Mockito without Spring context.
 * Tests all REST endpoints with mocked QueryService.
 */
@ExtendWith(MockitoExtension.class)
class QueryControllerTest {

    @Mock
    private QueryService queryService;

    @InjectMocks
    private QueryController queryController;

    private QueryRequest validRequest;
    private QueryResponse mockResponse;
    private UUID testUuid;

    @BeforeEach
    void setUp() {
        testUuid = UUID.randomUUID();

        validRequest = QueryRequest.builder()
                .query("test query")
                .limit(5)
                .minRelevance(0.7)
                .build();

        mockResponse = QueryResponse.builder()
                .query("test query")
                .results(List.of(
                        QueryResponse.SearchResult.builder()
                                .id(testUuid)
                                .content("Test content")
                                .score(0.95)
                                .type("CHUNK")
                                .metadata(Map.of("key", "value"))
                                .build()))
                .processingTimeMs(100L)
                .build();
    }

    // ==================== CONTEXT ENDPOINT TESTS ====================

    @Nested
    @DisplayName("Context Endpoints")
    class ContextEndpointTests {

        @Test
        @DisplayName("POST /api/query/contexts - should return search results")
        void searchContexts_ShouldReturnResults() {
            when(queryService.searchContexts(any(QueryRequest.class), any()))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.searchContexts(validRequest, null);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("test query", response.getBody().getQuery());
            assertEquals(1, response.getBody().getResults().size());
            verify(queryService, times(1)).searchContexts(any(QueryRequest.class), any());
        }

        @Test
        @DisplayName("GET /api/query/contexts/kb/{kbId} - should return contexts for knowledge base")
        void getContextsByKnowledgeBase_ShouldReturnContexts() {
            when(queryService.getContextsByKnowledgeBase(testUuid))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getContextsByKnowledgeBase(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            verify(queryService, times(1)).getContextsByKnowledgeBase(testUuid);
        }

        @Test
        @DisplayName("GET /api/query/contexts/recent - should return recent contexts with default days")
        void getRecentContexts_ShouldReturnRecentContextsWithDefaultDays() {
            when(queryService.getRecentContexts(7))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getRecentContexts(7);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getRecentContexts(7);
        }

        @Test
        @DisplayName("GET /api/query/contexts/recent - should accept custom days parameter")
        void getRecentContexts_ShouldAcceptCustomDays() {
            when(queryService.getRecentContexts(30))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getRecentContexts(30);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getRecentContexts(30);
        }

        @Test
        @DisplayName("GET /api/query/contexts/range - should return contexts by date range")
        void getContextsByDateRange_ShouldReturnContexts() {
            String startDate = "2024-01-01T00:00:00Z";
            String endDate = "2024-12-31T23:59:59Z";

            when(queryService.getContextsByDateRange(startDate, endDate))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getContextsByDateRange(startDate, endDate);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getContextsByDateRange(startDate, endDate);
        }

        @Test
        @DisplayName("POST /api/query/contexts/recent - should search recent contexts")
        void searchRecentContexts_ShouldReturnResults() {
            when(queryService.searchRecentContexts(any(QueryRequest.class), eq(7)))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.searchRecentContexts(validRequest, 7);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).searchRecentContexts(any(QueryRequest.class), eq(7));
        }

        @Test
        @DisplayName("GET /api/query/contexts/siblings/{contextId} - should return sibling contexts")
        void getSiblingContexts_ShouldReturnSiblings() {
            when(queryService.getSiblingContexts(testUuid))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getSiblingContexts(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getSiblingContexts(testUuid);
        }
    }

    // ==================== ENTITY ENDPOINT TESTS ====================

    @Nested
    @DisplayName("Entity Endpoints")
    class EntityEndpointTests {

        @Test
        @DisplayName("POST /api/query/entities - should return entity search results")
        void searchEntities_ShouldReturnResults() {
            when(queryService.searchEntities(any(QueryRequest.class)))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.searchEntities(validRequest);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).searchEntities(any(QueryRequest.class));
        }

        @Test
        @DisplayName("GET /api/query/entities/name/{name} - should return entity when found")
        void getEntityByName_ShouldReturnEntityWhenFound() {
            RagEntity mockEntity = new RagEntity();
            mockEntity.setId(testUuid);
            mockEntity.setName("TestEntity");
            mockEntity.setType("CONCEPT");

            when(queryService.getEntityByName("TestEntity"))
                    .thenReturn(Optional.of(mockEntity));

            ResponseEntity<RagEntity> response = queryController.getEntityByName("TestEntity");

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("TestEntity", response.getBody().getName());
            verify(queryService, times(1)).getEntityByName("TestEntity");
        }

        @Test
        @DisplayName("GET /api/query/entities/name/{name} - should return 404 when not found")
        void getEntityByName_ShouldReturn404WhenNotFound() {
            when(queryService.getEntityByName("NonExistent"))
                    .thenReturn(Optional.empty());

            ResponseEntity<RagEntity> response = queryController.getEntityByName("NonExistent");

            assertNotNull(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            verify(queryService, times(1)).getEntityByName("NonExistent");
        }

        @Test
        @DisplayName("GET /api/query/entities/name-ignore-case/{name} - should return entity case-insensitively")
        void getEntityByNameIgnoreCase_ShouldReturnEntity() {
            RagEntity mockEntity = new RagEntity();
            mockEntity.setName("TestEntity");

            when(queryService.getEntityByNameIgnoreCase("testentity"))
                    .thenReturn(Optional.of(mockEntity));

            ResponseEntity<RagEntity> response = queryController.getEntityByNameIgnoreCase("testentity");

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getEntityByNameIgnoreCase("testentity");
        }

        @Test
        @DisplayName("GET /api/query/entities/id/{name} - should return entity ID when found")
        void getEntityIdByName_ShouldReturnIdWhenFound() {
            when(queryService.getEntityIdByName("TestEntity"))
                    .thenReturn(Optional.of(testUuid));

            ResponseEntity<Map<String, UUID>> response = queryController.getEntityIdByName("TestEntity");

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(testUuid, response.getBody().get("id"));
            verify(queryService, times(1)).getEntityIdByName("TestEntity");
        }

        @Test
        @DisplayName("GET /api/query/entities/id/{name} - should return 404 when ID not found")
        void getEntityIdByName_ShouldReturn404WhenNotFound() {
            when(queryService.getEntityIdByName("NonExistent"))
                    .thenReturn(Optional.empty());

            ResponseEntity<Map<String, UUID>> response = queryController.getEntityIdByName("NonExistent");

            assertNotNull(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            verify(queryService, times(1)).getEntityIdByName("NonExistent");
        }

        @Test
        @DisplayName("POST /api/query/entities/disambiguate - should disambiguate entity")
        void disambiguateEntity_ShouldReturnEntity() {
            RagEntity mockEntity = new RagEntity();
            mockEntity.setName("Apple");

            when(queryService.disambiguateEntity(eq("Apple"), anyString()))
                    .thenReturn(Optional.of(mockEntity));

            ResponseEntity<RagEntity> response = queryController.disambiguateEntity("Apple", "Tech company");

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).disambiguateEntity(eq("Apple"), anyString());
        }

        @Test
        @DisplayName("GET /api/query/entities/{entityId}/contexts - should return contexts for entity")
        void getContextsForEntity_ShouldReturnContexts() {
            when(queryService.getContextsForEntity(testUuid))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getContextsForEntity(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getContextsForEntity(testUuid);
        }

        @Test
        @DisplayName("GET /api/query/contexts/{contextId}/entities - should return entities for context")
        void getEntitiesForContext_ShouldReturnEntities() {
            when(queryService.getEntitiesForContext(testUuid))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getEntitiesForContext(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getEntitiesForContext(testUuid);
        }

        @Test
        @DisplayName("POST /api/query/entities/merge - should merge entities")
        void mergeEntities_ShouldReturnOk() {
            UUID sourceId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();

            doNothing().when(queryService).mergeEntities(sourceId, targetId);

            ResponseEntity<Void> response = queryController.mergeEntities(sourceId, targetId);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).mergeEntities(sourceId, targetId);
        }
    }

    // ==================== KNOWLEDGE BASE/HISTORY ENDPOINT TESTS
    // ====================

    @Nested
    @DisplayName("History Endpoints")
    class HistoryEndpointTests {

        @Test
        @DisplayName("POST /api/query/history - should return history search results")
        void searchHistory_ShouldReturnResults() {
            when(queryService.searchHistory(any(QueryRequest.class)))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.searchHistory(validRequest);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).searchHistory(any(QueryRequest.class));
        }

        @Test
        @DisplayName("GET /api/query/history/user/{uid} - should return user history")
        void getHistoryByUser_ShouldReturnHistory() {
            when(queryService.getHistoryByUser("user-123"))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getHistoryByUser("user-123");

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getHistoryByUser("user-123");
        }

        @Test
        @DisplayName("GET /api/query/history/recent - should return recent knowledge bases with default hours")
        void getRecentKnowledgeBases_ShouldReturnRecentWithDefaultHours() {
            when(queryService.getRecentKnowledgeBases(24))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getRecentKnowledgeBases(24);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getRecentKnowledgeBases(24);
        }

        @Test
        @DisplayName("GET /api/query/history/recent - should accept custom hours parameter")
        void getRecentKnowledgeBases_ShouldAcceptCustomHours() {
            when(queryService.getRecentKnowledgeBases(48))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getRecentKnowledgeBases(48);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getRecentKnowledgeBases(48);
        }

        @Test
        @DisplayName("GET /api/query/history/since - should return knowledge bases since timestamp")
        void getKnowledgeBasesSince_ShouldReturnResults() {
            String timestamp = "2024-01-01T00:00:00Z";
            Instant instant = Instant.parse(timestamp);

            when(queryService.getKnowledgeBasesSince(instant))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getKnowledgeBasesSince(timestamp);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getKnowledgeBasesSince(instant);
        }

        @Test
        @DisplayName("DELETE /api/query/history/user/{uid} - should delete user data")
        void deleteUserData_ShouldReturnOk() {
            doNothing().when(queryService).deleteUserData("user-123");

            ResponseEntity<Void> response = queryController.deleteUserData("user-123");

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).deleteUserData("user-123");
        }
    }

    // ==================== GRAPH/RELATION ENDPOINT TESTS ====================

    @Nested
    @DisplayName("Graph/Relation Endpoints")
    class GraphEndpointTests {

        @Test
        @DisplayName("GET /api/query/graph/outgoing/{entityId} - should return outgoing connections")
        void getOutgoingConnections_ShouldReturnConnections() {
            when(queryService.getOutgoingConnections(testUuid))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getOutgoingConnections(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getOutgoingConnections(testUuid);
        }

        @Test
        @DisplayName("GET /api/query/graph/incoming/{entityId} - should return incoming connections")
        void getIncomingConnections_ShouldReturnConnections() {
            when(queryService.getIncomingConnections(testUuid))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getIncomingConnections(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getIncomingConnections(testUuid);
        }

        @Test
        @DisplayName("GET /api/query/graph/2hop/{entityId} - should return two-hop connections")
        void getTwoHopConnections_ShouldReturnConnections() {
            when(queryService.getTwoHopConnections(testUuid))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getTwoHopConnections(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getTwoHopConnections(testUuid);
        }

        @Test
        @DisplayName("GET /api/query/graph/top - should return top relations with default limit")
        void getTopRelations_ShouldReturnTopRelationsWithDefaultLimit() {
            when(queryService.getTopRelations(10))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getTopRelations(10);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getTopRelations(10);
        }

        @Test
        @DisplayName("GET /api/query/graph/top - should accept custom limit parameter")
        void getTopRelations_ShouldAcceptCustomLimit() {
            when(queryService.getTopRelations(20))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.getTopRelations(20);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).getTopRelations(20);
        }

        @Test
        @DisplayName("GET /api/query/graph/source/{sourceId} - should return relations by source")
        void getRelationsBySource_ShouldReturnRelations() {
            Relation mockRelation = new Relation();
            mockRelation.setRelationType("WORKS_FOR");
            mockRelation.setEdgeWeight(1);

            when(queryService.getRelationsBySource(testUuid))
                    .thenReturn(List.of(mockRelation));

            ResponseEntity<List<Relation>> response = queryController.getRelationsBySource(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            verify(queryService, times(1)).getRelationsBySource(testUuid);
        }

        @Test
        @DisplayName("GET /api/query/graph/target/{targetId} - should return relations by target")
        void getRelationsByTarget_ShouldReturnRelations() {
            Relation mockRelation = new Relation();
            mockRelation.setRelationType("EMPLOYED_BY");

            when(queryService.getRelationsByTarget(testUuid))
                    .thenReturn(List.of(mockRelation));

            ResponseEntity<List<Relation>> response = queryController.getRelationsByTarget(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            verify(queryService, times(1)).getRelationsByTarget(testUuid);
        }

        @Test
        @DisplayName("GET /api/query/graph/type/{relationType} - should return relations by type")
        void getRelationsByType_ShouldReturnRelations() {
            Relation mockRelation = new Relation();
            mockRelation.setRelationType("KNOWS");

            when(queryService.getRelationsByType("KNOWS"))
                    .thenReturn(List.of(mockRelation));

            ResponseEntity<List<Relation>> response = queryController.getRelationsByType("KNOWS");

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            verify(queryService, times(1)).getRelationsByType("KNOWS");
        }

        @Test
        @DisplayName("GET /api/query/graph/source/{sourceId} - should return empty list when no relations")
        void getRelationsBySource_ShouldReturnEmptyListWhenNoRelations() {
            when(queryService.getRelationsBySource(testUuid))
                    .thenReturn(Collections.emptyList());

            ResponseEntity<List<Relation>> response = queryController.getRelationsBySource(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isEmpty());
        }
    }

    // ==================== HYBRID SEARCH ENDPOINT TESTS ====================

    @Nested
    @DisplayName("Hybrid Search Endpoint")
    class HybridSearchEndpointTests {

        @Test
        @DisplayName("POST /api/query/hybrid - should return hybrid search results")
        void hybridSearch_ShouldReturnResults() {
            when(queryService.hybridSearch(any(QueryRequest.class)))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.hybridSearch(validRequest);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("test query", response.getBody().getQuery());
            verify(queryService, times(1)).hybridSearch(any(QueryRequest.class));
        }

        @Test
        @DisplayName("POST /api/query/hybrid - should handle request with filters")
        void hybridSearch_ShouldHandleRequestWithFilters() {
            QueryRequest requestWithFilters = QueryRequest.builder()
                    .query("test query")
                    .limit(10)
                    .minRelevance(0.8)
                    .filters(Map.of("type", "CONCEPT"))
                    .build();

            when(queryService.hybridSearch(any(QueryRequest.class)))
                    .thenReturn(mockResponse);

            ResponseEntity<QueryResponse> response = queryController.hybridSearch(requestWithFilters);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).hybridSearch(any(QueryRequest.class));
        }
    }

    // ==================== INTEGRATION TESTS ====================

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle multiple sequential requests correctly")
        void shouldHandleMultipleSequentialRequests() {
            when(queryService.searchContexts(any(QueryRequest.class), any()))
                    .thenReturn(mockResponse);

            // First request
            ResponseEntity<QueryResponse> response1 = queryController.searchContexts(validRequest, null);
            assertNotNull(response1);
            assertEquals(HttpStatus.OK, response1.getStatusCode());

            // Second request
            ResponseEntity<QueryResponse> response2 = queryController.searchContexts(validRequest, null);
            assertNotNull(response2);
            assertEquals(HttpStatus.OK, response2.getStatusCode());

            verify(queryService, times(2)).searchContexts(any(QueryRequest.class), any());
        }

        @Test
        @DisplayName("Should verify service is called with exact parameters")
        void shouldVerifyServiceCalledWithExactParameters() {
            UUID specificUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

            when(queryService.getContextsByKnowledgeBase(specificUuid))
                    .thenReturn(mockResponse);

            queryController.getContextsByKnowledgeBase(specificUuid);

            verify(queryService).getContextsByKnowledgeBase(eq(specificUuid));
            verify(queryService, never()).getContextsByKnowledgeBase(argThat(uuid -> !uuid.equals(specificUuid)));
        }

        @Test
        @DisplayName("Should handle empty query results gracefully")
        void shouldHandleEmptyQueryResults() {
            QueryResponse emptyResponse = QueryResponse.builder()
                    .query("no results")
                    .results(Collections.emptyList())
                    .processingTimeMs(50L)
                    .build();

            when(queryService.searchContexts(any(QueryRequest.class), any()))
                    .thenReturn(emptyResponse);

            ResponseEntity<QueryResponse> response = queryController.searchContexts(validRequest, null);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getResults().isEmpty());
        }

        @Test
        @DisplayName("Should verify no unwanted service interactions")
        void shouldVerifyNoUnwantedServiceInteractions() {
            when(queryService.searchContexts(any(QueryRequest.class), any()))
                    .thenReturn(mockResponse);

            queryController.searchContexts(validRequest, null);

            verify(queryService, times(1)).searchContexts(any(QueryRequest.class), any());
            verify(queryService, never()).searchEntities(any(QueryRequest.class));
            verify(queryService, never()).searchHistory(any(QueryRequest.class));
        }
    }
}