package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Relation between two entities in the knowledge graph.
 * Mirrors the backend {@code Relation} entity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Relation {

    private UUID id;
    private UUID sourceEntityId;
    private UUID targetEntityId;
    private String relationType;
    private int edgeWeight;

    public Relation() {
    }

    // ── Getters & Setters ────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(UUID sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public UUID getTargetEntityId() {
        return targetEntityId;
    }

    public void setTargetEntityId(UUID targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public int getEdgeWeight() {
        return edgeWeight;
    }

    public void setEdgeWeight(int edgeWeight) {
        this.edgeWeight = edgeWeight;
    }
}
