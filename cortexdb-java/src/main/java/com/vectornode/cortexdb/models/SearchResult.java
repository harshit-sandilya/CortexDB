package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.UUID;

/**
 * A single search result within a {@link QueryResponse}.
 * Mirrors the backend {@code QueryResponse.SearchResult} inner class.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult {

    private UUID id;
    private String content;
    private double score;
    private String type;
    private Map<String, Object> metadata;

    public SearchResult() {
    }

    // ── Getters & Setters ────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
