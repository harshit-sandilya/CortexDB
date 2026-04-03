package com.vectornode.memory.query.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.entity.QueryLog;
import com.vectornode.memory.query.dto.response.QueryResponse;
import com.vectornode.memory.query.repository.QueryLogRepository;
import com.vectornode.memory.query.service.QueryMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueryMemoryService.
 * Tests intent-memory boost computation, boost application, query logging,
 * follow-up detection, and intent stats.
 */
@ExtendWith(MockitoExtension.class)
class QueryMemoryServiceTest {

    @Mock
    private QueryLogRepository queryLogRepository;

    private QueryMemoryService queryMemoryService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        queryMemoryService = new QueryMemoryService(queryLogRepository, objectMapper);
    }

    // ==================== COMPUTE BOOSTS TESTS ====================

    @Nested
    @DisplayName("computeBoosts")
    class ComputeBoostsTests {

        @Test
        @DisplayName("should return empty map when no similar past queries exist")
        void shouldReturnEmptyBoostsWhenNoPastQueries() {
            float[] embedding = new float[] { 0.1f, 0.2f, 0.3f };

            when(queryLogRepository.findSimilarPastQueries(anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.emptyList());

            Map<UUID, Double> boosts = queryMemoryService.computeBoosts(embedding);

            assertNotNull(boosts);
            assertTrue(boosts.isEmpty());
        }

        @Test
        @DisplayName("should compute boosts for contexts from successful past queries")
        void shouldComputeBoostsForSuccessfulPastQueries() {
            float[] embedding = new float[] { 0.1f, 0.2f, 0.3f };
            UUID contextId1 = UUID.randomUUID();
            UUID contextId2 = UUID.randomUUID();

            // Past query: session was continued (success), 1 follow-up
            String contextIdsJson = "[\"" + contextId1 + "\",\"" + contextId2 + "\"]";
            Object[] pastQuery = new Object[] {
                    UUID.randomUUID(), // id
                    "past query text", // query_text
                    contextIdsJson, // retrieved_context_ids
                    true, // session_continued
                    1, // follow_up_count
                    0.90 // similarity_score
            };

            when(queryLogRepository.findSimilarPastQueries(anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.singletonList(pastQuery));

            Map<UUID, Double> boosts = queryMemoryService.computeBoosts(embedding);

            assertNotNull(boosts);
            assertFalse(boosts.isEmpty());
            // Both contexts should have boosts since the query was successful
            assertTrue(boosts.containsKey(contextId1));
            assertTrue(boosts.containsKey(contextId2));
            // Boosts should be positive and capped
            assertTrue(boosts.get(contextId1) > 0.0);
            assertTrue(boosts.get(contextId1) <= 0.20); // MAX_BOOST
        }

        @Test
        @DisplayName("should not boost contexts from unsuccessful queries")
        void shouldNotBoostUnsuccessfulQueries() {
            float[] embedding = new float[] { 0.1f };
            UUID contextId = UUID.randomUUID();

            // Past query: NOT continued (unsuccessful)
            String contextIdsJson = "[\"" + contextId + "\"]";
            Object[] pastQuery = new Object[] {
                    UUID.randomUUID(), "text", contextIdsJson,
                    false, // not continued
                    0, // no follow-ups
                    0.85
            };

            when(queryLogRepository.findSimilarPastQueries(anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.singletonList(pastQuery));

            Map<UUID, Double> boosts = queryMemoryService.computeBoosts(embedding);

            assertTrue(boosts.isEmpty(), "Unsuccessful queries should produce no boosts");
        }

        @Test
        @DisplayName("should handle null/blank context IDs gracefully")
        void shouldHandleNullContextIds() {
            float[] embedding = new float[] { 0.1f };

            Object[] pastQuery = new Object[] {
                    UUID.randomUUID(), "text",
                    null, // null context IDs
                    true, 1, 0.90
            };

            when(queryLogRepository.findSimilarPastQueries(anyString(), anyDouble(), anyInt()))
                    .thenReturn(Collections.singletonList(pastQuery));

            Map<UUID, Double> boosts = queryMemoryService.computeBoosts(embedding);

            assertTrue(boosts.isEmpty());
        }
    }

    // ==================== APPLY BOOSTS TESTS ====================

    @Nested
    @DisplayName("applyBoosts")
    class ApplyBoostsTests {

        @Test
        @DisplayName("should return original results when no boosts exist")
        void shouldReturnOriginalResultsWhenNoBoosts() {
            List<QueryResponse.SearchResult> results = List.of(
                    QueryResponse.SearchResult.builder()
                            .id(UUID.randomUUID())
                            .content("test")
                            .score(0.9)
                            .type("CHUNK")
                            .metadata(Map.of())
                            .build());

            List<QueryResponse.SearchResult> boosted = queryMemoryService.applyBoosts(results, Collections.emptyMap());

            assertEquals(results, boosted, "Should return same results when no boosts");
        }

        @Test
        @DisplayName("should add boost to score and re-sort results")
        void shouldAddBoostAndResort() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            List<QueryResponse.SearchResult> results = List.of(
                    QueryResponse.SearchResult.builder()
                            .id(id1).content("a").score(0.9).type("CHUNK")
                            .metadata(new HashMap<>()).build(),
                    QueryResponse.SearchResult.builder()
                            .id(id2).content("b").score(0.8).type("CHUNK")
                            .metadata(new HashMap<>()).build());

            // Boost id2 enough to overtake id1
            Map<UUID, Double> boosts = Map.of(id2, 0.15);

            List<QueryResponse.SearchResult> boosted = queryMemoryService.applyBoosts(results, boosts);

            assertEquals(2, boosted.size());
            // id2 (0.8+0.15=0.95) should now be first, id1 (0.9) should be second
            assertEquals(id2, boosted.get(0).getId());
            assertEquals(id1, boosted.get(1).getId());
            assertEquals(0.95, boosted.get(0).getScore(), 0.001);
        }

        @Test
        @DisplayName("should add intentMemoryBoost metadata when boost > 0")
        void shouldAddBoostMetadata() {
            UUID id = UUID.randomUUID();

            List<QueryResponse.SearchResult> results = List.of(
                    QueryResponse.SearchResult.builder()
                            .id(id).content("a").score(0.9).type("CHUNK")
                            .metadata(new HashMap<>()).build());

            Map<UUID, Double> boosts = Map.of(id, 0.1);

            List<QueryResponse.SearchResult> boosted = queryMemoryService.applyBoosts(results, boosts);

            assertTrue(boosted.get(0).getMetadata().containsKey("intentMemoryBoost"));
        }
    }

    // ==================== LOG QUERY TESTS ====================

    @Nested
    @DisplayName("logQueryAsync")
    class LogQueryTests {

        @Test
        @DisplayName("should persist query log")
        void shouldPersistQueryLog() {
            float[] embedding = new float[] { 0.1f };
            List<UUID> contextIds = List.of(UUID.randomUUID());

            queryMemoryService.logQueryAsync("user1", "test query", embedding, contextIds);

            verify(queryLogRepository).save(any(QueryLog.class));
        }

        @Test
        @DisplayName("should detect follow-up for identified user")
        void shouldDetectFollowUpForIdentifiedUser() {
            float[] embedding = new float[] { 0.1f };
            List<UUID> contextIds = List.of(UUID.randomUUID());

            // Mock a recent previous query
            QueryLog previousLog = QueryLog.builder()
                    .id(UUID.randomUUID())
                    .uid("user1")
                    .queryText("previous")
                    .queryEmbedding(embedding)
                    .followUpCount(0)
                    .build();

            when(queryLogRepository.findRecentByUid(eq("user1"), any(Instant.class), any(Instant.class)))
                    .thenReturn(Optional.of(previousLog));

            queryMemoryService.logQueryAsync("user1", "follow-up query", embedding, contextIds);

            verify(queryLogRepository).save(any(QueryLog.class));
            verify(queryLogRepository).markSessionContinued(eq(previousLog.getId()), any(Instant.class));
        }

        @Test
        @DisplayName("should skip follow-up detection when uid is null")
        void shouldSkipFollowUpWhenNoUid() {
            float[] embedding = new float[] { 0.1f };
            List<UUID> contextIds = List.of(UUID.randomUUID());

            queryMemoryService.logQueryAsync(null, "query", embedding, contextIds);

            verify(queryLogRepository).save(any(QueryLog.class));
            verify(queryLogRepository, never()).findRecentByUid(any(), any(), any());
        }
    }

    // ==================== INTENT STATS TESTS ====================

    @Nested
    @DisplayName("getIntentStats")
    class IntentStatsTests {

        @Test
        @DisplayName("should return zero stats when no matching logs exist")
        void shouldReturnZeroStatsWhenNoLogs() {
            UUID contextId = UUID.randomUUID();

            when(queryLogRepository.findLogsContainingContext(anyString()))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> stats = queryMemoryService.getIntentStats(contextId);

            assertNotNull(stats);
            assertEquals(contextId, stats.get("contextId"));
            assertEquals(0, stats.get("totalRetrievals"));
            assertEquals(0.0, stats.get("weightedSuccesses"));
            assertEquals("0.0000", stats.get("estimatedBoost"));
        }

        @Test
        @DisplayName("should compute accurate stats from matching logs")
        void shouldComputeStatsFromMatchingLogs() {
            UUID contextId = UUID.randomUUID();

            // Two matching logs: one successful (continued), one not
            Object[] log1 = new Object[] { true, 2 }; // continued, 2 follow-ups
            Object[] log2 = new Object[] { false, 0 }; // not continued

            when(queryLogRepository.findLogsContainingContext(anyString()))
                    .thenReturn(Arrays.asList(log1, log2));

            Map<String, Object> stats = queryMemoryService.getIntentStats(contextId);

            assertEquals(2, stats.get("totalRetrievals"));
            assertEquals(3.0, stats.get("weightedSuccesses")); // 1.0 + 2 = 3.0
        }
    }
}
