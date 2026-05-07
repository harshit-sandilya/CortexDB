package com.vectornode.memory.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class LLMConfig {

    @Bean
    public LLMProvider llmProvider() {
        log.info("Initializing LLMProvider...");

        // Use environment variables or defaults
        String provider = System.getenv("LLM_PROVIDER");
        String apiKey = System.getenv("LLM_API_KEY");
        String chatModel = System.getenv("LLM_CHAT_MODEL");
        String embedModel = System.getenv("LLM_EMBED_MODEL");
        String baseUrl = System.getenv("LLM_BASE_URL");

        // Set defaults if not specified
        if (provider == null) provider = "GEMINI";
        if (chatModel == null) chatModel = "gemini-2.0-flash";
        if (embedModel == null) embedModel = "gemini-embedding-001";

        log.info("LLMProvider initialized with provider: {}, chatModel: {}, embedModel: {}",
                provider, chatModel, embedModel);

        if (apiKey == null || apiKey.isBlank() || apiKey.trim().equals("dummy")) {
            log.warn("LLM_API_KEY not configured - LLM functionality will be limited");
        }

        return new LLMProvider(provider, apiKey, baseUrl, chatModel, embedModel);
    }
}