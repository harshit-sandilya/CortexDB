package com.vectornode.memory.ingest.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Response for document ingestion API.
 */
@Data
@Builder
public class IngestResponse {
    private UUID knowledgeBaseId;
    private String status;
    private String message;
    private int embeddingDimensions;
    private Long processingTimeMs;
    private Long embeddingTimeMs;
}
