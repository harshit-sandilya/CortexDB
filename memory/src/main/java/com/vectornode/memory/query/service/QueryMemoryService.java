package com.vectornode.memory.query.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.query.dto.response.QueryResponse;
import com.vectornode.memory.query.repository.QueryLogRepository;
import com.vectornode.memory.entity.QueryLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core logic for Query Intent Memory (Feature 1).
 * <p>
 * Computes intent-based boosts from historical query-retrieval patterns,
 * applies those boosts to current search results, and logs each query
 * for future learning. Follow-up detection piggybacks on each new query.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryMemoryService {

    private final QueryLogRepository queryLogRepository;
    private final ObjectMapper objectMapper;

    // ── Tuning constants ────────────────────────────────────────────
    private static final double SIMILARITY_THRESHOLD = 0.82;
    private static final int MAX_PAST_QUERIES = 10;
    private static final double BASE_BOOST = 0.15;
    private static final double MAX_BOOST = 0.20;
    private static final int FOLLOW_UP_WINDOW_MINUTES = 30;

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Compute intent-memory boosts for each context UUID.
     * Returns a map: contextId → boost value (0.0 – MAX_BOOST).
     */
    public Map<UUID, Double> computeBoosts(float[] queryEmbedding) {
        String vectorStr = toVectorString(queryEmbedding);
        Map<UUID, Double> boosts = new HashMap<>();

        List<Object[]> pastQueries = queryLogRepository
                .findSimilarPastQueries(vectorStr, SIMILARITY_THRESHOLD, MAX_PAST_QUERIES);

        if (pastQueries.isEmpty()) {
            return boosts;
        }

        // Accumulate per-context stats across all similar past queries
        Map<UUID, Integer> totalAppearances = new HashMap<>();
        Map<UUID, Double> weightedSuccesses = new HashMap<>();

        for (Object[] row : pastQueries) {
            String contextIdsJson = (String) row[2];
            boolean continued = (Boolean) row[3];
            int followUpCount = ((Number) row[4]).intValue();

            if (contextIdsJson == null || contextIdsJson.isBlank()) {
                continue;
            }

            List<String> contextIds = parseContextIds(contextIdsJson);

            for (String idStr : contextIds) {
                UUID ctxId;
                try {
                    ctxId = UUID.fromString(idStr.trim());
                } catch (IllegalArgumentException e) {
                    continue; // skip malformed
                }

                totalAppearances.merge(ctxId, 1, Integer::sum);

                if (continued) {
                    double successWeight = 1.0 + followUpCount;
                    weightedSuccesses.merge(ctxId, successWeight, Double::sum);
                }
            }
        }

        // Compute boost for each context that appeared
        for (UUID ctxId : totalAppearances.keySet()) {
            int appearances = totalAppearances.get(ctxId);
            double successes = weightedSuccesses.getOrDefault(ctxId, 0.0);

            if (successes == 0.0) {
                continue; // no successes → no boost
            }

            double successRate = successes / appearances;
            double rawBoost = BASE_BOOST * successRate * Math.log(1 + successes);
            double boost = Math.min(rawBoost, MAX_BOOST);

            boosts.put(ctxId, boost);
        }

        log.debug("INTENT_MEMORY | computed boosts for {} contexts from {} past queries",
                boosts.size(), pastQueries.size());

        return boosts;
    }

    /**
     * Apply pre-computed boosts to search results.
     * Adds the boost to each result's score and includes it in metadata.
     * Returns a new list re-sorted by boosted score (descending).
     */
    public List<QueryResponse.SearchResult> applyBoosts(
            List<QueryResponse.SearchResult> results,
            Map<UUID, Double> boosts) {

        if (boosts.isEmpty()) {
            return results;
        }

        List<QueryResponse.SearchResult> boosted = new ArrayList<>();
        for (QueryResponse.SearchResult result : results) {
            double boost = boosts.getOrDefault(result.getId(), 0.0);
            double newScore = result.getScore() + boost;

            Map<String, Object> enrichedMeta = new HashMap<>(
                    result.getMetadata() != null ? result.getMetadata() : Map.of());

            if (boost > 0.0) {
                enrichedMeta.put("intentMemoryBoost", String.format("%.4f", boost));
            }

            boosted.add(QueryResponse.SearchResult.builder()
                    .id(result.getId())
                    .content(result.getContent())
                    .score(newScore)
                    .type(result.getType())
                    .metadata(enrichedMeta)
                    .build());
        }

        // Re-sort descending by boosted score
        boosted.sort(Comparator.comparingDouble(QueryResponse.SearchResult::getScore).reversed());
        return boosted;
    }

    /**
     * Log the current query and detect follow-up sessions.
     * Runs async so it never adds latency to the query response.
     */
    @Async
    @Transactional
    public void logQueryAsync(String uid, String queryText,
            float[] queryEmbedding,
            List<UUID> retrievedContextIds) {

        // 1. Persist query log
        String contextIdsJson;
        try {
            contextIdsJson = objectMapper.writeValueAsString(retrievedContextIds);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize retrieved context IDs", e);
            contextIdsJson = "[]";
        }

        QueryLog queryLog = QueryLog.builder()
                .uid(uid)
                .queryText(queryText)
                .queryEmbedding(queryEmbedding)
                .retrievedContextIds(contextIdsJson)
                .build();

        queryLogRepository.save(queryLog);

        // 2. Follow-up detection (only for identified users)
        if (uid != null && !uid.isBlank()) {
            detectAndMarkFollowUp(uid);
        }
    }

    /**
     * Get intent stats for a specific context.
     * Returns: totalRetrieval count, successfulRetrievals, boostEstimate.
     */
    public Map<String, Object> getIntentStats(UUID contextId) {
        String targetId = contextId.toString();

        List<Object[]> matchingLogs = queryLogRepository.findLogsContainingContext(targetId);

        int totalAppearances = matchingLogs.size();
        double weightedSuccesses = 0;

        for (Object[] row : matchingLogs) {
            boolean continued = (Boolean) row[0];
            int followUpCount = ((Number) row[1]).intValue();
            if (continued) {
                weightedSuccesses += 1.0 + followUpCount;
            }
        }

        double successRate = totalAppearances > 0 ? weightedSuccesses / totalAppearances : 0;
        double boost = totalAppearances > 0
                ? Math.min(BASE_BOOST * successRate * Math.log(1 + weightedSuccesses), MAX_BOOST)
                : 0;

        return Map.of(
                "contextId", contextId,
                "totalRetrievals", totalAppearances,
                "weightedSuccesses", weightedSuccesses,
                "estimatedBoost", String.format("%.4f", boost));
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * If the same uid made a query in the last FOLLOW_UP_WINDOW_MINUTES,
     * retroactively mark that earlier query as session_continued.
     */
    private void detectAndMarkFollowUp(String uid) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(FOLLOW_UP_WINDOW_MINUTES, ChronoUnit.MINUTES);

        // Find the most recent *previous* query from this user within the window
        // (the one we just saved will be the newest, so we need the one before it)
        Optional<QueryLog> previous = queryLogRepository.findRecentByUid(uid, now, windowStart);

        if (previous.isPresent()) {
            QueryLog prev = previous.get();
            queryLogRepository.markSessionContinued(prev.getId(), now);
            log.info("FOLLOW_UP_DETECTED | uid={} | previous_query_id={} | follow_up_count={}",
                    uid, prev.getId(), prev.getFollowUpCount() + 1);
        }
    }

    private List<String> parseContextIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse retrieved_context_ids JSON: {}", json);
            return List.of();
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
}
