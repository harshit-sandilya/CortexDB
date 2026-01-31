package com.vectornode.memory.ingest.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Aggregate response for the complete ingestion process.
 * Contains detailed responses from each stage of the pipeline.
 */
@Data
@Builder
public class IngestResponse {
    // Legacy fields for backward compatibility
    private String knowledgeBaseId;
    private String status;
    private String message;

    // Detailed responses from each stage
    private KnowledgeBaseResponse knowledgeBase;
    private List<ContextResponse> contexts;
    private List<EntityResponse> entities;
    private List<RelationResponse> relations;

    // Processing metrics
    private Long processingTimeMs;
    private Long embeddingTimeMs;
    private Long extractionTimeMs;
}
