package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Simplified KnowledgeBase entity returned in ingest responses.
 * Mirrors the backend {@code KnowledgeBase} entity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeBase {

    private UUID id;
    private String uid;
    private String content;
    private Instant createdAt;

    public KnowledgeBase() {
    }

    // ── Getters & Setters ────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
