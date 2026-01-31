package com.vectornode.memory.ingest.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.ingest.dto.request.IngestDocumentRequest;
import com.vectornode.memory.ingest.dto.response.IngestResponse;
import com.vectornode.memory.ingest.dto.response.KnowledgeBaseResponse;
import com.vectornode.memory.ingest.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for ingesting documents into the knowledge base.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestService {

        private final KnowledgeBaseRepository knowledgeBaseRepository;

        /**
         * Ingests a new document into the knowledge base.
         * Returns detailed response with all stored field values.
         *
         * @param request The document ingestion request.
         * @return Response containing details of the created knowledge base entry.
         */
        @Transactional
        public IngestResponse ingestDocument(IngestDocumentRequest request) {
                log.info("Ingesting document for user: {}", request.getUserId());
                long startTime = System.currentTimeMillis();

                try {
                        // 1. Generate Embedding
                        long embeddingStart = System.currentTimeMillis();
                        float[] vector = LLMProvider.getEmbedding(request.getContent());
                        long embeddingTime = System.currentTimeMillis() - embeddingStart;
                        log.info("Embedding generated in {} ms, dimensions: {}", embeddingTime, vector.length);

                        // 2. Create KnowledgeBase Entity
                        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                                        .userId(request.getUserId())
                                        .content(request.getContent())
                                        .vector(vector)
                                        .build();

                        // 3. Save to DB (triggers NOTIFY for async processing)
                        KnowledgeBase savedKb = knowledgeBaseRepository.save(knowledgeBase);
                        long processingTime = System.currentTimeMillis() - startTime;

                        // 4. Build detailed response with all stored field values
                        KnowledgeBaseResponse kbResponse = KnowledgeBaseResponse.from(
                                        savedKb.getId(),
                                        savedKb.getUserId(),
                                        savedKb.getContent(),
                                        savedKb.getVector(),
                                        savedKb.getMetadata(),
                                        savedKb.getCreatedAt(),
                                        null // updatedAt - not set on initial create
                        );

                        log.info("KnowledgeBase stored: id={}, userId={}, contentLength={}, vectorDimensions={}, createdAt={}, processingTime={}ms",
                                        kbResponse.getId(), kbResponse.getUserId(),
                                        request.getContent() != null ? request.getContent().length() : 0,
                                        kbResponse.getVectorDimensions(), kbResponse.getCreatedAt(), processingTime);

                        return IngestResponse.builder()
                                        .knowledgeBaseId(savedKb.getId().toString())
                                        .status("SUCCESS")
                                        .message("Document ingested successfully. Async processing (chunking, entity extraction) will follow.")
                                        .knowledgeBase(kbResponse)
                                        .processingTimeMs(processingTime)
                                        .embeddingTimeMs(embeddingTime)
                                        .build();

                } catch (Exception e) {
                        log.error("Failed to ingest document", e);
                        throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
                }
        }
}
