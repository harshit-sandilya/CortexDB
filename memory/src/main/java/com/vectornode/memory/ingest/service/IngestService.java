package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.ingest.dto.request.IngestDocumentRequest;
import com.vectornode.memory.ingest.dto.request.IngestPromptRequest;
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
         * Processes a prompt ingestion request.
         * Generates embeddings, persists to database, and returns the inserted row.
         */
        @Transactional
        public IngestResponse processPrompt(IngestPromptRequest request) {
                log.info("Ingesting prompt for uid: {}, converser: {}", request.getUid(), request.getConverser());
                long startTime = System.currentTimeMillis();

                try {
                        // Generate Embedding
                        long embeddingStart = System.currentTimeMillis();
                        float[] embedding = LLMProvider.getEmbedding(request.getText());
                        long embeddingTime = System.currentTimeMillis() - embeddingStart;

                        // Create KnowledgeBase entity with metadata
                        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                                        .uid(request.getUid())
                                        .converser(request.getConverser())
                                        .content(request.getText())
                                        .vectorEmbedding(embedding)
                                        .build();

                        // Add metadata
                        knowledgeBase.setMetadata(objectMapper.createObjectNode()
                                        .put("contentLength", request.getText().length())
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
                                        .message("Prompt ingested successfully")
                                        .processingTimeMs(processingTime)
                                        .embeddingTimeMs(embeddingTime)
                                        .build();

                } catch (Exception e) {
                        log.error("Failed to ingest prompt", e);
                        throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
                }
        }

        /**
         * Processes a document ingestion request.
         * For now, it simply saves the document in the DB.
         * The async listener or direct invocation will trigger Phase 2 (PageIndex).
         */
        @Transactional
        public IngestResponse processDocument(IngestDocumentRequest request) {
                log.info("Ingesting document for uid: {}, title: {}", request.getUid(), request.getDocumentTitle());
                long startTime = System.currentTimeMillis();

                try {
                        // Create KnowledgeBase entity with metadata
                        // We use the new DOCUMENT converser role
                        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                                        .uid(request.getUid())
                                        .converser(com.vectornode.memory.entity.enums.ConverserRole.DOCUMENT)
                                        .content(request.getDocumentText())
                                        // Documents might be too large to embed whole natively - we will leave it null
                                        .vectorEmbedding(new float[768])
                                        .build();

                        // Add metadata
                        knowledgeBase.setMetadata(objectMapper.createObjectNode()
                                        .put("documentTitle", request.getDocumentTitle())
                                        .put("contentLength", request.getDocumentText().length()));

                        // Persist directly using EntityManager
                        entityManager.persist(knowledgeBase);
                        entityManager.flush(); // Ensure ID is generated

                        long processingTime = System.currentTimeMillis() - startTime;

                        log.info("KB_ROW | id={} | uid={} | converser=DOCUMENT | content_length={} | metadata={} | created_at={}",
                                        knowledgeBase.getId(),
                                        knowledgeBase.getUid(),
                                        knowledgeBase.getContent().length(),
                                        knowledgeBase.getMetadata(),
                                        knowledgeBase.getCreatedAt());

                        // Return response with the full persisted entity
                        return IngestResponse.builder()
                                        .knowledgeBase(knowledgeBase)
                                        .status("SUCCESS")
                                        .message("Document ingested successfully")
                                        .processingTimeMs(processingTime)
                                        .embeddingTimeMs(0L)
                                        .build();

                } catch (Exception e) {
                        log.error("Failed to ingest document", e);
                        throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
                }
        }
}
