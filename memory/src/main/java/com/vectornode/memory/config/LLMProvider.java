package com.vectornode.memory.config;

import lombok.extern.slf4j.Slf4j;
import com.vectornode.memory.setup.exception.custom.LlmAuthenticationException;
import com.vectornode.memory.setup.exception.custom.LlmProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.retry.support.RetryTemplate;

@Slf4j
public class LLMProvider {

    private static ChatClient chatClient;
    private static EmbeddingModel embeddingModel;

    /**
     * Initialize LLMProvider with separate chat and embedding models.
     * 
     * @param provider
     * @param apiKey
     * @param baseUrl
     * @param chatModelName
     * @param embedModelName
     */
    public LLMProvider(String provider, String apiKey, String baseUrl, String chatModelName, String embedModelName) {
        log.info("Initializing LLMProvider with provider: {}, chatModel: {}, embedModel: {}, baseUrl: {}",
                provider, chatModelName, embedModelName, baseUrl);

        try {
            ChatModel chatModel;

            switch (provider.toUpperCase()) {
                case "GEMINI":
                    String geminiBaseUrl = (baseUrl == null || baseUrl.isBlank())
                            ? "https://generativelanguage.googleapis.com/v1beta/openai/"
                            : baseUrl;

                    OpenAiApi geminiApi = OpenAiApi.builder()
                            .baseUrl(geminiBaseUrl)
                            .apiKey(apiKey)
                            .build();

                    embeddingModel = new OpenAiEmbeddingModel(geminiApi, MetadataMode.EMBED,
                            OpenAiEmbeddingOptions.builder()
                                    .model(embedModelName)
                                    .dimensions(768)
                                    .build(),
                            RetryTemplate.builder().build());

                    chatModel = OpenAiChatModel.builder()
                            .openAiApi(geminiApi)
                            .defaultOptions(OpenAiChatOptions.builder().model(chatModelName).build())
                            .retryTemplate(RetryTemplate.builder().build())
                            .observationRegistry(ObservationRegistry.NOOP)
                            .build();
                    break;
                case "OPENAI":
                    String openaiBaseUrl = (baseUrl == null || baseUrl.isBlank())
                            ? "https://api.openai.com/"
                            : baseUrl;
                    OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(openaiBaseUrl).apiKey(apiKey).build();

                    embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                            OpenAiEmbeddingOptions.builder().model(embedModelName).build(),
                            RetryTemplate.builder().build());

                    chatModel = OpenAiChatModel.builder()
                            .openAiApi(openAiApi)
                            .defaultOptions(OpenAiChatOptions.builder().model(chatModelName).build())
                            .retryTemplate(RetryTemplate.builder().build())
                            .observationRegistry(ObservationRegistry.NOOP)
                            .build();
                    break;
                case "ANTHROPIC":
                case "OPENROUTER":
                    // Both Anthropic and OpenRouter expose OpenAI-compatible APIs
                    String compatBaseUrl;
                    if (baseUrl != null && !baseUrl.isBlank()) {
                        compatBaseUrl = baseUrl;
                    } else if (provider.equalsIgnoreCase("ANTHROPIC")) {
                        compatBaseUrl = "https://api.anthropic.com";
                    } else {
                        compatBaseUrl = "https://openrouter.ai/api";
                    }
                    OpenAiApi compatApi = OpenAiApi.builder().baseUrl(compatBaseUrl).apiKey(apiKey).build();

                    embeddingModel = new OpenAiEmbeddingModel(compatApi, MetadataMode.EMBED,
                            OpenAiEmbeddingOptions.builder().model(embedModelName).build(),
                            RetryTemplate.builder().build());

                    chatModel = OpenAiChatModel.builder()
                            .openAiApi(compatApi)
                            .defaultOptions(OpenAiChatOptions.builder().model(chatModelName).build())
                            .retryTemplate(RetryTemplate.builder().build())
                            .observationRegistry(ObservationRegistry.NOOP)
                            .build();
                    break;
                case "AZURE":
                    String azureBaseUrl = (baseUrl == null || baseUrl.isBlank())
                            ? "https://cortexdb.openai.azure.com/"
                            : baseUrl;

                    OpenAIClientBuilder azClientBuilder = new OpenAIClientBuilder()
                            .endpoint(azureBaseUrl)
                            .credential(new AzureKeyCredential(apiKey));

                    embeddingModel = new AzureOpenAiEmbeddingModel(azClientBuilder.buildClient(),
                            MetadataMode.EMBED,
                            AzureOpenAiEmbeddingOptions.builder().deploymentName(embedModelName).build(),
                            ObservationRegistry.NOOP);

                    chatModel = AzureOpenAiChatModel.builder()
                            .openAIClientBuilder(azClientBuilder)
                            .defaultOptions(AzureOpenAiChatOptions.builder().deploymentName(chatModelName).build())
                            .observationRegistry(ObservationRegistry.NOOP)
                            .build();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported provider: " + provider);
            }

            // Build ChatClient
            chatClient = ChatClient.builder(chatModel).build();

            log.info("LLMProvider initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize LLMProvider: {}", e.getMessage());
            throw new IllegalStateException("LLMProvider initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy constructor for backward compatibility.
     * Uses the same model for both chat and embeddings.
     * 
     * @deprecated Use the constructor with separate chatModel and embedModel
     *             parameters.
     */
    @Deprecated
    public LLMProvider(String provider, String apiKey, String baseUrl, String model) {
        this(provider, apiKey, baseUrl, model, model);
    }

    public static float[] getEmbedding(String text) {
        log.debug("Generating embedding for text");

        try {
            if (embeddingModel == null) {
                throw new IllegalStateException("EmbeddingModel not initialized");
            }
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.error("Embedding generation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    public static String callLLM(String prompt) {
        log.debug("Calling LLM with prompt");

        try {
            if (chatClient == null) {
                throw new IllegalStateException("ChatClient not initialized");
            }

            ChatResponse chatResponse = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            return chatResponse.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("LLM Call failed: {}", e.getMessage());
            throw new IllegalArgumentException("LLM Call failed: " + e.getMessage(), e);
        }
    }
}
