package com.vectornode.memory.ingest.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response after storing data in relations table.
 * Returns all stored field values.
 */
@Data
@Builder
public class RelationResponse {
    private Integer sourceId;
    private String sourceName;
    private Integer targetId;
    private String targetName;
    private String relationType;
    private Integer edgeWeight;
    private JsonNode metadata;
    private boolean isNew; // true if new relation, false if edge_weight was incremented
    private Instant createdAt;

    public static RelationResponse from(
            Integer sourceId,
            String sourceName,
            Integer targetId,
            String targetName,
            String relationType,
            Integer edgeWeight,
            JsonNode metadata,
            boolean isNew,
            Instant createdAt) {
        return RelationResponse.builder()
                .sourceId(sourceId)
                .sourceName(sourceName)
                .targetId(targetId)
                .targetName(targetName)
                .relationType(relationType)
                .edgeWeight(edgeWeight)
                .metadata(metadata)
                .isNew(isNew)
                .createdAt(createdAt)
                .build();
    }
}
