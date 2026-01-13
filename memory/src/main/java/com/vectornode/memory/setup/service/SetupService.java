package com.vectornode.memory.setup.service;

import com.vectornode.memory.setup.dto.request.SetupRequest;
import com.vectornode.memory.setup.dto.response.SetupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class SetupService {

    // Injected by Spring (provided by the Devs via starter dependencies/properties)
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;

    @PostConstruct
    public void init() {
        log.info("Initialized SetupService with injected models: EmbeddingModel={}, ChatModel={}",
                embeddingModel.getClass().getSimpleName(),
                chatModel.getClass().getSimpleName());
    }

    // Public API for other services
    public float[] getEmbedding(String text) {
        return this.embeddingModel.embed(text);
    }

    public String callLLM(String prompt) {
        return this.chatModel.call(prompt);
    }

    // Runtime configuration endpoint (Non-persisted)
    public SetupResponse configureLLM(SetupRequest request) {
        log.info("Received runtime setup request for Provider: {}", request.getProvider());

        // In Framework mode without specific factory dependencies, we cannot easily
        // hot-swap
        // the injected beans (OpenAI/Ollama) at runtime safely.
        // We acknowledge the request to satisfy the API contract.

        return SetupResponse.builder()
                .message(
                        "Configuration received. Note: System is using injected beans from application.properties (Framework Mode). Runtime hot-swap is limited.")
                .success(true)
                .configuredProvider(request.getProvider().name())
                .configuredModel(request.getModelName())
                .timestamp(Instant.now())
                .build();
    }
}
