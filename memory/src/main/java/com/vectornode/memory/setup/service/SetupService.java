package com.vectornode.memory.setup.service;

import com.vectornode.memory.entity.SetupConfiguration;
import com.vectornode.memory.setup.dto.request.SetupRequest;
import com.vectornode.memory.setup.dto.response.SetupResponse;
import com.vectornode.memory.setup.repository.SetupRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SetupService {

    private final SetupRepository setupRepository;

    // Injected by Spring (provided by the Devs via starter dependencies/properties)
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;

    @PostConstruct
    public void init() {
        log.info("Initialized SetupService with injected models: EmbeddingModel={}, ChatModel={}",
                embeddingModel.getClass().getSimpleName(),
                chatModel.getClass().getSimpleName());

        // If a config exists in DB, we could log it, but in framework mode, we trust
        // the injected beans.
        Optional<SetupConfiguration> activeConfig = setupRepository.findByIsActiveTrue();
        if (activeConfig.isEmpty()) {
            log.info("No active SetupConfiguration in DB. Using defaults provided by Spring Boot Application.");
        }
    }

    // No manual initModels required as we use Injected Bean

    // Public API for other services
    public float[] getEmbedding(String text) {
        return this.embeddingModel.embed(text);
    }

    public String callLLM(String prompt) {
        return this.chatModel.call(prompt);
    }

    @Transactional
    public SetupResponse configureLLM(SetupRequest request) {
        // Since we are in Framework mode using injected beans, this runtime config
        // will save to DB but NOT apply hot-reload unless we implement complex bean
        // refreshing.
        // For now, we save it as a record or for potential future use-cases.

        List<SetupConfiguration> activeConfigs = setupRepository.findAll();
        activeConfigs.forEach(c -> c.setActive(false));
        setupRepository.saveAll(activeConfigs);

        SetupConfiguration config = SetupConfiguration.builder()
                .provider(request.getProvider())
                .apiKey(request.getApiKey())
                .modelName(request.getModelName())
                .baseUrl(request.getBaseUrl())
                .isActive(true)
                .build();

        setupRepository.save(config);

        return SetupResponse.builder()
                .message(
                        "Configuration saved successfully. Note: Injected beans are currently active based on application.properties.")
                .success(true)
                .configuredProvider(config.getProvider().name())
                .configuredModel(config.getModelName())
                .timestamp(Instant.now())
                .build();
    }
}
