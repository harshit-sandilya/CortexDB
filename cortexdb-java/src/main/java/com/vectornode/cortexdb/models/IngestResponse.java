package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from the {@code POST /api/ingest/document} endpoint.
 * Mirrors the backend {@code IngestResponse} DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IngestResponse {

    private KnowledgeBase knowledgeBase;
    private String status;
    private String message;
    private Long processingTimeMs;
    private Long embeddingTimeMs;

    public IngestResponse() {
    }

    // ── Getters & Setters ────────────────────────────────────────

    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public Long getEmbeddingTimeMs() {
        return embeddingTimeMs;
    }

    public void setEmbeddingTimeMs(Long embeddingTimeMs) {
        this.embeddingTimeMs = embeddingTimeMs;
    }
}
