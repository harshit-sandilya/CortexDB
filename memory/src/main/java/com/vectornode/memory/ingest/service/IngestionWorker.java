package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.entity.Relation;
import com.vectornode.memory.repository.ContextRepository;
import com.vectornode.memory.repository.EntityRepository;
import com.vectornode.memory.repository.KnowledgeBaseRepository;
import com.vectornode.memory.repository.RelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
     */
    @Async
    @Transactional
    public void handleKbCreated(UUID kbId) {
        log.info("Processing KB_CREATED for ID: {}", kbId);
        Instant processingStarted = Instant.now();

        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(kbId);
        if (kbOpt.isEmpty()) {
            log.warn("KnowledgeBase not found: {}", kbId);
            return;
        }

        KnowledgeBase kb = kbOpt.get();
        String content = kb.getContent();

        // 1. Chunk the content
        List<String> chunks = chunkingService.chunkText(content);
        log.info("Created {} chunks for KB: {}", chunks.size(), kbId);

        // 2. Generate embeddings and save contexts
        List<Context> contexts = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);

            float[] embedding = LLMProvider.getEmbedding(chunk);

            // Build metadata for context
            ObjectNode contextMetadata = objectMapper.createObjectNode();
            contextMetadata.put("source_kb_id", kbId.toString());
            contextMetadata.put("chunk_index", i);
            contextMetadata.put("total_chunks", chunks.size());
            contextMetadata.put("chunk_length", chunk.length());
            contextMetadata.put("processing_started", processingStarted.toString());

            Context ctx = Context.builder()
                    .knowledgeBase(kb)
                    .textChunk(chunk)
                    .vectorEmbedding(embedding)
                    .chunkIndex(i)
                    .metadata(contextMetadata)
                    .build();
            // createdAt is set by @PrePersist in BaseEntity
            contexts.add(ctx);
        }

        // 3. Bulk save (each save triggers CONTEXT_CREATED)
        contextRepository.saveAll(contexts);
        log.info("Saved {} contexts for KB: {}", contexts.size(), kbId);
    }

    /**
     * Handles CONTEXT_CREATED event.
     * 1. Fetches Context text.
     * 2. Calls LLM for entity/relation extraction.
     * 3. Saves Entities and Relations.
     */
    @Async
    @Transactional
    public void handleContextCreated(UUID contextId) {
        log.info("Processing CONTEXT_CREATED for ID: {}", contextId);
        Instant extractionStarted = Instant.now();

        Optional<Context> ctxOpt = contextRepository.findById(contextId);
        if (ctxOpt.isEmpty()) {
            log.warn("Context not found: {}", contextId);
            return;
        }

        Context ctx = ctxOpt.get();
        UUID kbId = ctx.getKnowledgeBase().getId();

        // 1. Extract entities & relations via LLM
        ExtractionService.ExtractionResult result = extractionService.extractFromText(ctx.getTextChunk());

        // 2. Save entities with embeddings and metadata
        List<RagEntity> savedEntities = new ArrayList<>();
        for (ExtractionService.ExtractedEntity extracted : result.getEntities()) {
            // Check if entity already exists (deduplication by name)
            Optional<RagEntity> existing = entityRepository.findByName(extracted.getName());
            if (existing.isPresent()) {
                // Link existing entity to this context
                RagEntity existingEntity = existing.get();
                if (!existingEntity.getContexts().contains(ctx)) {
                    existingEntity.getContexts().add(ctx);
                    entityRepository.save(existingEntity);
                }
                savedEntities.add(existingEntity);
                continue;
            }

            float[] entityEmbedding = LLMProvider.getEmbedding(extracted.getName() + " " + extracted.getDescription());

            // Build metadata for entity
            ObjectNode entityMetadata = objectMapper.createObjectNode();
            entityMetadata.put("source_kb_id", kbId.toString());
            entityMetadata.put("source_context_id", contextId.toString());
            entityMetadata.put("extraction_started", extractionStarted.toString());
            entityMetadata.put("extraction_method", "llm");

            RagEntity entity = RagEntity.builder()
                    .name(extracted.getName())
                    .type(extracted.getType())
                    .description(extracted.getDescription())
                    .vectorEmbedding(entityEmbedding)
                    .metadata(entityMetadata)
                    .contexts(new ArrayList<>(List.of(ctx)))
                    .build();
            // createdAt is set by @PrePersist in BaseEntity

            savedEntities.add(entityRepository.save(entity));
        }

        // 3. Save relations with metadata
        for (ExtractionService.ExtractedRelation rel : result.getRelations()) {
            Optional<RagEntity> sourceOpt = savedEntities.stream()
                    .filter(e -> e.getName().equalsIgnoreCase(rel.getSourceName()))
                    .findFirst();
            Optional<RagEntity> targetOpt = savedEntities.stream()
                    .filter(e -> e.getName().equalsIgnoreCase(rel.getTargetName()))
                    .findFirst();

            if (sourceOpt.isPresent() && targetOpt.isPresent()) {
                // Build metadata for relation
                ObjectNode relationMetadata = objectMapper.createObjectNode();
                relationMetadata.put("source_kb_id", kbId.toString());
                relationMetadata.put("source_context_id", contextId.toString());
                relationMetadata.put("extraction_started", extractionStarted.toString());
                relationMetadata.put("extraction_method", "llm");

                Relation relation = Relation.builder()
                        .sourceEntity(sourceOpt.get())
                        .targetEntity(targetOpt.get())
                        .relationType(rel.getRelationType())
                        .edgeWeight(1)
                        .metadata(relationMetadata)
                        .build();
                // createdAt is set by @PrePersist in BaseEntity
                relationRepository.save(relation);
            }
        }

        log.info("Extracted {} entities and {} relations from context: {}",
                result.getEntities().size(), result.getRelations().size(), contextId);
    }
}
