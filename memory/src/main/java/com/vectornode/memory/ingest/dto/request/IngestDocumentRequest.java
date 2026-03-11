package com.vectornode.memory.ingest.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class IngestDocumentRequest {
    @NotBlank(message = "UID cannot be blank")
    private String uid;

    @NotBlank(message = "Document Title cannot be blank")
    private String documentTitle;

    @NotBlank(message = "Document Text cannot be blank")
    private String documentText;

    private Map<String, Object> metadata;
}
