package com.vectornode.memory.setup.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetupRequest {
    public enum Provider {
        OPENAI, AZURE, OLLAMA
    }

    @NotNull
    private Provider provider;

    private String apiKey;

    @NotNull
    private String modelName;

    private String baseUrl;
}
