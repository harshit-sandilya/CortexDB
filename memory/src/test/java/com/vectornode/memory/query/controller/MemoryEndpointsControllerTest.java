package com.vectornode.memory.query.controller;

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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the new memory feature endpoints in QueryController:
 * - Contradiction endpoints (GET all, GET by context, POST resolve)
 * - Intent stats endpoint (GET)
 */
@ExtendWith(MockitoExtension.class)
class MemoryEndpointsControllerTest {

    @Mock
    private QueryService queryService;

    @InjectMocks
    private QueryController queryController;

    private QueryResponse mockContradictionResponse;
    private UUID testUuid;

    @BeforeEach
    void setUp() {
        testUuid = UUID.randomUUID();

        mockContradictionResponse = QueryResponse.builder()
                .query("contradictions:all")
                .results(List.of(
                        QueryResponse.SearchResult.builder()
                                .id(testUuid)
                                .content("Fact A ⚡ Fact B")
                                .score(0.0)
                                .type("CONTRADICTION")
                                .metadata(Map.of(
                                        "contextIdA", UUID.randomUUID(),
                                        "contextIdB", UUID.randomUUID(),
                                        "summary", "Direct factual conflict",
                                        "severity", "HIGH"))
                                .build()))
                .processingTimeMs(50L)
                .build();
    }

    // ==================== CONTRADICTION ENDPOINT TESTS ====================

    @Nested
    @DisplayName("Contradiction Endpoints")
    class ContradictionEndpointTests {

        @Test
        @DisplayName("GET /contradictions - should return all contradictions")
        void getAllContradictions_ShouldReturnResults() {
            when(queryService.getAllContradictions()).thenReturn(mockContradictionResponse);

            ResponseEntity<QueryResponse> response = queryController.getAllContradictions();

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().getResults().size());
            assertEquals("CONTRADICTION", response.getBody().getResults().get(0).getType());
            verify(queryService, times(1)).getAllContradictions();
        }

        @Test
        @DisplayName("GET /contradictions - should handle empty results")
        void getAllContradictions_ShouldHandleEmpty() {
            QueryResponse emptyResponse = QueryResponse.builder()
                    .query("contradictions:all")
                    .results(Collections.emptyList())
                    .processingTimeMs(10L)
                    .build();

            when(queryService.getAllContradictions()).thenReturn(emptyResponse);

            ResponseEntity<QueryResponse> response = queryController.getAllContradictions();

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().getResults().isEmpty());
        }

        @Test
        @DisplayName("GET /contradictions/context/{contextId} - should return contradictions for context")
        void getContradictionsForContext_ShouldReturnResults() {
            QueryResponse contextResponse = QueryResponse.builder()
                    .query("contradictions:" + testUuid)
                    .results(mockContradictionResponse.getResults())
                    .processingTimeMs(30L)
                    .build();

            when(queryService.getContradictionsForContext(testUuid)).thenReturn(contextResponse);

            ResponseEntity<QueryResponse> response = queryController.getContradictionsForContext(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, response.getBody().getResults().size());
            verify(queryService, times(1)).getContradictionsForContext(testUuid);
        }

        @Test
        @DisplayName("POST /contradictions/{id}/resolve - should resolve contradiction")
        void resolveContradiction_ShouldReturnOk() {
            doNothing().when(queryService).resolveContradiction(testUuid);

            ResponseEntity<Void> response = queryController.resolveContradiction(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(queryService, times(1)).resolveContradiction(testUuid);
        }
    }

    // ==================== INTENT STATS ENDPOINT TESTS ====================

    @Nested
    @DisplayName("Intent Stats Endpoint")
    class IntentStatsTests {

        @Test
        @DisplayName("GET /intent-stats - should return stats for context")
        void getIntentStats_ShouldReturnStats() {
            Map<String, Object> stats = Map.of(
                    "contextId", testUuid,
                    "totalRetrievals", 5,
                    "weightedSuccesses", 3.0,
                    "estimatedBoost", "0.1200");

            when(queryService.getIntentStats(testUuid)).thenReturn(stats);

            ResponseEntity<Map<String, Object>> response = queryController.getIntentStats(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(testUuid, response.getBody().get("contextId"));
            assertEquals(5, response.getBody().get("totalRetrievals"));
            verify(queryService, times(1)).getIntentStats(testUuid);
        }

        @Test
        @DisplayName("GET /intent-stats - should handle zero stats")
        void getIntentStats_ShouldHandleZeroStats() {
            Map<String, Object> emptyStats = Map.of(
                    "contextId", testUuid,
                    "totalRetrievals", 0,
                    "weightedSuccesses", 0.0,
                    "estimatedBoost", "0.0000");

            when(queryService.getIntentStats(testUuid)).thenReturn(emptyStats);

            ResponseEntity<Map<String, Object>> response = queryController.getIntentStats(testUuid);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0, response.getBody().get("totalRetrievals"));
            assertEquals("0.0000", response.getBody().get("estimatedBoost"));
        }
    }

    // ==================== CROSS-FEATURE TESTS ====================

    @Nested
    @DisplayName("Cross-feature verification")
    class CrossFeatureTests {

        @Test
        @DisplayName("Contradiction and intent stats endpoints should not interfere with each other")
        void contradictionAndIntentStatsShouldNotInterfere() {
            when(queryService.getAllContradictions()).thenReturn(mockContradictionResponse);
            when(queryService.getIntentStats(testUuid)).thenReturn(
                    Map.of("contextId", testUuid, "totalRetrievals", 0,
                            "weightedSuccesses", 0.0, "estimatedBoost", "0.0000"));

            // Call both endpoints
            ResponseEntity<QueryResponse> contradictions = queryController.getAllContradictions();
            ResponseEntity<Map<String, Object>> stats = queryController.getIntentStats(testUuid);

            // Both should work independently
            assertEquals(HttpStatus.OK, contradictions.getStatusCode());
            assertEquals(HttpStatus.OK, stats.getStatusCode());
            assertEquals(1, contradictions.getBody().getResults().size());
            assertEquals(0, stats.getBody().get("totalRetrievals"));
        }
    }
}
