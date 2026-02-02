package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Coordinates the RAG ingestion pipeline.
 * Handles chunking, embedding generation, and entity/relation extraction.
 * Logs processing results for testing/debugging.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionWorker {

        private final ChunkingService chunkingService;
        private final ExtractionService extractionService;
        private final ObjectMapper objectMapper;

        /**
         * Processes content from a knowledge base entry.
         * Chunks text and generates embeddings for each chunk.
         * Fire-and-forget: results are logged, not persisted.
         */
        @Async
        public void processKnowledgeBase(UUID kbId, String content) {
                log.info("Processing KB_CREATED for id: {}", kbId);
                long startTime = System.currentTimeMillis();

                if (content == null || content.isBlank()) {
                        log.warn("Empty content for KB: {}", kbId);
                        return;
                }

                // 1. Chunk the content
                long chunkingStart = System.currentTimeMillis();
                List<String> chunks = chunkingService.chunkText(content, ChunkingService.DEFAULT_CHUNK_SIZE,
                                ChunkingService.DEFAULT_OVERLAP);
                long chunkingTime = System.currentTimeMillis() - chunkingStart;
                log.info("Chunked KB {}: {} chunks in {}ms", kbId, chunks.size(), chunkingTime);

                // 2. Generate embeddings for each chunk
                long embeddingStart = System.currentTimeMillis();
                for (int i = 0; i < chunks.size(); i++) {
                        String chunk = chunks.get(i);
                        float[] embedding = LLMProvider.getEmbedding(chunk);

                        log.info("Context {}/{} for KB {}: chunkLength={}, embeddingDimensions={}",
                                        i + 1, chunks.size(), kbId, chunk.length(), embedding.length);
                }
                long embeddingTime = System.currentTimeMillis() - embeddingStart;

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("KB {} processing complete: {} chunks, chunkingTime={}ms, embeddingTime={}ms, totalTime={}ms",
                                kbId, chunks.size(), chunkingTime, embeddingTime, totalTime);
        }

        /**
         * Processes a context chunk for entity/relation extraction.
         * Fire-and-forget: results are logged, not persisted.
         */
        @Async
        public void processContext(UUID contextId, UUID kbId, String textChunk) {
                log.info("Processing CONTEXT_CREATED for id: {}, kbId: {}", contextId, kbId);
                long startTime = System.currentTimeMillis();

                if (textChunk == null || textChunk.isBlank()) {
                        log.warn("Empty text chunk for context: {}", contextId);
                        return;
                }

                // 1. Extract entities & relations via LLM
                ExtractionService.ExtractionResult result = extractionService.extractFromText(textChunk);

                // 2. Log extracted entities
                for (ExtractionService.ExtractedEntity entity : result.getEntities()) {
                        float[] embedding = LLMProvider.getEmbedding(entity.getName() + " " + entity.getDescription());
                        log.info("Entity extracted from context {}: name='{}', type='{}', embeddingDimensions={}",
                                        contextId, entity.getName(), entity.getType(), embedding.length);
                }

                // 3. Log extracted relations
                for (ExtractionService.ExtractedRelation relation : result.getRelations()) {
                        log.info("Relation extracted from context {}: {} --[{}]--> {}",
                                        contextId, relation.getSourceName(), relation.getRelationType(),
                                        relation.getTargetName());
                }

                // 4. Log processing summary with metadata
                ExtractionService.ExtractedMetadata metadata = result.getMetadata();
                long totalTime = System.currentTimeMillis() - startTime;
                log.info("Context {} complete: {} entities, {} relations | topics={}, sentiment={}, totalTime={}ms",
                                contextId, result.getEntities().size(), result.getRelations().size(),
                                metadata.getTopics(), metadata.getSentiment(), totalTime);
        }
}
