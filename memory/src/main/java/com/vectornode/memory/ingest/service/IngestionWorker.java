package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.ingest.dto.response.*;
import com.vectornode.memory.ingest.repository.ContextRepository;
import com.vectornode.memory.ingest.repository.EntityRepository;
import com.vectornode.memory.ingest.repository.KnowledgeBaseRepository;
import com.vectornode.memory.ingest.repository.RelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates the async RAG ingestion pipeline.
 * Called by PostgresNotificationListener when events arrive.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionWorker {

        private final KnowledgeBaseRepository knowledgeBaseRepository;
        private final ContextRepository contextRepository;
        private final EntityRepository entityRepository;
        private final RelationRepository relationRepository;
        private final ChunkingService chunkingService;
        private final ExtractionService extractionService;
        private final ObjectMapper objectMapper;

        /**
         * Handles KB_CREATED event.
         * 1. Fetches KB content.
         * 2. Chunks text.
         * 3. Generates embeddings.
         * 4. Saves Context entities (triggers next stage).
         * 
         * @return KnowledgeBaseProcessingResult with details of all stored contexts
         */
        @Async
        @Transactional
        public CompletableFuture<KnowledgeBaseProcessingResult> handleKbCreated(Integer kbId) {
                log.info("Processing KB_CREATED for ID: {}", kbId);
                long processingStarted = System.currentTimeMillis();

                Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(kbId);
                if (kbOpt.isEmpty()) {
                        log.warn("KnowledgeBase not found: {}", kbId);
                        return CompletableFuture.completedFuture(null);
                }

                KnowledgeBase kb = kbOpt.get();
                String content = kb.getContent();

                // 1. Chunk the content
                long chunkingStart = System.currentTimeMillis();
                List<String> chunks = chunkingService.chunkText(content);
                long chunkingTime = System.currentTimeMillis() - chunkingStart;
                log.info("Created {} chunks for KB: {} in {}ms", chunks.size(), kbId, chunkingTime);

                // 2. Generate embeddings and save contexts
                long embeddingStart = System.currentTimeMillis();
                List<Context> contexts = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                        String chunk = chunks.get(i);

                        float[] vector = LLMProvider.getEmbedding(chunk);

                        // Build metadata for context
                        ObjectNode contextMetadata = objectMapper.createObjectNode();
                        contextMetadata.put("source_kb_id", kbId);
                        contextMetadata.put("index", i);
                        contextMetadata.put("total_chunks", chunks.size());
                        contextMetadata.put("processing_started", Instant.ofEpochMilli(processingStarted).toString());

                        Context ctx = Context.builder()
                                        .knowledgeBase(kb)
                                        .contextData(chunk)
                                        .vector(vector)
                                        .metadata(contextMetadata)
                                        .build();
                        contexts.add(ctx);
                }
                long embeddingTime = System.currentTimeMillis() - embeddingStart;

                // 3. Bulk save (each save triggers CONTEXT_CREATED via NOTIFY)
                List<Context> savedContexts = contextRepository.saveAll(contexts);
                log.info("Saved {} contexts for KB: {} in {}ms", savedContexts.size(), kbId, embeddingTime);

                // 4. Build response with all stored field values
                List<ContextResponse> contextResponses = new ArrayList<>();
                for (int i = 0; i < savedContexts.size(); i++) {
                        Context savedCtx = savedContexts.get(i);
                        ContextResponse response = ContextResponse.from(
                                        savedCtx.getId(),
                                        kbId,
                                        savedCtx.getContextData(),
                                        savedCtx.getVector(),
                                        savedCtx.getMetadata(),
                                        i,
                                        chunks.size(),
                                        savedCtx.getCreatedAt(),
                                        null // updatedAt - not set on initial create
                        );
                        contextResponses.add(response);
                        log.info("Context stored: id={}, kbId={}, chunkIndex={}/{}, vectorDimensions={}, createdAt={}, metadata={}",
                                        response.getId(), response.getKbId(), response.getChunkIndex() + 1,
                                        response.getTotalChunks(), response.getVectorDimensions(),
                                        response.getCreatedAt(), response.getMetadata());
                }

                KnowledgeBaseProcessingResult result = KnowledgeBaseProcessingResult.builder()
                                .kbId(kbId)
                                .contexts(contextResponses)
                                .chunkingTimeMs(chunkingTime)
                                .embeddingTimeMs(embeddingTime)
                                .build();

                return CompletableFuture.completedFuture(result);
        }

        /**
         * Handles CONTEXT_CREATED event.
         * 1. Fetches Context text.
         * 2. Calls LLM for entity/relation extraction.
         * 3. Saves Entities and Relations using UPSERT pattern.
         * 
         * @return ContextProcessingResult with details of all stored entities and
         *         relations
         */
        @Async
        @Transactional
        public CompletableFuture<ContextProcessingResult> handleContextCreated(Integer contextId) {
                log.info("Processing CONTEXT_CREATED for ID: {}", contextId);
                long extractionStarted = System.currentTimeMillis();

                Optional<Context> ctxOpt = contextRepository.findById(contextId);
                if (ctxOpt.isEmpty()) {
                        log.warn("Context not found: {}", contextId);
                        return CompletableFuture.completedFuture(null);
                }

                Context ctx = ctxOpt.get();
                Integer kbId = ctx.getKnowledgeBase().getId();

                // 1. Extract entities & relations via LLM
                ExtractionService.ExtractionResult result = extractionService.extractFromText(ctx.getContextData());

                // 2. Save entities with embeddings and metadata
                List<EntityResponse> entityResponses = new ArrayList<>();
                List<RagEntity> savedEntities = new ArrayList<>();

                for (ExtractionService.ExtractedEntity extracted : result.getEntities()) {
                        // Check if entity already exists (deduplication by name)
                        Optional<RagEntity> existing = entityRepository.findByEntityName(extracted.getName());

                        if (existing.isPresent()) {
                                // Link existing entity to this context
                                RagEntity existingEntity = existing.get();
                                if (!existingEntity.getContexts().contains(ctx)) {
                                        existingEntity.getContexts().add(ctx);
                                        entityRepository.save(existingEntity);
                                }
                                savedEntities.add(existingEntity);

                                EntityResponse entityResponse = EntityResponse.from(
                                                existingEntity.getId(),
                                                existingEntity.getEntityName(),
                                                existingEntity.getVector(),
                                                existingEntity.getMetadata(),
                                                false, // isNewEntity = false
                                                contextId,
                                                existingEntity.getCreatedAt(),
                                                null // updatedAt
                                );
                                entityResponses.add(entityResponse);
                                log.info("Entity linked (existing): id={}, name='{}', type={}, createdAt={}, metadata={}",
                                                entityResponse.getId(), entityResponse.getEntityName(),
                                                entityResponse.getType(), entityResponse.getCreatedAt(),
                                                entityResponse.getMetadata());
                                continue;
                        }

                        float[] entityVector = LLMProvider
                                        .getEmbedding(extracted.getName() + " " + extracted.getDescription());

                        // Build metadata for entity
                        ObjectNode entityMetadata = objectMapper.createObjectNode();
                        entityMetadata.put("type", extracted.getType());
                        entityMetadata.put("description", extracted.getDescription());
                        entityMetadata.put("source_kb_id", kbId);
                        entityMetadata.put("source_context_id", contextId);
                        entityMetadata.put("extraction_started", Instant.ofEpochMilli(extractionStarted).toString());

                        RagEntity entity = RagEntity.builder()
                                        .entityName(extracted.getName())
                                        .vector(entityVector)
                                        .metadata(entityMetadata)
                                        .contexts(new ArrayList<>(List.of(ctx)))
                                        .build();

                        RagEntity savedEntity = entityRepository.save(entity);
                        savedEntities.add(savedEntity);

                        EntityResponse entityResponse = EntityResponse.from(
                                        savedEntity.getId(),
                                        savedEntity.getEntityName(),
                                        savedEntity.getVector(),
                                        savedEntity.getMetadata(),
                                        true, // isNewEntity = true
                                        contextId,
                                        savedEntity.getCreatedAt(),
                                        null // updatedAt
                        );
                        entityResponses.add(entityResponse);
                        log.info("Entity stored (new): id={}, name='{}', type={}, vectorDimensions={}, createdAt={}, metadata={}",
                                        entityResponse.getId(), entityResponse.getEntityName(),
                                        entityResponse.getType(), entityResponse.getVectorDimensions(),
                                        entityResponse.getCreatedAt(), entityResponse.getMetadata());
                }

                // 3. Save relations using UPSERT pattern
                List<RelationResponse> relationResponses = new ArrayList<>();
                for (ExtractionService.ExtractedRelation rel : result.getRelations()) {
                        Optional<RagEntity> sourceOpt = savedEntities.stream()
                                        .filter(e -> e.getEntityName().equalsIgnoreCase(rel.getSourceName()))
                                        .findFirst();
                        Optional<RagEntity> targetOpt = savedEntities.stream()
                                        .filter(e -> e.getEntityName().equalsIgnoreCase(rel.getTargetName()))
                                        .findFirst();

                        if (sourceOpt.isPresent() && targetOpt.isPresent()) {
                                RagEntity source = sourceOpt.get();
                                RagEntity target = targetOpt.get();

                                // Use the upsert method (increments edge_weight on conflict)
                                relationRepository.upsertRelation(
                                                source.getId(),
                                                target.getId(),
                                                rel.getRelationType());

                                RelationResponse relationResponse = RelationResponse.from(
                                                source.getId(),
                                                source.getEntityName(),
                                                target.getId(),
                                                target.getEntityName(),
                                                rel.getRelationType(),
                                                1, // Initial edge weight
                                                null, // metadata - not set for relations yet
                                                true, // We don't know if it's new or updated without extra query
                                                Instant.now() // createdAt approximation
                                );
                                relationResponses.add(relationResponse);
                                log.info("Relation stored: {} --[{}]--> {} (id:{} -> id:{}), createdAt={}",
                                                relationResponse.getSourceName(), relationResponse.getRelationType(),
                                                relationResponse.getTargetName(), relationResponse.getSourceId(),
                                                relationResponse.getTargetId(), relationResponse.getCreatedAt());
                        }
                }

                long extractionTime = System.currentTimeMillis() - extractionStarted;
                log.info("Extracted {} entities and {} relations from context {} in {}ms",
                                entityResponses.size(), relationResponses.size(), contextId, extractionTime);

                ContextProcessingResult processingResult = ContextProcessingResult.builder()
                                .contextId(contextId)
                                .entities(entityResponses)
                                .relations(relationResponses)
                                .extractionTimeMs(extractionTime)
                                .build();

                return CompletableFuture.completedFuture(processingResult);
        }
}
