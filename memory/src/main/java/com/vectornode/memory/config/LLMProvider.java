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
// import org.springframework.ai.ollama.OllamaChatModel;
// import org.springframework.ai.ollama.OllamaEmbeddingModel;
// import org.springframework.ai.ollama.api.OllamaApi;
// import org.springframework.ai.ollama.api.OllamaOptions;
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
                case "OPENAI":
                    OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();

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
                case "GEMINI":
                    String geminiBaseUrl = (baseUrl == null || baseUrl.isBlank())
                            ? "https://generativelanguage.googleapis.com/v1beta/openai/"
                            : baseUrl;

                    OpenAiApi geminiApi = OpenAiApi.builder()
                            .baseUrl(geminiBaseUrl)
                            .apiKey(apiKey)
                            .build();

                    // Use embedModelName for embeddings (e.g., "gemini-embedding-001")
                    embeddingModel = new OpenAiEmbeddingModel(geminiApi, MetadataMode.EMBED,
                            OpenAiEmbeddingOptions.builder().model(embedModelName).build(),
                            RetryTemplate.builder().build());

                    // Use chatModelName for chat (e.g., "gemini-2.0-flash")
                    chatModel = OpenAiChatModel.builder()
                            .openAiApi(geminiApi)
                            .defaultOptions(OpenAiChatOptions.builder().model(chatModelName).build())
                            .retryTemplate(RetryTemplate.builder().build())
                            .observationRegistry(ObservationRegistry.NOOP)
                            .build();
                    break;
                case "AZURE":
                    OpenAIClientBuilder azClientBuilder = new OpenAIClientBuilder()
                            .endpoint(baseUrl)
                            .credential(new AzureKeyCredential(apiKey));

                    embeddingModel = new AzureOpenAiEmbeddingModel(azClientBuilder.buildClient(),
                            MetadataMode.EMBED,
                            AzureOpenAiEmbeddingOptions.builder().deploymentName(embedModelName).build(),
                            null);

                    chatModel = new AzureOpenAiChatModel(azClientBuilder,
                            AzureOpenAiChatOptions.builder().deploymentName(chatModelName).build(),
                            null,
                            ObservationRegistry.NOOP);
                    break;
                /*
                 * case "OLLAMA":
                 * OllamaApi ollamaApi = new OllamaApi(baseUrl);
                 * embeddingModel = new OllamaEmbeddingModel(ollamaApi,
                 * OllamaOptions.builder().model(embedModelName).build());
                 * chatModel = new OllamaChatModel(ollamaApi,
                 * OllamaOptions.builder().model(chatModelName).build());
                 * break;
                 */
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
