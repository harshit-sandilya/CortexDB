package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.entity.Relation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates the RAG ingestion pipeline.
 * Handles chunking, embedding generation, and entity/relation extraction.
 * Persists contexts, entities, and relations to database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionWorker {

        private final ChunkingService chunkingService;
        private final ExtractionService extractionService;
        private final ObjectMapper objectMapper;

        @PersistenceContext
        private EntityManager entityManager;

        /**
         * Processes content from a knowledge base entry.
         * Chunks text, generates embeddings, and persists contexts.
         */
        @Async
        @Transactional
        public void processKnowledgeBase(UUID kbId, String content) {
                log.info("Processing KB_CREATED for id: {}", kbId);
                long startTime = System.currentTimeMillis();

                if (content == null || content.isBlank()) {
                        log.warn("Empty content for KB: {}", kbId);
                        return;
                }

                // Get KnowledgeBase reference
                KnowledgeBase kb = entityManager.getReference(KnowledgeBase.class, kbId);

                // 1. Chunk the content
                long chunkingStart = System.currentTimeMillis();
                List<String> chunks = chunkingService.chunkText(content, ChunkingService.DEFAULT_CHUNK_SIZE,
                                ChunkingService.DEFAULT_OVERLAP);
                long chunkingTime = System.currentTimeMillis() - chunkingStart;
                log.info("Chunked KB {}: {} chunks in {}ms", kbId, chunks.size(), chunkingTime);

                // 2. Generate embeddings and persist each chunk as Context
                long embeddingStart = System.currentTimeMillis();
                for (int i = 0; i < chunks.size(); i++) {
                        String chunk = chunks.get(i);
                        float[] embedding = LLMProvider.getEmbedding(chunk);

                        Context context = Context.builder()
                                        .knowledgeBase(kb)
                                        .textChunk(chunk)
                                        .vectorEmbedding(embedding)
                                        .chunkIndex(i)
                                        .build();

                        // Add metadata
                        context.setMetadata(objectMapper.createObjectNode()
                                        .put("chunkLength", chunk.length())
                                        .put("embeddingDimensions", embedding.length)
                                        .put("chunkNumber", i + 1)
                                        .put("totalChunks", chunks.size()));

                        entityManager.persist(context);

                        // Log the complete persisted row
                        log.info("CONTEXT_ROW | id={} | kb_id={} | chunk_index={} | text_length={} | vector_dims={} | metadata={} | created_at={}",
                                        context.getId(),
                                        context.getKnowledgeBase().getId(),
                                        context.getChunkIndex(),
                                        context.getTextChunk().length(),
                                        context.getVectorEmbedding().length,
                                        context.getMetadata(),
                                        context.getCreatedAt());
                }
                entityManager.flush();
                long embeddingTime = System.currentTimeMillis() - embeddingStart;

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("KB {} processing complete: {} chunks persisted, chunkingTime={}ms, embeddingTime={}ms, totalTime={}ms",
                                kbId, chunks.size(), chunkingTime, embeddingTime, totalTime);
        }

        /**
         * Processes a context chunk for entity/relation extraction.
         * Extracts and persists entities and relations.
         */
        @Async
        @Transactional
        public void processContext(UUID contextId, UUID kbId, String textChunk) {
                log.info("Processing CONTEXT_CREATED for id: {}, kbId: {}", contextId, kbId);
                long startTime = System.currentTimeMillis();

                if (textChunk == null || textChunk.isBlank()) {
                        log.warn("Empty text chunk for context: {}", contextId);
                        return;
                }

                // Get Context reference
                Context context = entityManager.getReference(Context.class, contextId);

                // 1. Extract entities & relations via LLM
                ExtractionService.ExtractionResult result = extractionService.extractFromText(textChunk);

                // 2. Persist extracted entities
                Map<String, RagEntity> entityMap = new HashMap<>();
                for (ExtractionService.ExtractedEntity extractedEntity : result.getEntities()) {
                        float[] embedding = LLMProvider.getEmbedding(
                                        extractedEntity.getName() + " " + extractedEntity.getDescription());

                        // Check if entity already exists by name
                        List<RagEntity> existing = entityManager
                                        .createQuery("SELECT e FROM RagEntity e WHERE e.name = :name", RagEntity.class)
                                        .setParameter("name", extractedEntity.getName())
                                        .setMaxResults(1)
                                        .getResultList();

                        RagEntity entity;
                        if (!existing.isEmpty()) {
                                entity = existing.get(0);
                                log.info("ENTITY_EXISTS | id={} | name={}", entity.getId(), entity.getName());
                        } else {
                                entity = RagEntity.builder()
                                                .name(extractedEntity.getName())
                                                .type(extractedEntity.getType())
                                                .description(extractedEntity.getDescription())
                                                .vectorEmbedding(embedding)
                                                .build();

                                // Add metadata
                                entity.setMetadata(objectMapper.createObjectNode()
                                                .put("extractedFrom", "context")
                                                .put("contextId", contextId.toString())
                                                .put("embeddingDimensions", embedding.length)
                                                .put("descriptionLength",
                                                                extractedEntity.getDescription() != null
                                                                                ? extractedEntity.getDescription()
                                                                                                .length()
                                                                                : 0));

                                entityManager.persist(entity);

                                // Log the complete persisted row
                                log.info("ENTITY_ROW | id={} | name={} | type={} | description_length={} | vector_dims={} | metadata={} | created_at={}",
                                                entity.getId(),
                                                entity.getName(),
                                                entity.getType(),
                                                entity.getDescription() != null ? entity.getDescription().length() : 0,
                                                entity.getVectorEmbedding().length,
                                                entity.getMetadata(),
                                                entity.getCreatedAt());
                        }

                        // Link entity to context (persists to entity_context_junction table)
                        entity.getContexts().add(context);
                        entityManager.merge(entity);

                        log.info("JUNCTION_ROW | entity_id={} | context_id={}", entity.getId(), context.getId());
                        entityMap.put(entity.getName(), entity);
                }

                // 3. Persist extracted relations (with upsert logic for edge weight)
                for (ExtractionService.ExtractedRelation extractedRelation : result.getRelations()) {
                        RagEntity sourceEntity = entityMap.get(extractedRelation.getSourceName());
                        RagEntity targetEntity = entityMap.get(extractedRelation.getTargetName());

                        if (sourceEntity != null && targetEntity != null) {
                                // Check if relation already exists
                                List<Relation> existingRelations = entityManager.createQuery(
                                                "SELECT r FROM Relation r WHERE r.sourceEntity.id = :sourceId " +
                                                                "AND r.targetEntity.id = :targetId " +
                                                                "AND r.relationType = :relationType",
                                                Relation.class)
                                                .setParameter("sourceId", sourceEntity.getId())
                                                .setParameter("targetId", targetEntity.getId())
                                                .setParameter("relationType", extractedRelation.getRelationType())
                                                .getResultList();

                                Relation relation;
                                boolean isNew = existingRelations.isEmpty();

                                if (isNew) {
                                        // Create new relation with edge_weight = 1
                                        relation = Relation.builder()
                                                        .sourceEntity(sourceEntity)
                                                        .targetEntity(targetEntity)
                                                        .relationType(extractedRelation.getRelationType())
                                                        .edgeWeight(1)
                                                        .build();

                                        relation.setMetadata(objectMapper.createObjectNode()
                                                        .put("extractedFrom", "context")
                                                        .put("contextId", contextId.toString())
                                                        .put("edgeWeight", 1));

                                        entityManager.persist(relation);

                                        log.info("RELATION_NEW | id={} | source={} | target={} | type={} | edge_weight=1",
                                                        relation.getId(),
                                                        sourceEntity.getName(),
                                                        targetEntity.getName(),
                                                        relation.getRelationType());
                                } else {
                                        // Increment edge_weight on existing relation
                                        relation = existingRelations.get(0);
                                        int newWeight = relation.getEdgeWeight() + 1;
                                        relation.setEdgeWeight(newWeight);
                                        entityManager.merge(relation);

                                        log.info("RELATION_INCREMENT | id={} | source={} | target={} | type={} | edge_weight={}",
                                                        relation.getId(),
                                                        sourceEntity.getName(),
                                                        targetEntity.getName(),
                                                        relation.getRelationType(),
                                                        newWeight);
                                }
                        } else {
                                log.warn("RELATION_SKIPPED | relation_type={} | reason=source_or_target_not_found",
                                                extractedRelation.getRelationType());
                        }
                }

                entityManager.flush();

                // ExtractionService.ExtractedMetadata metadata = result.getMetadata();
                // log.info("Ingestion completed");
        }
}
