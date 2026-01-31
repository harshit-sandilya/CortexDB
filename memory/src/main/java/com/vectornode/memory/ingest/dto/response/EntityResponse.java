package com.vectornode.memory.ingest.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response after storing data in entities table.
 * Returns all stored field values.
 */
@Data
@Builder
public class EntityResponse {
        private Integer id;
        private String entityName;
        private int vectorDimensions;
        private JsonNode metadata; // Contains type, description, source_kb_id, source_context_id, etc.
        private boolean isNewEntity; // true if newly created, false if existing entity was linked
        private Integer sourceContextId;
        private Instant createdAt;
        private Instant updatedAt;

        // Convenience getters for common metadata fields
        public String getType() {
                return metadata != null && metadata.has("type") ? metadata.get("type").asText() : null;
        }

        public String getDescription() {
                return metadata != null && metadata.has("description") ? metadata.get("description").asText() : null;
        }

        public static EntityResponse from(
                        Integer id,
                        String entityName,
                        float[] vector,
                        JsonNode metadata,
                        boolean isNewEntity,
                        Integer sourceContextId,
                        Instant createdAt,
                        Instant updatedAt) {
                return EntityResponse.builder()
                                .id(id)
                                .entityName(entityName)
                                .vectorDimensions(vector != null ? vector.length : 0)
                                .metadata(metadata)
                                .isNewEntity(isNewEntity)
                                .sourceContextId(sourceContextId)
                                .createdAt(createdAt)
                                .updatedAt(updatedAt)
                                .build();
        }
}
