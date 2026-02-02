package com.vectornode.memory.ingest.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.ingest.dto.request.IngestContentRequest;
import com.vectornode.memory.ingest.dto.response.IngestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for ingesting documents.
 * Handles embedding generation and returns processing results.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestService {

        /**
         * Processes a document ingestion request.
         * Generates embeddings and returns response.
         */
        public IngestResponse ingestDocument(IngestContentRequest request) {
                log.info("Ingesting document for uid: {}, converser: {}", request.getUid(), request.getConverser());
                long startTime = System.currentTimeMillis();

                try {
                        // Generate Embedding
                        long embeddingStart = System.currentTimeMillis();
                        float[] embedding = LLMProvider.getEmbedding(request.getContent());
                        long embeddingTime = System.currentTimeMillis() - embeddingStart;

                        UUID tempId = UUID.randomUUID();
                        long processingTime = System.currentTimeMillis() - startTime;

                        log.info("Document ingested: id={}, uid={}, converser={}, contentLength={}, embeddingDimensions={}, processingTime={}ms",
                                        tempId, request.getUid(), request.getConverser(),
                                        request.getContent() != null ? request.getContent().length() : 0,
                                        embedding.length, processingTime);

                        return IngestResponse.builder()
                                        .knowledgeBaseId(tempId)
                                        .status("SUCCESS")
                                        .message("Document processed successfully.")
                                        .embeddingDimensions(embedding.length)
                                        .processingTimeMs(processingTime)
                                        .embeddingTimeMs(embeddingTime)
                                        .build();

                } catch (Exception e) {
                        log.error("Failed to ingest document", e);
                        throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
                }
        }
}
