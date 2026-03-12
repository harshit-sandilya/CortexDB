package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Request to ingest a prompt payload into CortexDB.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestPromptRequest {

    private String uid;
    private ConverserRole converser;
    private String text;
    private Map<String, Object> metadata;

    public IngestPromptRequest() {
    }

    public IngestPromptRequest(String uid, ConverserRole converser, String text, Map<String, Object> metadata) {
        this.uid = uid;
        this.converser = converser;
        this.text = text;
        this.metadata = metadata;
    }

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

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
