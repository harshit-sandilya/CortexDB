package com.vectornode.memory.setup.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Slf4j
public class LLMProvider {

    private static ChatClient.Builder chatClientBuilder;
    private static EmbeddingModel defaultEmbeddingModel;

    public LLMProvider(ChatClient.Builder builder, EmbeddingModel embeddingModel) {
        LLMProvider.chatClientBuilder = builder;
        LLMProvider.defaultEmbeddingModel = embeddingModel;
    }

    @PostConstruct
    public void init() {
        log.info("LLMProvider initialized (Dynamic instantiation disabled due to API mismatch)");
    }

    public static float[] getEmbedding(String text, String provider, String apiKey, String baseUrl, String model) {
        log.debug("Generating embedding via Provider request: {}", provider);
        // FIXME: Dynamic OpenAiApi instantiation removed due to constructor mismatch in Spring AI 1.1.2.
        // Falling back to the default injected model.
        
        try {
            if (defaultEmbeddingModel == null) {
                throw new IllegalStateException("Default EmbeddingModel not initialized");
            }
            return defaultEmbeddingModel.embed(text);
        } catch (Exception e) {
            log.error("Embedding generation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Embedding logic failed: " + e.getMessage());
        }
    }

    public static String callLLM(String prompt, String provider, String apiKey, String baseUrl, String model) {
        log.debug("Calling LLM via Provider request: {}", provider);
        
        try {
            if (chatClientBuilder == null) {
                throw new IllegalStateException("ChatClient.Builder not initialized");
            }
            // Use the injected builder (configured via application.properties)
            ChatResponse chatResponse = chatClientBuilder.build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            return chatResponse.getResult().getOutput().getText();

        } catch (Exception e) {
            log.error("LLM Call failed: {}", e.getMessage());
            throw new IllegalArgumentException("LLM Call failed: " + e.getMessage());
        }
    }
}


                