package com.vectornode.memory.ingest.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.enums.ConverserRole;
import com.vectornode.memory.ingest.dto.request.IngestDocumentRequest;
import com.vectornode.memory.ingest.dto.response.IngestResponse;
import com.vectornode.memory.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * Ingests a new document into the knowledge base.
     * 1. Generates vector embedding for the content.
     * 2. Saves content + embedding to KnowledgeBase table.
     *
     * @param request The document ingestion request.
     * @return Response containing the ID of the created knowledge base entry.
     */
    @Transactional
    public IngestResponse ingestDocument(IngestDocumentRequest request) {
        log.info("Ingesting document for user: {}", request.getUserId());

        try {
            // 1. Generate Embedding
            long startTime = System.currentTimeMillis();
            // LLMProvider.getEmbedding returns float[] directly
            float[] vectorEmbedding = LLMProvider.getEmbedding(request.getContent());
            log.info("Embedding generated in {} ms", System.currentTimeMillis() - startTime);

            // 2. Create KnowledgeBase Entity
            KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                    .uid(UUID.randomUUID().toString())
                    .converser(ConverserRole.USER) // Assuming documents are user-provided
                    .content(request.getContent())
                    .vectorEmbedding(vectorEmbedding)
                    .build();

            // 3. Save to DB
            KnowledgeBase savedKb = knowledgeBaseRepository.save(knowledgeBase);
            log.info("Document saved with ID: {}", savedKb.getId());

            return IngestResponse.builder()
                    .knowledgeBaseId(savedKb.getId().toString())
                    .status("SUCCESS")
                    .message("Document ingested successfully.")
                    .build();

        } catch (Exception e) {
            log.error("Failed to ingest document", e);
            throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
        }
    }
}
