package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.ingest.dto.request.IngestContentRequest;
import com.vectornode.memory.ingest.dto.response.IngestResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for ingesting documents.
 * Persists knowledge base entries directly using EntityManager.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestService {

        private final ObjectMapper objectMapper;

        @PersistenceContext
        private EntityManager entityManager;

        /**
         * Processes a content ingestion request.
         * Generates embeddings, persists to database, and returns the inserted row.
         */
        @Transactional
        public IngestResponse ingestContent(IngestContentRequest request) {
                log.info("Ingesting document for uid: {}, converser: {}", request.getUid(), request.getConverser());
                long startTime = System.currentTimeMillis();

                try {
                        // Generate Embedding
                        long embeddingStart = System.currentTimeMillis();
                        float[] embedding = LLMProvider.getEmbedding(request.getContent());
                        long embeddingTime = System.currentTimeMillis() - embeddingStart;

                        // Create KnowledgeBase entity with metadata
                        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                                        .uid(request.getUid())
                                        .converser(request.getConverser())
                                        .content(request.getContent())
                                        .vectorEmbedding(embedding)
                                        .build();

                        // Add metadata
                        knowledgeBase.setMetadata(objectMapper.createObjectNode()
                                        .put("contentLength", request.getContent().length())
                                        .put("embeddingDimensions", embedding.length)
                                        .put("embeddingTimeMs", embeddingTime));

                        // Persist directly using EntityManager
                        entityManager.persist(knowledgeBase);
                        entityManager.flush(); // Ensure ID is generated

                        long processingTime = System.currentTimeMillis() - startTime;

                        // Log the complete persisted row
                        log.info("KB_ROW | id={} | uid={} | converser={} | content_length={} | vector_dims={} | metadata={} | created_at={}",
                                        knowledgeBase.getId(),
                                        knowledgeBase.getUid(),
                                        knowledgeBase.getConverser(),
                                        knowledgeBase.getContent().length(),
                                        knowledgeBase.getVectorEmbedding().length,
                                        knowledgeBase.getMetadata(),
                                        knowledgeBase.getCreatedAt());

                        // Return response with the full persisted entity
                        return IngestResponse.builder()
                                        .knowledgeBase(knowledgeBase)
                                        .status("SUCCESS")
                                        .message("Document ingested successfully")
                                        .processingTimeMs(processingTime)
                                        .embeddingTimeMs(embeddingTime)
                                        .build();

                } catch (Exception e) {
                        log.error("Failed to ingest document", e);
                        throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
                }
        }
}
