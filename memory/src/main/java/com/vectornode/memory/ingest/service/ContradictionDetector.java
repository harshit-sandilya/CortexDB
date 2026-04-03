package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.query.repository.ContextRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Detects contradictions between newly ingested chunks and existing memories.
 * <p>
 * Operates in the similarity band [0.60, 0.85) — chunks that are related
 * enough to be about the same topic, but distinct enough that they might
 * be saying opposite things. Chunks above 0.85 are handled by SimpleMem merge;
 * below 0.60 are considered unrelated.
 * <p>
 * Always called after persist+flush so the context has a real UUID.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContradictionDetector {

    @PersistenceContext
    private EntityManager entityManager;

    private final ContextRepository contextRepository;
    private final ObjectMapper objectMapper;

    private static final double BAND_LOWER = 0.60;
    private static final double BAND_UPPER = 0.85;
    private static final int MAX_CANDIDATES = 10;

    private static final String CONTRADICTION_PROMPT = """
            You are a fact-checking system. Determine if these two statements contradict each other.

            Statement A: "%s"
            Statement B: "%s"

            Respond ONLY with valid JSON, no markdown:
            {
              "contradicts": true or false,
              "severity": "LOW" or "MODERATE" or "HIGH",
              "summary": "One sentence explaining the contradiction, or null if none"
            }

            Severity rules:
            - HIGH: direct factual conflict (different values for same fact)
            - MODERATE: partial conflict, one statement partially invalidates the other
            - LOW: temporal inconsistency or subtle tension
            - false: statements are compatible, complementary, or about different subjects entirely
            """;

    // ── Public entry points ────────────────────────────────────────

    /**
     * Entry point — called after context is persisted and has a real ID.
     * Runs fully async, never blocks the ingest pipeline.
     */
    @Async
    @Transactional
    public void checkAndPersistAsync(
            String contextText,
            float[] embedding,
            UUID contextId) {

        log.info("CONTRADICTION_CHECK_START | context_id={}", contextId);

        String vectorStr = toVectorString(embedding);

        List<Object[]> candidates = contextRepository
                .findInSimilarityBand(vectorStr, BAND_LOWER, BAND_UPPER, MAX_CANDIDATES, contextId);

        if (candidates.isEmpty()) {
            log.debug("CONTRADICTION_CHECK_DONE | context_id={} | candidates=0", contextId);
            return;
        }

        for (Object[] row : candidates) {
            UUID candidateId = (UUID) row[0];
            String candidateText = (String) row[1];
            double similarity = ((Number) row[2]).doubleValue();

            ContradictionCheckResponse check = callLLMForContradiction(contextText, candidateText);

            if (check.contradicts()) {
                log.warn(
                        "CONTRADICTION_FOUND | context_a={} | context_b={} | " +
                                "severity={} | similarity={} | summary={}",
                        contextId, candidateId,
                        check.severity(), similarity, check.summary());

                persistContradiction(
                        contextId, candidateId,
                        check.summary(), check.severity());
            }
        }

        log.info("CONTRADICTION_CHECK_DONE | context_id={} | candidates_checked={}",
                contextId, candidates.size());
    }

    /**
     * Called after a SimpleMem MERGE resolves a conflict.
     * Marks all contradictions involving this context as resolved.
     */
    @Async
    @Transactional
    public void markResolvedAsync(UUID contextId, UUID resolvedByContextId) {
        int updated = entityManager.createNativeQuery("""
                UPDATE context_contradictions
                SET resolved = true,
                    resolution_context_id = :resolvedBy
                WHERE (context_id_a = :id OR context_id_b = :id)
                  AND resolved = false
                """)
                .setParameter("resolvedBy", resolvedByContextId)
                .setParameter("id", contextId)
                .executeUpdate();

        if (updated > 0) {
            log.info("CONTRADICTION_RESOLVED | context_id={} | resolved_by={} | count={}",
                    contextId, resolvedByContextId, updated);
        }
    }

    // ── Private helpers ────────────────────────────────────────────

    private void persistContradiction(
            UUID contextIdA, UUID contextIdB,
            String summary, String severity) {

        // Canonical ordering: smaller UUID first, so (A,B) and (B,A)
        // never create two separate rows for the same pair
        UUID first = contextIdA.compareTo(contextIdB) < 0 ? contextIdA : contextIdB;
        UUID second = contextIdA.compareTo(contextIdB) < 0 ? contextIdB : contextIdA;

        entityManager.createNativeQuery("""
                INSERT INTO context_contradictions
                    (id, context_id_a, context_id_b, contradiction_summary, severity)
                VALUES
                    (uuid_generate_v4(), :a, :b, :summary, :severity)
                ON CONFLICT (context_id_a, context_id_b) DO NOTHING
                """)
                .setParameter("a", first)
                .setParameter("b", second)
                .setParameter("summary", summary)
                .setParameter("severity", severity)
                .executeUpdate();
    }

    private ContradictionCheckResponse callLLMForContradiction(
            String textA, String textB) {
        String prompt = CONTRADICTION_PROMPT.formatted(textA, textB);
        String raw = com.vectornode.memory.config.LLMProvider.callLLM(prompt);
        return parseContradictionResponse(raw);
    }

    private ContradictionCheckResponse parseContradictionResponse(String json) {
        try {
            json = json.replaceAll("```json|```", "").trim();
            JsonNode root = objectMapper.readTree(json);
            return new ContradictionCheckResponse(
                    root.path("contradicts").asBoolean(false),
                    root.path("severity").asText("MODERATE"),
                    root.path("summary").asText(null));
        } catch (Exception e) {
            log.error("Failed to parse contradiction LLM response", e);
            return new ContradictionCheckResponse(false, "LOW", null);
        }
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1)
                sb.append(",");
        }
        return sb.append("]").toString();
    }

    private record ContradictionCheckResponse(
            boolean contradicts,
            String severity,
            String summary) {
    }
}
