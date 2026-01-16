package com.vectornode.memory.setup.service;

import com.vectornode.memory.setup.config.SetupConfiguration;
import com.vectornode.memory.setup.dto.request.SetupRequest;
import com.vectornode.memory.setup.dto.response.SetupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// No Direct @Service annotation (created via @Bean in SetupConfiguration)

import java.time.Instant;

@RequiredArgsConstructor
@Slf4j
public class SetupService {

    // Injecting the Configuration (which acts as the middle-layer provider wrapper)
    private final SetupConfiguration setupConfiguration;

    // Runtime configuration endpoint (Validation & Echo)
    public SetupResponse configureLLM(SetupRequest request) {
        log.info("Received runtime setup request for Provider: {}", request.getProvider());

        if (request.getProvider() == null) {
            throw new IllegalArgumentException("Provider is required");
        }

        // 2. Dynamic Embedding Verification (The "Probe")
        try {
            log.info("Probing embedding model for verification...");
            // Delegating probe to the chain via Configuration -> LLMProvider
            this.setupConfiguration.getEmbedding("Verification Probe");
            log.info("Probe successful.");
        } catch (Exception e) {
            log.error("Embedding probe failed: {}", e.getMessage());
            throw new IllegalArgumentException("Embedding verification failed: " + e.getMessage());
        }

        // 3. Determine Base URL
        String effectiveBaseUrl = request.getBaseUrl();
        if (effectiveBaseUrl == null || effectiveBaseUrl.isBlank()) {
            switch (request.getProvider()) {
                case OLLAMA:
                    effectiveBaseUrl = "http://localhost:11434";
                    break;
                case OPENAI:
                    effectiveBaseUrl = "https://api.openai.com";
                    break;
                case AZURE:
                    effectiveBaseUrl = "https://api.azure.com";
                    break;
                case MISTRAL:
                    effectiveBaseUrl = "https://api.mistral.com";
                    break;
                case GEMINI:
                    effectiveBaseUrl = "https://api.gemini.com";
                    break;
                default:
                    effectiveBaseUrl = "N/A";
            }
        }

        return SetupResponse.builder()
                .message("Setup params validated and probed successfully. Backend is ready.")
                .success(true)
                .configuredProvider(request.getProvider().name())
                .configuredModel(request.getModelName())
                .baseUrl(effectiveBaseUrl)
                .timestamp(Instant.now())
                .build();
    }
}
