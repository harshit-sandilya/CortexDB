package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Response from the {@code POST /api/setup} endpoint.
 * Mirrors the backend {@code SetupResponse} DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SetupResponse {

    private String message;
    private boolean success;
    private String configuredProvider;
    private String configuredChatModel;
    private String configuredEmbedModel;
    private String baseUrl;
    private Instant timestamp;

    public SetupResponse() {
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getConfiguredProvider() {
        return configuredProvider;
    }

    public void setConfiguredProvider(String configuredProvider) {
        this.configuredProvider = configuredProvider;
    }

    public String getConfiguredChatModel() {
        return configuredChatModel;
    }

    public void setConfiguredChatModel(String configuredChatModel) {
        this.configuredChatModel = configuredChatModel;
    }

    public String getConfiguredEmbedModel() {
        return configuredEmbedModel;
    }

    public void setConfiguredEmbedModel(String configuredEmbedModel) {
        this.configuredEmbedModel = configuredEmbedModel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
