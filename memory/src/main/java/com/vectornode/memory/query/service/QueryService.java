package com.vectornode.memory.query.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.entity.Relation;
import com.vectornode.memory.query.dto.request.QueryRequest;
import com.vectornode.memory.query.dto.response.QueryResponse;
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
                                                .metadata(Map.of("chunkIndex",
                                                                Objects.requireNonNullElse(ctx.getChunkIndex(), 0)))
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
                // log.info("Searching recent contexts (last {} days) for: {}", days,
                // request.getQuery());

                float[] embedding = LLMProvider.getEmbedding(request.getQuery());
                String vectorString = toVectorString(embedding);

                List<Object[]> rows = contextRepository.findRecentSimilarWithScore(days, vectorString,
                                request.getLimit());

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
                                                .metadata(Map.of("chunkIndex",
                                                                Objects.requireNonNullElse(ctx.getChunkIndex(), 0)))
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
                                .map(row -> {
                                    // Handle UUID/String casting for entity ID (PostgreSQL native query issue)
                                    Object idObj = row[0];
                                    UUID entityId;
                                    try {
                                        if (idObj instanceof UUID) {
                                            entityId = (UUID) idObj;
                                        } else if (idObj instanceof String) {
                                            entityId = UUID.fromString((String) idObj);
                                        } else {
                                            log.warn("Unexpected entity ID type: {}, skipping", idObj.getClass().getName());
                                            return null;
                                        }
                                    } catch (Exception e) {
                                        log.error("Failed to parse entity ID: {}", idObj, e);
                                        return null;
                                    }
                                    return QueryResponse.SearchResult.builder()
                                                    .id(entityId)
                                                    .content((String) row[1])
                                                    .score(((Number) row[4]).doubleValue())
                                                    .type("ENTITY")
                                                    .metadata(Map.of(
                                                                    "entityType",
                                                                    Objects.requireNonNullElse(row[2], "UNKNOWN"),
                                                                    "description", Objects.requireNonNullElse(row[3], "")))
                                                    .build();
                                })
                                .filter(Objects::nonNull)
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
                                                .score(0.0) // Score not applicable for direct fetch
                                                .type("CONTEXT_ENTITY")
                                                .metadata(Map.of("entityType",
                                                                Objects.requireNonNullElse(entity.getType(),
                                                                                "UNKNOWN")))
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
                                                .score(0.0) // Score not applicable for direct fetch
                                                .type("KNOWLEDGE_BASE")
                                                .metadata(Map.of("converser",
                                                                kb.getConverser() != null ? kb.getConverser().name()
                                                                                : ""))
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
                                                .metadata(Map.of("relationType",
                                                                Objects.requireNonNullElse(row[0], "UNKNOWN")))
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
                                                .metadata(Map.of("relationType",
                                                                Objects.requireNonNullElse(row[1], "UNKNOWN")))
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
                                                .score(0.5)
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
                                                .metadata(Map.of("relationType",
                                                                Objects.requireNonNullElse(row[1], "UNKNOWN")))
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
                                                .metadata(Map.of("entityType",
                                                                Objects.requireNonNullElse(entity.getType(),
                                                                                "UNKNOWN")))
                                                .build());
                        }
                }

                // Step 3: Vector search on entities directly (with scores)
                List<Object[]> entityRows = entityRepository.findSimilarEntitiesWithScore(vectorString,
                                request.getLimit());
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

        // ==================== AGENTIC ROUTER (DUAL-PIPELINE) ====================

        /**
         * Entry point for the Dual-Pipeline Query.
         * 1. Classifies intent using LLM (PROMPT vs DOCUMENT)
         * 2. Routes to appropriate search execution
         * 3. Generates a final answer
         */
        public QueryResponse routeQuery(QueryRequest request) {
                long startTime = System.currentTimeMillis();
                log.info("Agentic Router processing query: {}", request.getQuery());

                // 1. Classification
                String classificationPrompt = """
                                Analyze this query and classify the user's intent into exactly one of these two categories:

                                PROMPT: Simple text-based queries that should use direct semantic search on text chunks.
                                       Use for: factual questions, general knowledge, short answers, when no document structure is needed.
                                       Examples: "What is Kubernetes?", "Which company created Java?", "How does React work?"
                                DOCUMENT: Queries that require hierarchical document traversal (PageIndex).
                                         Use ONLY for: ingested documents (PDFs, manuals) with clear section structure.
                                         Examples: "Explain section 3.2 of the Kubernetes manual", "Navigate to the API reference in the docs"

                                Output ONLY the word PROMPT or DOCUMENT.
                                Default to PROMPT if unsure - most queries should use simple semantic search.

                                Query: "%s"
                                """
                                .formatted(request.getQuery());

                String classification = "DOCUMENT";
                try {
                        String llmResponse = LLMProvider.callLLM(classificationPrompt);
                        if (llmResponse != null) {
                                classification = llmResponse.trim().toUpperCase();
                        }
                        log.info("Agentic Router classified query as: {}", classification);
                } catch (Exception e) {
                        log.warn("LLM error during classification (rate limit?), defaulting to DOCUMENT: {}", e.getMessage());
                }

                List<QueryResponse.SearchResult> retrievedContext;

                // 2. Routed Execution
                // Note: Most queries should use PROMPT (simple semantic search)
                // DOCUMENT (PageIndex) is only for hierarchical document traversal
                if ("PROMPT".equals(classification)) {
                    log.info("Using SimpleMEM search (direct semantic search on text chunks)");
                    retrievedContext = executePromptSearch(request);
                } else {
                    log.info("Using PageIndex traversal (for hierarchical documents)");
                    retrievedContext = executeDocumentSearch(request);
                }

                // 3. Final Generation (Optional for Benchmark)
                String finalAnswer = null;
                if (request.isGenerateAnswer()) {
                        // Build structured context with separate sections for chunks,
                        // entity metadata, and relation metadata
                        StringBuilder contextChunks = new StringBuilder();
                        StringBuilder entityKnowledge = new StringBuilder();
                        StringBuilder relationKnowledge = new StringBuilder();

                        for (QueryResponse.SearchResult result : retrievedContext) {
                            switch (result.getType()) {
                                case "CONTEXT", "CHUNK" -> contextChunks
                                        .append("- ").append(result.getContent()).append("\n");

                                case "ENTITY_METADATA", "LINKED_ENTITY", "SIMILAR_ENTITY" -> {
                                    Map<String, Object> meta = result.getMetadata() != null ? result.getMetadata() : Map.of();
                                    entityKnowledge
                                        .append("- Entity: ").append(result.getContent())
                                        .append(" | Metadata: ").append(meta.getOrDefault("dbMetadata", "{}"))
                                        .append("\n");
                                }

                                case "RELATION_METADATA", "RELATION_2HOP" -> {
                                    Map<String, Object> meta = result.getMetadata() != null ? result.getMetadata() : Map.of();
                                    relationKnowledge
                                        .append("- ").append(result.getContent())
                                        .append(" | Metadata: ").append(meta.getOrDefault("dbMetadata", "{}"))
                                        .append("\n");
                                }

                                default -> contextChunks
                                        .append("- ").append(result.getContent()).append("\n");
                            }
                        }

                        // Compose structured prompt with all three knowledge sources
                        StringBuilder fullContext = new StringBuilder();

                        if (!contextChunks.isEmpty()) {
                            fullContext.append("Retrieved Contexts:\n").append(contextChunks).append("\n");
                        }
                        if (!entityKnowledge.isEmpty()) {
                            fullContext.append("Entity Knowledge:\n").append(entityKnowledge).append("\n");
                        }
                        if (!relationKnowledge.isEmpty()) {
                            fullContext.append("Entity Relationships:\n").append(relationKnowledge).append("\n");
                        }

                        String finalPrompt = """
                                        Answer the user's question using ONLY the provided context. \
                                        Use the entity knowledge and relationships to enrich your answer where relevant. \
                                        If the context does not contain the answer, say "I don't know based on my memory."

                                        %s

                                        Question: "%s"
                                        """
                                        .formatted(fullContext.toString(), request.getQuery());

                        try {
                                finalAnswer = LLMProvider.callLLM(finalPrompt);
                        } catch (Exception e) {
                                log.warn("LLM failed to generate final answer: {}", e.getMessage());
                                finalAnswer = "I'm sorry, I could not generate an answer at this time due to an AI error.";
                        }
                }

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("Agentic Router completed in {}ms", totalTime);

                // Return a custom metadata block containing the final answer and routing debug
                // info
                return QueryResponse.builder()
                                .query(request.getQuery())
                                .results(retrievedContext)
                                .finalAnswer(finalAnswer)
                                .processingTimeMs(totalTime)
                                .build();
        }

        /**
         * SimpleMem Hybrid Search with LLM-based Entity Extraction and Graph Traversal.
         *
         * Flow:
         * 1. Vector search on contexts via contextRepository.findSimilarWithScore()
         * 2. Extract entity names from the query text using an LLM call
         * 3. Look up each extracted entity in the entities table
         * 4. If found, return the entity metadata (type, description) from the entities table
         * 5. 1-hop graph traversal: find entities directly connected to the current entity
         *    and return the relation metadata (relationType, edgeWeight) from the relations table
         *
         * Returns three categories of results:
         *   - CONTEXT chunks (from vector search)
         *   - ENTITY_METADATA (type + description for each recognized entity)
         *   - RELATION_METADATA (relation details for each 1-hop connected entity)
         */
        private List<QueryResponse.SearchResult> executePromptSearch(QueryRequest request) {
                log.info("Executing SimpleMEM Hybrid Search with LLM Entity Extraction & Graph Traversal");
                List<QueryResponse.SearchResult> results = new ArrayList<>();

                try {
                    // ── Step 1: Hybrid Vector Search (Chunks + Vector Entities) ────────
                    // We increase the limit inside the hybrid request to gather deeper graph context
                    QueryRequest hybridReq = new QueryRequest();
                    hybridReq.setQuery(request.getQuery());
                    hybridReq.setLimit(request.getLimit() * 2);
                    
                    QueryResponse hybridResponse = hybridSearch(hybridReq);
                    results.addAll(hybridResponse.getResults());

                    log.info("Hybrid search injected {} base context elements", hybridResponse.getResults().size());

                    // ── Step 2: Extract entities from the query via LLM ───────────────
                    String entityExtractionPrompt = """
                            Extract all named entities (people, organizations, technologies, concepts, \
                            places, products, events, etc.) from the following query.

                            Return ONLY a comma-separated list of entity names, nothing else.
                            If there are no entities, return "NONE".

                            Query: "%s"
                            """.formatted(request.getQuery());

                    String llmResponse = LLMProvider.callLLM(entityExtractionPrompt);
                    List<String> extractedEntityNames = parseExtractedEntityNames(llmResponse);

                    log.info("LLM extracted {} entities from query: {}", extractedEntityNames.size(), extractedEntityNames);

                    // ── Step 3 & 4: Look up each entity and retrieve metadata ─────────
                    for (String entityName : extractedEntityNames) {
                        Optional<RagEntity> entityOpt = entityRepository.findByNameIgnoreCase(entityName.trim());

                        if (entityOpt.isEmpty()) {
                            log.debug("Entity '{}' not found in database, skipping", entityName);
                            continue;
                        }

                        RagEntity entity = entityOpt.get();
                        log.info("Found entity '{}' (id={}, type={})", entity.getName(), entity.getId(), entity.getType());

                        // Add the actual metadata JSONB from the entities table
                        String entityMetadataStr = entity.getMetadata() != null
                                ? entity.getMetadata().toString() : "{}";

                        results.add(QueryResponse.SearchResult.builder()
                                .id(entity.getId())
                                .content(entity.getName())
                                .score(1.0) // Exact match from LLM extraction
                                .type("ENTITY_METADATA")
                                .metadata(Map.of("dbMetadata", entityMetadataStr))
                                .build());

                        // ── Step 5: 1-hop graph traversal ─────────────────────────────
                        // Find outgoing relations (current entity → connected entity)
                        List<Object[]> outgoingRelations = relationRepository.findOutgoingRelations(entity.getId());
                        for (Object[] rel : outgoingRelations) {
                            String relationType = (String) rel[0];
                            String targetEntityName = (String) rel[1];
                            double edgeWeight = ((Number) rel[2]).doubleValue();
                            String relationMetadataStr = rel[3] != null ? rel[3].toString() : "{}";

                            results.add(QueryResponse.SearchResult.builder()
                                    .content(entity.getName() + " -> " + targetEntityName)
                                    .score(edgeWeight)
                                    .type("RELATION_METADATA")
                                    .metadata(Map.of("dbMetadata", relationMetadataStr))
                                    .build());
                        }

                        // Find incoming relations (connected entity → current entity)
                        List<Object[]> incomingRelations = relationRepository.findIncomingRelations(entity.getId());
                        for (Object[] rel : incomingRelations) {
                            String sourceEntityName = (String) rel[0];
                            String relationType = (String) rel[1];
                            double edgeWeight = ((Number) rel[2]).doubleValue();
                            String relationMetadataStr = rel[3] != null ? rel[3].toString() : "{}";

                            results.add(QueryResponse.SearchResult.builder()
                                    .content(sourceEntityName + " -> " + entity.getName())
                                    .score(edgeWeight)
                                    .type("RELATION_METADATA")
                                    .metadata(Map.of("dbMetadata", relationMetadataStr))
                                    .build());
                        }

                        // ── Step 5.5: 2-hop graph traversal ─────────────────────────────
                        List<String> twoHopConnections = relationRepository.findTwoHopConnections(entity.getId());
                        for (String targetEntityName : twoHopConnections) {
                            results.add(QueryResponse.SearchResult.builder()
                                    .content(entity.getName() + " -> [Intermediate] -> " + targetEntityName)
                                    .score(0.5) // Lower weight for 2-hop
                                    .type("RELATION_2HOP")
                                    .metadata(Map.of("hopCount", 2))
                                    .build());
                        }

                        log.info("Graph traversal for '{}': {} outgoing, {} incoming, {} 2-hop relations",
                                entity.getName(), outgoingRelations.size(), incomingRelations.size(), twoHopConnections.size());
                    }

                    log.info("GraphRAG Hybrid Search complete — {} total results", results.size());

                } catch (Exception e) {
                    log.warn("Error during prompt search, falling back to partial results: {}", e.getMessage());
                }

                return results;
        }

        /**
         * Parses the LLM response from entity extraction into a list of entity names.
         * Expects a comma-separated list or "NONE".
         */
        private List<String> parseExtractedEntityNames(String llmResponse) {
                if (llmResponse == null || llmResponse.isBlank() || llmResponse.trim().equalsIgnoreCase("NONE")) {
                    return List.of();
                }

                List<String> names = new ArrayList<>();
                for (String name : llmResponse.split(",")) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        names.add(trimmed);
                    }
                }
                return names;
        }

        /**
         * PageIndex Agentic Traversal
         */
        private List<QueryResponse.SearchResult> executeDocumentSearch(QueryRequest request) {
                log.info("Executing PageIndex Traversal");

                // Find root nodes (sections with depth 0, or nodes that have no incoming
                // HAS_SUBSECTION relations)
                // Simplification for the backend: we perform a vector search to find the most
                // relevant DOCUMENT_SECTION entity,
                // then traverse its children.

                List<QueryResponse.SearchResult> results = new ArrayList<>();

                try {
                    float[] embedding = LLMProvider.getEmbedding(request.getQuery());
                    String vectorString = toVectorString(embedding);

                    // Vector search to find the closest entry point
                    List<Object[]> entityRows = entityRepository.findSimilarEntitiesWithScore(vectorString, 5);

                    if (entityRows.isEmpty())
                        return results;

                    // Traverse down from the best matching entity
                    // Handle both UUID and String representations (PostgreSQL native query returns String)
                    Object idObj = entityRows.get(0)[0];
                    log.info("DEBUG: entityRows.get(0)[0] type: {}, value: {}", idObj.getClass().getName(), idObj);
                    UUID bestEntityId;
                    if (idObj instanceof UUID) {
                        bestEntityId = (UUID) idObj;
                    } else if (idObj instanceof String) {
                        bestEntityId = UUID.fromString((String) idObj);
                    } else {
                        log.warn("Unexpected entity ID type: {}, trying to cast anyway", idObj.getClass().getName());
                        // Try casting to String and parsing
                        try {
                            bestEntityId = UUID.fromString(idObj.toString());
                        } catch (Exception e) {
                            log.error("Failed to parse entity ID: {}", idObj, e);
                            throw new IllegalStateException("Cannot parse entity ID from: " + idObj, e);
                        }
                    }
                    traversePageIndex(bestEntityId, request.getQuery(), results, 0, 3); // Max depth 3
                } catch (ClassCastException e) {
                    log.warn("ClassCastException during document search (UUID casting issue): {}", e.getMessage());
                    // Fallback to empty results if casting fails
                } catch (Exception e) {
                    log.warn("LLM failed during document search (rate limit/embedding error?), falling back to empty results: {}", e.getMessage());
                    // Fallback to empty results if LLM fails
                }

                // If document search returned no results, fallback to simple semantic search
                if (results.isEmpty()) {
                    log.info("Document search returned no results, falling back to SimpleMEM search");
                    return executePromptSearch(request);
                }

                return results;
        }

        // Recursive tree traversal using LLM to pick branches
        private void traversePageIndex(UUID currentEntityId, String originalQuery,
                        List<QueryResponse.SearchResult> accumulation, int currentDepth, int maxDepth) {
                if (currentDepth >= maxDepth)
                        return;

                // 1. Add current node's context to accumulation
                List<Object[]> contexts = entityRepository.findContextsForEntity(currentEntityId);
                for (Object[] row : contexts) {
                        // Handle UUID/String casting for context ID (PostgreSQL native query returns UUIDs)
                        Object contextIdObj = row[0];
                        UUID contextId;
                        if (contextIdObj instanceof UUID) {
                            contextId = (UUID) contextIdObj;
                        } else if (contextIdObj instanceof String) {
                            contextId = UUID.fromString((String) contextIdObj);
                        } else {
                            log.warn("Unexpected context ID type: {}, skipping", contextIdObj.getClass().getName());
                            continue; // Skip this context if we can't parse the ID
                        }
                        // Handle potential UUID casting for content as well (native query may return UUIDs)
                        Object contentObj = row[1];
                        String content;
                        if (contentObj instanceof String) {
                            content = (String) contentObj;
                        } else if (contentObj instanceof UUID) {
                            content = contentObj.toString(); // Convert UUID to String
                        } else {
                            log.warn("Unexpected content type: {}, converting to string", contentObj.getClass().getName());
                            content = contentObj != null ? contentObj.toString() : "null";
                        }
                        accumulation.add(QueryResponse.SearchResult.builder()
                                        .id(contextId)
                                        .content(content)
                                        .score(0.0) // Score not applicable for direct tree traversal node fetch
                                        .type("DOCUMENT_NODE")
                                        .build());
                }

                // 2. Fetch children via HAS_SUBSECTION relations
                List<Object[]> outgoing = relationRepository.findOutgoingRelations(currentEntityId);
                List<String> options = new ArrayList<>();

                for (Object[] rel : outgoing) {
                        if ("HAS_SUBSECTION".equals(rel[0])) {
                                options.add((String) rel[1]); // target entity name
                        }
                }

                if (options.isEmpty())
                        return; // Leaf node

                // 3. Ask LLM which branch to take
                StringBuilder optionsStr = new StringBuilder();
                for (int i = 0; i < options.size(); i++) {
                        optionsStr.append(i).append(": ").append(options.get(i)).append("\n");
                }

                String prompt = """
                                Which of the following document sections is most likely to contain the answer to the user's query?

                                Options:
                                %s

                                Query: "%s"

                                Output ONLY the integer index of the best option.
                                """
                                .formatted(optionsStr.toString(), originalQuery);

                try {
                        int selectedIndex = Integer.parseInt(LLMProvider.callLLM(prompt).trim());
                        if (selectedIndex >= 0 && selectedIndex < options.size()) {
                                String selectedBranchName = options.get(selectedIndex);
                                Optional<UUID> nextEntityId = entityRepository.findIdByName(selectedBranchName);
                                if (nextEntityId.isPresent()) {
                                        traversePageIndex(nextEntityId.get(), originalQuery, accumulation,
                                                        currentDepth + 1, maxDepth);
                                }
                        }
                } catch (Exception e) {
                        log.warn("LLM failed or rate limited during branch traversal: {}", e.getMessage());
                }
        }
}