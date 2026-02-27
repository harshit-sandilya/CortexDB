package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to configure the LLM provider on the CortexDB server.
 * Mirrors the backend {@code SetupRequest} DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SetupRequest {

    private LLMApiProvider provider;
    private String apiKey;
    private String chatModelName;
    private String embedModelName;
    private String baseUrl;

    public SetupRequest() {
    }

    public SetupRequest(LLMApiProvider provider, String apiKey,
            String chatModelName, String embedModelName,
            String baseUrl) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.chatModelName = chatModelName;
        this.embedModelName = embedModelName;
        this.baseUrl = baseUrl;
    }

    // ── Getters & Setters ────────────────────────────────────────

    public LLMApiProvider getProvider() {
        return provider;
    }

    public void setProvider(LLMApiProvider provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChatModelName() {
        return chatModelName;
    }

    public void setChatModelName(String chatModelName) {
        this.chatModelName = chatModelName;
    }

    public String getEmbedModelName() {
        return embedModelName;
    }

    public void setEmbedModelName(String embedModelName) {
        this.embedModelName = embedModelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
