package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Request to ingest a document into CortexDB.
 * Mirrors the backend {@code IngestContentRequest} DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestRequest {

    private String uid;
    private ConverserRole converser;
    private String content;
    private Map<String, Object> metadata;

    public IngestRequest() {
    }

    public IngestRequest(String uid, ConverserRole converser, String content,
            Map<String, Object> metadata) {
        this.uid = uid;
        this.converser = converser;
        this.content = content;
        this.metadata = metadata;
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public ConverserRole getConverser() {
        return converser;
    }

    public void setConverser(ConverserRole converser) {
        this.converser = converser;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
