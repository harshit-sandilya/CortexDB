package com.vectornode.memory.ingest.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngestResponse {
    private String knowledgeBaseId;
    private String status;
    private String message;
}
