package com.vectornode.memory.setup.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

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
}
