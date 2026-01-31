package com.vectornode.memory.ingest.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response after storing data in contexts table.
 * Returns all stored field values.
 */
@Data
@Builder
public class ContextResponse {
        private Integer id;
        private Integer kbId;
        private String contextData; // Full chunk text
        private String contextDataPreview; // First 100 chars for display
        private int vectorDimensions;
        private JsonNode metadata;
        private int chunkIndex;
        private int totalChunks;
        private Instant createdAt;
        private Instant updatedAt;

        public static ContextResponse from(
                        Integer id,
                        Integer kbId,
                        String contextData,
                        float[] vector,
                        JsonNode metadata,
                        int chunkIndex,
                        int totalChunks,
                        Instant createdAt,
                        Instant updatedAt) {
                return ContextResponse.builder()
                                .id(id)
                                .kbId(kbId)
                                .contextData(contextData)
                                .contextDataPreview(contextData != null && contextData.length() > 100
                                                ? contextData.substring(0, 100) + "..."
                                                : contextData)
                                .vectorDimensions(vector != null ? vector.length : 0)
                                .metadata(metadata)
                                .chunkIndex(chunkIndex)
                                .totalChunks(totalChunks)
                                .createdAt(createdAt)
                                .updatedAt(updatedAt)
                                .build();
        }
}
