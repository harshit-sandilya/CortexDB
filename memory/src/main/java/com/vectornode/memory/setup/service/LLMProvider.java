package com.vectornode.memory.setup.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
@Slf4j
public class LLMProvider {

    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;

    @PostConstruct
    public void init() {
        log.info("LLMProvider initialized with models: {}, {}",
                embeddingModel.getClass().getSimpleName(),
                chatModel.getClass().getSimpleName());
    }

    public float[] getEmbedding(String text) {
        return this.embeddingModel.embed(text);
    }

    public String callLLM(String prompt) {
        return this.chatModel.call(prompt);
    }
}
