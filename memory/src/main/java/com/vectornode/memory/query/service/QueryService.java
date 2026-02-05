package com.vectornode.memory.query.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.entity.Relation;
import com.vectornode.memory.query.dto.QueryRequest;
import com.vectornode.memory.query.dto.QueryResponse;
import com.vectornode.memory.query.repository.ContextRepository;
import com.vectornode.memory.query.repository.EntityRepository;
import com.vectornode.memory.query.repository.KnowledgeBaseRepository;
import com.vectornode.memory.query.repository.RelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryService {

    private final ContextRepository contextRepository;
    private final EntityRepository entityRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RelationRepository relationRepository;

    // ==================== CONTEXT OPERATIONS ====================

    // Semantic search on contexts with similarity scores
    public QueryResponse searchContexts(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Searching contexts for query: {}", request.getQuery());

        float[] embedding = LLMProvider.getEmbedding(request.getQuery());
        String vectorString = toVectorString(embedding);

        List<Object[]> rows = contextRepository.findSimilarWithScore(vectorString, request.getLimit());

        List<QueryResponse.SearchResult> results = rows.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .id((UUID) row[0])
                        .content((String) row[1])
                        .score(((Number) row[3]).doubleValue())
                        .type("CHUNK")
                        .metadata(Map.of("chunkIndex", Objects.requireNonNullElse(row[2], 0)))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Context search completed in {}ms, found {} results", totalTime, results.size());

        return QueryResponse.builder()
                .query(request.getQuery())
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get all contexts for a specific knowledge base
    public QueryResponse getContextsByKnowledgeBase(UUID kbId) {
        long startTime = System.currentTimeMillis();
        log.info("Fetching contexts for knowledge base: {}", kbId);

        List<Context> contexts = contextRepository.findByKnowledgeBaseId(kbId);

        List<QueryResponse.SearchResult> results = contexts.stream()
                .map(ctx -> QueryResponse.SearchResult.builder()
                        .id(ctx.getId())
                        .content(ctx.getTextChunk())
                        .score(1.0)
                        .type("CHUNK")
                        .metadata(Map.of("chunkIndex", Objects.requireNonNullElse(ctx.getChunkIndex(), 0)))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Fetched {} contexts for KB {} in {}ms", results.size(), kbId, totalTime);

        return QueryResponse.builder()
                .query("kb:" + kbId)
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get recent contexts (last N days)
    public QueryResponse getRecentContexts(int days) {
        long startTime = System.currentTimeMillis();
        log.info("Fetching contexts from last {} days", days);

        List<Context> contexts = contextRepository.findRecentContexts(days);

        List<QueryResponse.SearchResult> results = contexts.stream()
                .map(ctx -> QueryResponse.SearchResult.builder()
                        .id(ctx.getId())
                        .content(ctx.getTextChunk())
                        .score(1.0)
                        .type("CHUNK")
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Fetched {} recent contexts in {}ms", results.size(), totalTime);

        return QueryResponse.builder()
                .query("recent:" + days + "days")
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get contexts by date range
    public QueryResponse getContextsByDateRange(String startDate, String endDate) {
        long startTime = System.currentTimeMillis();
        log.info("Fetching contexts from {} to {}", startDate, endDate);

        List<Context> contexts = contextRepository.findByDateRange(startDate, endDate);

        List<QueryResponse.SearchResult> results = contexts.stream()
                .map(ctx -> QueryResponse.SearchResult.builder()
                        .id(ctx.getId())
                        .content(ctx.getTextChunk())
                        .score(1.0)
                        .type("CHUNK")
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Fetched {} contexts in date range in {}ms", results.size(), totalTime);

        return QueryResponse.builder()
                .query("range:" + startDate + "_to_" + endDate)
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Search recent contexts with vector similarity
    public QueryResponse searchRecentContexts(QueryRequest request, int days) {
        long startTime = System.currentTimeMillis();
        log.info("Searching recent contexts (last {} days) for: {}", days, request.getQuery());

        float[] embedding = LLMProvider.getEmbedding(request.getQuery());
        String vectorString = toVectorString(embedding);

        List<Context> contexts = contextRepository.findRecentSimilar(days, vectorString, request.getLimit());

        List<QueryResponse.SearchResult> results = contexts.stream()
                .map(ctx -> QueryResponse.SearchResult.builder()
                        .id(ctx.getId())
                        .content(ctx.getTextChunk())
                        .score(1.0)
                        .type("CHUNK")
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Recent context search completed in {}ms", totalTime);

        return QueryResponse.builder()
                .query(request.getQuery())
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get sibling contexts (other chunks from same document)
    public QueryResponse getSiblingContexts(UUID contextId) {
        long startTime = System.currentTimeMillis();
        log.info("Fetching sibling contexts for: {}", contextId);

        List<Context> contexts = contextRepository.findSiblingContexts(contextId);

        List<QueryResponse.SearchResult> results = contexts.stream()
                .map(ctx -> QueryResponse.SearchResult.builder()
                        .id(ctx.getId())
                        .content(ctx.getTextChunk())
                        .score(1.0)
                        .type("SIBLING_CHUNK")
                        .metadata(Map.of("chunkIndex", Objects.requireNonNullElse(ctx.getChunkIndex(), 0)))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Found {} sibling contexts in {}ms", results.size(), totalTime);

        return QueryResponse.builder()
                .query("siblings:" + contextId)
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // ==================== ENTITY OPERATIONS ====================

    // Semantic search on entities with similarity scores
    public QueryResponse searchEntities(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Searching entities for query: {}", request.getQuery());

        float[] embedding = LLMProvider.getEmbedding(request.getQuery());
        String vectorString = toVectorString(embedding);

        List<Object[]> rows = entityRepository.findSimilarEntitiesWithScore(vectorString, request.getLimit());

        List<QueryResponse.SearchResult> results = rows.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .id((UUID) row[0])
                        .content((String) row[1])
                        .score(((Number) row[4]).doubleValue())
                        .type("ENTITY")
                        .metadata(Map.of(
                                "entityType", Objects.requireNonNullElse(row[2], "UNKNOWN"),
                                "description", Objects.requireNonNullElse(row[3], "")))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Entity search completed in {}ms, found {} results", totalTime, results.size());

        return QueryResponse.builder()
                .query(request.getQuery())
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Find entity by exact name
    public Optional<RagEntity> getEntityByName(String name) {
        log.info("Looking up entity by name: {}", name);
        return entityRepository.findByName(name);
    }

    // Find entity by name (case-insensitive)
    public Optional<RagEntity> getEntityByNameIgnoreCase(String name) {
        log.info("Looking up entity by name (case-insensitive): {}", name);
        return entityRepository.findByNameIgnoreCase(name);
    }

    // Get entity ID by name
    public Optional<UUID> getEntityIdByName(String name) {
        log.info("Looking up entity ID by name: {}", name);
        return entityRepository.findIdByName(name);
    }

    // Disambiguate entity using vector similarity
    public Optional<RagEntity> disambiguateEntity(String entityName, String contextText) {
        log.info("Disambiguating entity '{}' with context", entityName);
        float[] embedding = LLMProvider.getEmbedding(contextText);
        String vectorString = toVectorString(embedding);
        return entityRepository.disambiguateEntity(entityName, vectorString);
    }

    // Get all contexts where an entity is mentioned
    public QueryResponse getContextsForEntity(UUID entityId) {
        long startTime = System.currentTimeMillis();
        log.info("Getting contexts for entity: {}", entityId);

        List<Object[]> rows = entityRepository.findContextsForEntity(entityId);

        List<QueryResponse.SearchResult> results = rows.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .id((UUID) row[0])
                        .content((String) row[1])
                        .score(1.0)
                        .type("ENTITY_CONTEXT")
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Found {} contexts for entity in {}ms", results.size(), totalTime);

        return QueryResponse.builder()
                .query("entity_contexts:" + entityId)
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get all entities mentioned in a context
    public QueryResponse getEntitiesForContext(UUID contextId) {
        long startTime = System.currentTimeMillis();
        log.info("Getting entities for context: {}", contextId);

        List<RagEntity> entities = entityRepository.findEntitiesForContext(contextId);

        List<QueryResponse.SearchResult> results = entities.stream()
                .map(entity -> QueryResponse.SearchResult.builder()
                        .id(entity.getId())
                        .content(entity.getName())
                        .score(1.0)
                        .type("CONTEXT_ENTITY")
                        .metadata(Map.of("entityType", Objects.requireNonNullElse(entity.getType(), "UNKNOWN")))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Found {} entities for context in {}ms", results.size(), totalTime);

        return QueryResponse.builder()
                .query("context_entities:" + contextId)
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Merge two entities (moves all references from source to target)
    @Transactional
    public void mergeEntities(UUID sourceEntityId, UUID targetEntityId) {
        log.info("Merging entity {} into {}", sourceEntityId, targetEntityId);
        entityRepository.mergeEntities(sourceEntityId, targetEntityId);
        log.info("Entity merge completed");
    }

    // ==================== KNOWLEDGE BASE OPERATIONS ====================

    // Semantic search on knowledge bases with similarity scores
    public QueryResponse searchHistory(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Searching history for query: {}", request.getQuery());

        float[] embedding = LLMProvider.getEmbedding(request.getQuery());
        String vectorString = toVectorString(embedding);

        List<Object[]> rows = knowledgeBaseRepository.findSimilarWithScore(vectorString, request.getLimit());

        List<QueryResponse.SearchResult> results = rows.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .id((UUID) row[0])
                        .content((String) row[1])
                        .score(((Number) row[4]).doubleValue())
                        .type("KNOWLEDGE_BASE")
                        .metadata(Map.of(
                                "uid", Objects.requireNonNullElse(row[2], ""),
                                "converser", Objects.requireNonNullElse(row[3], "")))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("History search completed in {}ms, found {} results", totalTime, results.size());

        return QueryResponse.builder()
                .query(request.getQuery())
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get all history for a specific user
    public QueryResponse getHistoryByUser(String uid) {
        long startTime = System.currentTimeMillis();
        log.info("Fetching history for user: {}", uid);

        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findByUidOrderByCreatedAtDesc(uid);

        List<QueryResponse.SearchResult> results = knowledgeBases.stream()
                .map(kb -> QueryResponse.SearchResult.builder()
                        .id(kb.getId())
                        .content(kb.getContent())
                        .score(1.0)
                        .type("KNOWLEDGE_BASE")
                        .metadata(Map.of("converser", kb.getConverser()))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Fetched {} history entries for user in {}ms", results.size(), totalTime);

        return QueryResponse.builder()
                .query("user:" + uid)
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get recent knowledge bases (last N hours)
    public QueryResponse getRecentKnowledgeBases(int hours) {
        long startTime = System.currentTimeMillis();
        log.info("Fetching knowledge bases from last {} hours", hours);

        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findRecent(hours);

        List<QueryResponse.SearchResult> results = knowledgeBases.stream()
                .map(kb -> QueryResponse.SearchResult.builder()
                        .id(kb.getId())
                        .content(kb.getContent())
                        .score(1.0)
                        .type("KNOWLEDGE_BASE")
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Fetched {} recent knowledge bases in {}ms", results.size(), totalTime);

        return QueryResponse.builder()
                .query("recent:" + hours + "hours")
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get knowledge bases since a specific timestamp
    public QueryResponse getKnowledgeBasesSince(Instant since) {
        long startTime = System.currentTimeMillis();
        log.info("Fetching knowledge bases since: {}", since);

        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findByCreatedAtAfter(since);

        List<QueryResponse.SearchResult> results = knowledgeBases.stream()
                .map(kb -> QueryResponse.SearchResult.builder()
                        .id(kb.getId())
                        .content(kb.getContent())
                        .score(1.0)
                        .type("KNOWLEDGE_BASE")
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Fetched {} knowledge bases since timestamp in {}ms", results.size(), totalTime);

        return QueryResponse.builder()
                .query("since:" + since.toString())
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Delete all data for a user (GDPR Right to be Forgotten)
    @Transactional
    public void deleteUserData(String uid) {
        log.warn("Deleting all data for user: {} (GDPR request)", uid);
        knowledgeBaseRepository.deleteByUid(uid);
        log.info("User data deletion completed for: {}", uid);
    }

    // ==================== RELATION/GRAPH OPERATIONS ====================

    // Get outgoing relations for an entity
    public QueryResponse getOutgoingConnections(UUID entityId) {
        long startTime = System.currentTimeMillis();
        log.info("Getting outgoing connections for entity: {}", entityId);

        List<Object[]> relations = relationRepository.findOutgoingRelations(entityId);

        List<QueryResponse.SearchResult> results = relations.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .content((String) row[1])
                        .score(((Number) row[2]).doubleValue())
                        .type("RELATION")
                        .metadata(Map.of("relationType", row[0]))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Outgoing connections completed in {}ms, found {} results", totalTime, results.size());

        return QueryResponse.builder()
                .query("outgoing:" + entityId)
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get incoming relations for an entity
    public QueryResponse getIncomingConnections(UUID entityId) {
        long startTime = System.currentTimeMillis();
        log.info("Getting incoming connections for entity: {}", entityId);

        List<Object[]> relations = relationRepository.findIncomingRelations(entityId);

        List<QueryResponse.SearchResult> results = relations.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .content((String) row[0])
                        .score(((Number) row[2]).doubleValue())
                        .type("RELATION")
                        .metadata(Map.of("relationType", row[1]))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Incoming connections completed in {}ms, found {} results", totalTime, results.size());

        return QueryResponse.builder()
                .query("incoming:" + entityId)
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get 2-hop connections (Friends of Friends)
    public QueryResponse getTwoHopConnections(UUID entityId) {
        long startTime = System.currentTimeMillis();
        log.info("Getting 2-hop connections for entity: {}", entityId);

        List<String> entityNames = relationRepository.findTwoHopConnections(entityId);

        List<QueryResponse.SearchResult> results = entityNames.stream()
                .map(name -> QueryResponse.SearchResult.builder()
                        .content(name)
                        .score(1.0)
                        .type("TWO_HOP_ENTITY")
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("2-hop connections completed in {}ms, found {} results", totalTime, results.size());

        return QueryResponse.builder()
                .query("2hop:" + entityId)
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get top/strongest relations in the database
    public QueryResponse getTopRelations(int limit) {
        long startTime = System.currentTimeMillis();
        log.info("Getting top {} relations", limit);

        List<Object[]> relations = relationRepository.findTopRelations(limit);

        List<QueryResponse.SearchResult> results = relations.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .content(row[0] + " -> " + row[2])
                        .score(((Number) row[3]).doubleValue())
                        .type("TOP_RELATION")
                        .metadata(Map.of("relationType", row[1]))
                        .build())
                .toList();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Top relations completed in {}ms, found {} results", totalTime, results.size());

        return QueryResponse.builder()
                .query("top_relations")
                .results(results)
                .processingTimeMs(totalTime)
                .build();
    }

    // Get all relations from a source entity (as Relation entities)
    public List<Relation> getRelationsBySource(UUID sourceId) {
        log.info("Getting relations by source: {}", sourceId);
        return relationRepository.findBySourceEntityId(sourceId);
    }

    // Get all relations to a target entity (as Relation entities)
    public List<Relation> getRelationsByTarget(UUID targetId) {
        log.info("Getting relations by target: {}", targetId);
        return relationRepository.findByTargetEntityId(targetId);
    }

    // Get all relations of a specific type
    public List<Relation> getRelationsByType(String relationType) {
        log.info("Getting relations by type: {}", relationType);
        return relationRepository.findByRelationType(relationType);
    }

    // ==================== HYBRID SEARCH ====================

    // Performs hybrid search: vector search + graph expansion
    public QueryResponse hybridSearch(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Performing hybrid search for query: {}", request.getQuery());

        float[] embedding = LLMProvider.getEmbedding(request.getQuery());
        String vectorString = toVectorString(embedding);

        List<QueryResponse.SearchResult> allResults = new ArrayList<>();

        // Step 1: Vector search on contexts (with scores)
        List<Object[]> contextRows = contextRepository.findSimilarWithScore(vectorString, request.getLimit());
        for (Object[] row : contextRows) {
            UUID contextId = (UUID) row[0];
            double score = ((Number) row[3]).doubleValue();

            allResults.add(QueryResponse.SearchResult.builder()
                    .id(contextId)
                    .content((String) row[1])
                    .score(score)
                    .type("CHUNK")
                    .build());

            // Step 2: Find entities in this context
            List<RagEntity> entities = entityRepository.findEntitiesForContext(contextId);
            for (RagEntity entity : entities) {
                allResults.add(QueryResponse.SearchResult.builder()
                        .id(entity.getId())
                        .content(entity.getName())
                        .score(score * 0.8)
                        .type("LINKED_ENTITY")
                        .metadata(Map.of("entityType", Objects.requireNonNullElse(entity.getType(), "UNKNOWN")))
                        .build());
            }
        }

        // Step 3: Vector search on entities directly (with scores)
        List<Object[]> entityRows = entityRepository.findSimilarEntitiesWithScore(vectorString, request.getLimit());
        for (Object[] row : entityRows) {
            allResults.add(QueryResponse.SearchResult.builder()
                    .id((UUID) row[0])
                    .content((String) row[1])
                    .score(((Number) row[4]).doubleValue())
                    .type("SIMILAR_ENTITY")
                    .build());
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Hybrid search completed in {}ms, found {} results", totalTime, allResults.size());

        return QueryResponse.builder()
                .query(request.getQuery())
                .results(allResults)
                .processingTimeMs(totalTime)
                .build();
    }

    // ==================== UTILITY METHODS ====================

    // Converts float array to PostgreSQL vector string format
    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
