package com.vectornode.memory.setup.service;

import com.vectornode.memory.setup.dto.request.SetupRequest;
import com.vectornode.memory.setup.dto.response.SetupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class SetupService {

    public SetupResponse configureLLM(SetupRequest request) {
        log.info("Received runtime setup request for Provider: {}", request.getProvider());

        if (request.getProvider() == null) {
            throw new IllegalArgumentException("Provider is required");
        }

        // Determine Base URL
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
                    effectiveBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/";
                    break;
                default:
                    effectiveBaseUrl = "N/A";
            }
        }

        // Initialize LLMProvider with SEPARATE chat and embedding models
        log.info("Initializing LLMProvider with chatModel: {} and embedModel: {}...",
                request.getChatModelName(), request.getEmbedModelName());
        new LLMProvider(
                request.getProvider().name(),
                request.getApiKey(),
                effectiveBaseUrl,
                request.getChatModelName(),
                request.getEmbedModelName());

        // Test embedding
        log.info("Testing embedding model...");
        LLMProvider.getEmbedding("test");
        log.info("Embedding test successful.");

        // Test LLM call
        log.info("Testing LLM call...");
        LLMProvider.callLLM("Hello");
        log.info("LLM call test successful.");

        return SetupResponse.builder()
                .message("Setup params validated and probed successfully. Backend is ready.")
                .success(true)
                .configuredProvider(request.getProvider().name())
                .configuredChatModel(request.getChatModelName())
                .configuredEmbedModel(request.getEmbedModelName())
                .baseUrl(effectiveBaseUrl)
                .timestamp(Instant.now())
                .build();
    }
}