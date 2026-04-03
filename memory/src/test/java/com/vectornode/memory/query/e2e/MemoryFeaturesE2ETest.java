package com.vectornode.memory.query.e2e;

import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.enums.ConverserRole;
import com.vectornode.memory.query.dto.response.QueryResponse;
import com.vectornode.memory.query.repository.ContextRepository;
import com.vectornode.memory.query.repository.KnowledgeBaseRepository;
import com.vectornode.memory.query.service.QueryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End tests for Contradiction Detection and Query Intent Memory
 * features.
 * Tests the complete flow through real database with Testcontainers.
 *
 * Note: Tests that require embedding generation are not included because
 * they depend on external LLM APIs which may not be available in CI.
 *
 * Run with: docker compose run --rm backend-tests
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoints.enabled-by-default=false",
        "management.endpoint.health.enabled=false",
        "spring.ai.azure.openai.endpoint=https://dummy.openai.azure.com",
        "spring.ai.azure.openai.api-key=dummy-key",
        "spring.ai.openai.api-key=dummy-key",
        "spring.ai.openai.base-url=https://dummy.openai.com"
})
@Transactional
class MemoryFeaturesE2ETest {

    @Autowired
    private QueryService queryService;

    @Autowired
    private ContextRepository contextRepository;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private EntityManager entityManager;

    private Context savedContextA;
    private Context savedContextB;

    @BeforeEach
    void setUp() {
        // Create test knowledge base
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUid("test-user-memory");
        kb.setConverser(ConverserRole.USER);
        kb.setContent("Test content for memory features");
        kb.setVectorEmbedding(create768Vector(0.5f));
        kb = knowledgeBaseRepository.save(kb);

        // Create two contexts that could potentially contradict each other
        savedContextA = new Context();
        savedContextA.setKnowledgeBase(kb);
        savedContextA.setTextChunk("The speed of light is 300,000 km/s.");
        savedContextA.setChunkIndex(0);
        savedContextA.setVectorEmbedding(create768VectorWithPattern(0.5f, 1));
        savedContextA = contextRepository.save(savedContextA);

        savedContextB = new Context();
        savedContextB.setKnowledgeBase(kb);
        savedContextB.setTextChunk("Light travels at approximately 299,792 km/s.");
        savedContextB.setChunkIndex(1);
        savedContextB.setVectorEmbedding(create768VectorWithPattern(0.5f, 2));
        savedContextB = contextRepository.save(savedContextB);

        entityManager.flush();
    }

    // ==================== CONTRADICTION ENDPOINT TESTS ====================

    @Nested
    @DisplayName("Contradiction Endpoints (no LLM required)")
    class ContradictionEndpointTests {

        @Test
        @DisplayName("E2E: getAllContradictions should return empty when no contradictions exist")
        void getAllContradictionsShouldReturnEmpty() {
            QueryResponse response = queryService.getAllContradictions();

            assertNotNull(response, "Response should not be null");
            assertNotNull(response.getResults(), "Results should not be null");
            assertTrue(response.getResults().isEmpty(), "Should have no contradictions initially");
            assertEquals("contradictions:all", response.getQuery());
            assertTrue(response.getProcessingTimeMs() >= 0);
        }

        @Test
        @DisplayName("E2E: getContradictionsForContext should return empty for clean context")
        void getContradictionsForContextShouldReturnEmpty() {
            QueryResponse response = queryService.getContradictionsForContext(savedContextA.getId());

            assertNotNull(response, "Response should not be null");
            assertTrue(response.getResults().isEmpty(), "Clean context should have no contradictions");
        }

        @Test
        @DisplayName("E2E: getAllContradictions should find contradictions after manual insert")
        void getAllContradictionsShouldFindAfterInsert() {
            // Manually insert a contradiction (simulating what ContradictionDetector would
            // do)
            UUID contradictionId = insertContradiction(savedContextA.getId(), savedContextB.getId(),
                    "Values differ for speed of light", "LOW");

            QueryResponse response = queryService.getAllContradictions();

            assertNotNull(response);
            assertEquals(1, response.getResults().size());
            assertEquals("CONTRADICTION", response.getResults().get(0).getType());
            assertTrue(response.getResults().get(0).getMetadata().containsKey("severity"));
            assertTrue(response.getResults().get(0).getMetadata().containsKey("summary"));
        }

        @Test
        @DisplayName("E2E: getContradictionsForContext should find contradictions for involved context")
        void getContradictionsForContextShouldFindForInvolvedContext() {
            insertContradiction(savedContextA.getId(), savedContextB.getId(),
                    "Speed of light values differ", "MODERATE");

            // Check from side A
            QueryResponse responseA = queryService.getContradictionsForContext(savedContextA.getId());
            assertFalse(responseA.getResults().isEmpty(), "Context A should have contradictions");

            // Check from side B
            QueryResponse responseB = queryService.getContradictionsForContext(savedContextB.getId());
            assertFalse(responseB.getResults().isEmpty(), "Context B should also have contradictions");
        }

        @Test
        @DisplayName("E2E: resolveContradiction should mark contradiction as resolved")
        void resolveContradictionShouldMarkResolved() {
            UUID contradictionId = insertContradiction(savedContextA.getId(), savedContextB.getId(),
                    "Test conflict", "HIGH");

            // Before resolve: should appear
            QueryResponse before = queryService.getAllContradictions();
            assertEquals(1, before.getResults().size());

            // Resolve it
            queryService.resolveContradiction(contradictionId);
            entityManager.flush();
            entityManager.clear();

            // After resolve: should not appear (we only show unresolved)
            QueryResponse after = queryService.getAllContradictions();
            assertTrue(after.getResults().isEmpty(), "Resolved contradictions should not appear");
        }

        @Test
        @DisplayName("E2E: resolveContradiction with non-existent ID should not throw")
        void resolveNonExistentContradictionShouldNotThrow() {
            assertDoesNotThrow(() -> queryService.resolveContradiction(UUID.randomUUID()));
        }
    }

    // ==================== INTENT STATS TESTS ====================

    @Nested
    @DisplayName("Intent Stats Endpoint (no LLM required)")
    class IntentStatsTests {

        @Test
        @DisplayName("E2E: getIntentStats should return zero stats for new context")
        void getIntentStatsShouldReturnZeroForNewContext() {
            var stats = queryService.getIntentStats(savedContextA.getId());

            assertNotNull(stats);
            assertEquals(savedContextA.getId(), stats.get("contextId"));
            assertEquals(0, stats.get("totalRetrievals"));
            assertEquals(0.0, stats.get("weightedSuccesses"));
            assertEquals("0.0000", stats.get("estimatedBoost"));
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Manually insert a contradiction row. Returns the contradiction UUID.
     * Uses canonical ordering (smaller UUID first).
     */
    private UUID insertContradiction(UUID contextIdA, UUID contextIdB, String summary, String severity) {
        UUID first = contextIdA.compareTo(contextIdB) < 0 ? contextIdA : contextIdB;
        UUID second = contextIdA.compareTo(contextIdB) < 0 ? contextIdB : contextIdA;

        UUID contradictionId = UUID.randomUUID();

        entityManager.createNativeQuery("""
                INSERT INTO context_contradictions
                    (id, context_id_a, context_id_b, contradiction_summary, severity, resolved)
                VALUES
                    (:id, :a, :b, :summary, :severity, false)
                """)
                .setParameter("id", contradictionId)
                .setParameter("a", first)
                .setParameter("b", second)
                .setParameter("summary", summary)
                .setParameter("severity", severity)
                .executeUpdate();

        entityManager.flush();
        return contradictionId;
    }

    private float[] create768Vector(float value) {
        float[] vector = new float[768];
        Arrays.fill(vector, value);
        return vector;
    }

    private float[] create768VectorWithPattern(float baseValue, int patternIndex) {
        float[] vector = new float[768];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = baseValue + (float) Math.sin((i + patternIndex) * 0.01);
        }
        return vector;
    }
}
