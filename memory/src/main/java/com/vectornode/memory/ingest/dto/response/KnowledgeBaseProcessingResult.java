package com.vectornode.memory.ingest.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result from processing a knowledge base - contains created contexts.
 */
@Data
@Builder
public class KnowledgeBaseProcessingResult {
    private Integer kbId;
    private List<ContextResponse> contexts;
    private Long chunkingTimeMs;
    private Long embeddingTimeMs;
}
