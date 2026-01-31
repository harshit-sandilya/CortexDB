package com.vectornode.memory.ingest.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result from processing a context - contains extracted entities and relations.
 */
@Data
@Builder
public class ContextProcessingResult {
    private Integer contextId;
    private List<EntityResponse> entities;
    private List<RelationResponse> relations;
    private Long extractionTimeMs;
}
