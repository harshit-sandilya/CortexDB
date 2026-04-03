package com.vectornode.memory.ingest.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.ingest.service.ContradictionDetector;
import com.vectornode.memory.query.repository.ContextRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContradictionDetector.
 * Tests contradiction detection flow, LLM interaction parsing,
 * canonical UUID ordering, and merge resolution.
 */
@ExtendWith(MockitoExtension.class)
class ContradictionDetectorTest {

    @Mock
    private ContextRepository contextRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    private ContradictionDetector contradictionDetector;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        contradictionDetector = new ContradictionDetector(contextRepository, objectMapper);
        ReflectionTestUtils.setField(contradictionDetector, "entityManager", entityManager);
    }

    // ==================== CHECK AND PERSIST TESTS ====================

    @Nested
    @DisplayName("checkAndPersistAsync")
    class CheckAndPersistTests {

        @Test
        @DisplayName("should skip when no candidates found in similarity band")
        void shouldSkipWhenNoCandidates() {
            UUID contextId = UUID.randomUUID();
            float[] embedding = new float[] { 0.1f, 0.2f };

            when(contextRepository.findInSimilarityBand(anyString(), anyDouble(), anyDouble(), anyInt(), eq(contextId)))
                    .thenReturn(Collections.emptyList());

            contradictionDetector.checkAndPersistAsync("test text", embedding, contextId);

            // Should not attempt any LLM call or persist
            verify(entityManager, never()).createNativeQuery(anyString());
        }

        @Test
        @DisplayName("should detect contradiction and persist when LLM says contradicts")
        void shouldDetectAndPersistContradiction() {
            UUID contextId = UUID.randomUUID();
            UUID candidateId = UUID.randomUUID();
            float[] embedding = new float[] { 0.1f };

            // Candidate in similarity band
            Object[] candidate = new Object[] { candidateId, "Earth is flat.", 0.75 };
            when(contextRepository.findInSimilarityBand(anyString(), anyDouble(), anyDouble(), anyInt(), eq(contextId)))
                    .thenReturn(Collections.singletonList(candidate));

            // LLM returns contradiction
            String llmResponse = """
                    {"contradicts": true, "severity": "HIGH", "summary": "Direct factual conflict about Earth shape"}
                    """;

            // Mock insert query
            when(entityManager.createNativeQuery(contains("INSERT INTO context_contradictions")))
                    .thenReturn(nativeQuery);
            when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
            when(nativeQuery.executeUpdate()).thenReturn(1);

            try (MockedStatic<LLMProvider> llm = mockStatic(LLMProvider.class)) {
                llm.when(() -> LLMProvider.callLLM(anyString())).thenReturn(llmResponse);

                contradictionDetector.checkAndPersistAsync("Earth is round.", embedding, contextId);

                // Should have persisted the contradiction
                verify(entityManager).createNativeQuery(contains("INSERT INTO context_contradictions"));
            }
        }

        @Test
        @DisplayName("should not persist when LLM says no contradiction")
        void shouldNotPersistWhenNoContradiction() {
            UUID contextId = UUID.randomUUID();
            UUID candidateId = UUID.randomUUID();
            float[] embedding = new float[] { 0.1f };

            Object[] candidate = new Object[] { candidateId, "Complementary fact.", 0.70 };
            when(contextRepository.findInSimilarityBand(anyString(), anyDouble(), anyDouble(), anyInt(), eq(contextId)))
                    .thenReturn(Collections.singletonList(candidate));

            String llmResponse = """
                    {"contradicts": false, "severity": "LOW", "summary": null}
                    """;

            try (MockedStatic<LLMProvider> llm = mockStatic(LLMProvider.class)) {
                llm.when(() -> LLMProvider.callLLM(anyString())).thenReturn(llmResponse);

                contradictionDetector.checkAndPersistAsync("A related fact.", embedding, contextId);

                verify(entityManager, never()).createNativeQuery(contains("INSERT"));
            }
        }

        @Test
        @DisplayName("should handle malformed LLM JSON gracefully")
        void shouldHandleMalformedLLMResponse() {
            UUID contextId = UUID.randomUUID();
            UUID candidateId = UUID.randomUUID();
            float[] embedding = new float[] { 0.1f };

            Object[] candidate = new Object[] { candidateId, "Some text.", 0.72 };
            when(contextRepository.findInSimilarityBand(anyString(), anyDouble(), anyDouble(), anyInt(), eq(contextId)))
                    .thenReturn(Collections.singletonList(candidate));

            try (MockedStatic<LLMProvider> llm = mockStatic(LLMProvider.class)) {
                llm.when(() -> LLMProvider.callLLM(anyString())).thenReturn("NOT_VALID_JSON");

                // Should not throw
                contradictionDetector.checkAndPersistAsync("Text.", embedding, contextId);

                // Malformed response defaults to contradicts=false, so no persist
                verify(entityManager, never()).createNativeQuery(contains("INSERT"));
            }
        }

        @Test
        @DisplayName("should strip markdown fences from LLM response")
        void shouldStripMarkdownFences() {
            UUID contextId = UUID.randomUUID();
            UUID candidateId = UUID.randomUUID();
            float[] embedding = new float[] { 0.1f };

            Object[] candidate = new Object[] { candidateId, "Old fact.", 0.68 };
            when(contextRepository.findInSimilarityBand(anyString(), anyDouble(), anyDouble(), anyInt(), eq(contextId)))
                    .thenReturn(Collections.singletonList(candidate));

            when(entityManager.createNativeQuery(contains("INSERT INTO context_contradictions")))
                    .thenReturn(nativeQuery);
            when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
            when(nativeQuery.executeUpdate()).thenReturn(1);

            // LLM response wrapped in markdown code fences
            String llmResponse = "```json\n{\"contradicts\": true, \"severity\": \"MODERATE\", \"summary\": \"Partial conflict\"}\n```";

            try (MockedStatic<LLMProvider> llm = mockStatic(LLMProvider.class)) {
                llm.when(() -> LLMProvider.callLLM(anyString())).thenReturn(llmResponse);

                contradictionDetector.checkAndPersistAsync("New fact.", embedding, contextId);

                verify(entityManager).createNativeQuery(contains("INSERT INTO context_contradictions"));
            }
        }
    }

    // ==================== MARK RESOLVED TESTS ====================

    @Nested
    @DisplayName("markResolvedAsync")
    class MarkResolvedTests {

        @Test
        @DisplayName("should update resolved status for matching contradictions")
        void shouldMarkResolved() {
            UUID contextId = UUID.randomUUID();
            UUID resolvedById = UUID.randomUUID();

            when(entityManager.createNativeQuery(contains("UPDATE context_contradictions")))
                    .thenReturn(nativeQuery);
            when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
            when(nativeQuery.executeUpdate()).thenReturn(2);

            contradictionDetector.markResolvedAsync(contextId, resolvedById);

            verify(entityManager).createNativeQuery(contains("UPDATE context_contradictions"));
            verify(nativeQuery).setParameter("resolvedBy", resolvedById);
            verify(nativeQuery).setParameter("id", contextId);
        }

        @Test
        @DisplayName("should handle zero updates gracefully (no contradictions to resolve)")
        void shouldHandleZeroUpdates() {
            UUID contextId = UUID.randomUUID();

            when(entityManager.createNativeQuery(contains("UPDATE context_contradictions")))
                    .thenReturn(nativeQuery);
            when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
            when(nativeQuery.executeUpdate()).thenReturn(0);

            // Should not throw
            contradictionDetector.markResolvedAsync(contextId, contextId);

            verify(nativeQuery).executeUpdate();
        }
    }
}
