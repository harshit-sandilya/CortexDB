package com.vectornode.memory.ingest.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response after storing data in knowledge_base table.
 * Returns all stored field values.
 */
@Data
@Builder
public class KnowledgeBaseResponse {
        private Integer id;
        private String userId;
        private String content; // Full content
        private String contentPreview; // First 100 chars for display
        private int vectorDimensions;
        private JsonNode metadata;
        private Instant createdAt;
        private Instant updatedAt;

        public static KnowledgeBaseResponse from(
                        Integer id,
                        String userId,
                        String content,
                        float[] vector,
                        JsonNode metadata,
                        Instant createdAt,
                        Instant updatedAt) {
                return KnowledgeBaseResponse.builder()
                                .id(id)
                                .userId(userId)
                                .content(content)
                                .contentPreview(content != null && content.length() > 100
                                                ? content.substring(0, 100) + "..."
                                                : content)
                                .vectorDimensions(vector != null ? vector.length : 0)
                                .metadata(metadata)
                                .createdAt(createdAt)
                                .updatedAt(updatedAt)
                                .build();
        }
}
