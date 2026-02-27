package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/**
 * Entity model representing a knowledge-graph entity (RagEntity).
 * Mirrors the backend {@code RagEntity} entity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Entity {

    private UUID id;
    private String name;
    private String type;
    private String description;
    private List<Float> embedding;

    public Entity() {
    }

    // ── Getters & Setters ────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding;
    }
}
