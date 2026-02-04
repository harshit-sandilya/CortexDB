package com.vectornode.memory.query.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.query.dto.QueryRequest;
import com.vectornode.memory.query.dto.QueryResponse;
import com.vectornode.memory.query.repository.ContextRepository;
import com.vectornode.memory.query.repository.EntityRepository;
import com.vectornode.memory.query.repository.KnowledgeBaseRepository;
import com.vectornode.memory.query.repository.RelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryService {

    private final ContextRepository contextRepository;
    private final EntityRepository entityRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RelationRepository relationRepository;

    // ==================== CONTEXT SEARCH (Semantic Search on Chunks)
    // ====================

    /**
     * Performs semantic search on contexts using vector similarity.
     */
    public QueryResponse searchContexts(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Searching contexts for query: {}", request.getQuery());

        float[] embedding = LLMProvider.getEmbedding(request.getQuery());
        String vectorString = toVectorString(embedding);

        List<Context> contexts = contextRepository.findSimilar(vectorString, request.getLimit());

        List<QueryResponse.SearchResult> results = contexts.stream()
                .map(ctx -> QueryResponse.SearchResult.builder()
                        .id(ctx.getId())
                        .content(ctx.getTextChunk())
                        .score(1.0) // TODO: Get actual score from query
                        .type("CHUNK")
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

    // ==================== ENTITY SEARCH (Semantic Search on Entities)
    // ====================

    /**
     * Performs semantic search on entities using vector similarity.
     */
    public QueryResponse searchEntities(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Searching entities for query: {}", request.getQuery());

        float[] embedding = LLMProvider.getEmbedding(request.getQuery());
        String vectorString = toVectorString(embedding);

        List<RagEntity> entities = entityRepository.findSimilarEntities(vectorString, request.getLimit());

        List<QueryResponse.SearchResult> results = entities.stream()
                .map(entity -> QueryResponse.SearchResult.builder()
                        .id(entity.getId())
                        .content(entity.getName())
                        .score(1.0)
                        .type("ENTITY")
                        .metadata(Map.of(
                                "entityType", entity.getType() != null ? entity.getType() : "UNKNOWN",
                                "description", entity.getDescription() != null ? entity.getDescription() : ""))
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

    // ==================== KNOWLEDGE BASE SEARCH (Search Past Conversations)
    // ====================

    /**
     * Searches past knowledge bases/conversations for similar content.
     */
    public QueryResponse searchHistory(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Searching history for query: {}", request.getQuery());

        float[] embedding = LLMProvider.getEmbedding(request.getQuery());
        String vectorString = toVectorString(embedding);

        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findSimilar(vectorString, request.getLimit());

        List<QueryResponse.SearchResult> results = knowledgeBases.stream()
                .map(kb -> QueryResponse.SearchResult.builder()
                        .id(kb.getId())
                        .content(kb.getContent())
                        .score(1.0)
                        .type("KNOWLEDGE_BASE")
                        .metadata(Map.of(
                                "uid", kb.getUid(),
                                "converser", kb.getConverser()))
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

    // ==================== GRAPH TRAVERSAL (Entity Connections)
    // ====================

    /**
     * Gets outgoing relations for an entity (what it connects to).
     */
    public QueryResponse getOutgoingConnections(UUID entityId) {
        long startTime = System.currentTimeMillis();
        log.info("Getting outgoing connections for entity: {}", entityId);

        List<Object[]> relations = relationRepository.findOutgoingRelations(entityId);

        List<QueryResponse.SearchResult> results = relations.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .content((String) row[1]) // target entity name
                        .score(((Number) row[2]).doubleValue()) // edge weight as score
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

    /**
     * Gets incoming relations for an entity (what connects to it).
     */
    public QueryResponse getIncomingConnections(UUID entityId) {
        long startTime = System.currentTimeMillis();
        log.info("Getting incoming connections for entity: {}", entityId);

        List<Object[]> relations = relationRepository.findIncomingRelations(entityId);

        List<QueryResponse.SearchResult> results = relations.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .content((String) row[0]) // source entity name
                        .score(((Number) row[2]).doubleValue()) // edge weight as score
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

    /**
     * Finds 2-hop connections (Friends of Friends) for an entity.
     */
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

    /**
     * Gets the top/strongest relations in the database.
     */
    public QueryResponse getTopRelations(int limit) {
        long startTime = System.currentTimeMillis();
        log.info("Getting top {} relations", limit);

        List<Object[]> relations = relationRepository.findTopRelations(limit);

        List<QueryResponse.SearchResult> results = relations.stream()
                .map(row -> QueryResponse.SearchResult.builder()
                        .content(row[0] + " -> " + row[2]) // source -> target
                        .score(((Number) row[3]).doubleValue()) // edge weight
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

    // ==================== HYBRID SEARCH (Vector + Graph Combined)
    // ====================

    /**
     * Performs hybrid search: vector search + graph expansion.
     * 1. Find relevant contexts via vector search
     * 2. Find entities mentioned in those contexts
     * 3. Expand to related entities via graph traversal
     */
    public QueryResponse hybridSearch(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Performing hybrid search for query: {}", request.getQuery());

        float[] embedding = LLMProvider.getEmbedding(request.getQuery());
        String vectorString = toVectorString(embedding);

        List<QueryResponse.SearchResult> allResults = new ArrayList<>();

        // Step 1: Vector search on contexts
        List<Context> contexts = contextRepository.findSimilar(vectorString, request.getLimit());
        for (Context ctx : contexts) {
            allResults.add(QueryResponse.SearchResult.builder()
                    .id(ctx.getId())
                    .content(ctx.getTextChunk())
                    .score(1.0)
                    .type("CHUNK")
                    .build());

            // Step 2: Find entities in this context
            List<RagEntity> entities = entityRepository.findEntitiesForContext(ctx.getId());
            for (RagEntity entity : entities) {
                allResults.add(QueryResponse.SearchResult.builder()
                        .id(entity.getId())
                        .content(entity.getName())
                        .score(0.8) // Slightly lower score for graph-expanded results
                        .type("LINKED_ENTITY")
                        .metadata(Map.of("entityType", entity.getType() != null ? entity.getType() : "UNKNOWN"))
                        .build());
            }
        }

        // Step 3: Vector search on entities directly
        List<RagEntity> similarEntities = entityRepository.findSimilarEntities(vectorString, request.getLimit());
        for (RagEntity entity : similarEntities) {
            allResults.add(QueryResponse.SearchResult.builder()
                    .id(entity.getId())
                    .content(entity.getName())
                    .score(0.9)
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

    /**
     * Converts float array to PostgreSQL vector string format.
     */
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
