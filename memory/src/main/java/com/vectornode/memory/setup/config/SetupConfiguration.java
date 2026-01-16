package com.vectornode.memory.setup.config;

import com.vectornode.memory.setup.service.LLMProvider;
import com.vectornode.memory.setup.service.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SetupConfiguration {

    private final LLMProvider llmProvider;

    public float[] getEmbedding(String text) {
        return llmProvider.getEmbedding(text);
    }

    public String callLLM(String prompt) {
        return llmProvider.callLLM(prompt);
    }

    @Bean
    public SetupService setupService() {
        return new SetupService(this);
    }
}
