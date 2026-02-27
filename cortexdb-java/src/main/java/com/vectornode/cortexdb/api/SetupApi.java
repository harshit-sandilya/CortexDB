package com.vectornode.cortexdb.api;

import com.vectornode.cortexdb.config.HttpClientWrapper;
import com.vectornode.cortexdb.models.LLMApiProvider;
import com.vectornode.cortexdb.models.SetupRequest;
import com.vectornode.cortexdb.models.SetupResponse;

/**
 * Wraps the {@code /api/setup} endpoint.
 */
public class SetupApi {

    private final HttpClientWrapper http;

    public SetupApi(HttpClientWrapper http) {
        this.http = http;
    }

    /**
     * Configure the LLM provider on the CortexDB server.
     *
     * @param provider   LLM provider (e.g. GEMINI, OPENAI, AZURE).
     * @param apiKey     API key for the provider.
     * @param chatModel  Name of the chat model.
     * @param embedModel Name of the embedding model.
     * @return SetupResponse with configuration details.
     */
    public SetupResponse configure(LLMApiProvider provider, String apiKey,
            String chatModel, String embedModel) {
        return configure(provider, apiKey, chatModel, embedModel, null);
    }

    /**
     * Configure the LLM provider on the CortexDB server.
     *
     * @param provider   LLM provider (e.g. GEMINI, OPENAI, AZURE).
     * @param apiKey     API key for the provider.
     * @param chatModel  Name of the chat model.
     * @param embedModel Name of the embedding model.
     * @param baseUrl    Custom base URL (optional, mainly for Azure/Ollama).
     * @return SetupResponse with configuration details.
     */
    public SetupResponse configure(LLMApiProvider provider, String apiKey,
            String chatModel, String embedModel,
            String baseUrl) {
        SetupRequest request = new SetupRequest(provider, apiKey,
                chatModel, embedModel, baseUrl);
        return http.post("/api/setup", request, SetupResponse.class);
    }

    /**
     * Configure using a string provider name (convenience overload).
     */
    public SetupResponse configure(String provider, String apiKey,
            String chatModel, String embedModel) {
        return configure(LLMApiProvider.valueOf(provider.toUpperCase()),
                apiKey, chatModel, embedModel);
    }
}
