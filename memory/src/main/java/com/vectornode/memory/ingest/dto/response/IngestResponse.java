package com.vectornode.memory.ingest.dto.response;

import com.vectornode.memory.entity.KnowledgeBase;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Response for document ingestion API.
 * Contains the persisted KnowledgeBase entity and processing metadata.
 */
@Data
@Builder
public class IngestResponse {
    private KnowledgeBase knowledgeBase; // The complete persisted entity
    private String status;
    private String message;
    private Long processingTimeMs;
    private Long embeddingTimeMs;
}
