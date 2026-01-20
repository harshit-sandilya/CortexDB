package com.vectornode.memory.setup.service;

import lombok.extern.slf4j.Slf4j;
import com.vectornode.memory.setup.exception.custom.LlmAuthenticationException;
import com.vectornode.memory.setup.exception.custom.LlmProviderException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaChatOptions;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import io.micrometer.observation.ObservationRegistry;

@Slf4j
public class LLMProvider {

    private static ChatClient chatClient;
    private static EmbeddingModel embeddingModel;

    public LLMProvider(String provider, String apiKey, String baseUrl, String model) {
        log.info("Initializing LLMProvider with provider: {}, model: {}, baseUrl: {}", provider, model, baseUrl);

        try {
            ChatModel chatModel;

            switch (provider.toUpperCase()) {
                case "OPENAI":
                    OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
                    embeddingModel = OpenAiEmbeddingModel.builder().openAiApi(openAiApi)
                            .options(OpenAiEmbeddingOptions.builder().model(model).build()).build();
                    chatModel = OpenAiChatModel.builder().openAiApi(openAiApi)
                            .defaultOptions(OpenAiChatOptions.builder().model(model).build()).build();
                    break;
                case "GEMINI":
                    String geminiBaseUrl = baseUrl;
                    if (geminiBaseUrl == null || geminiBaseUrl.isBlank()) {
                        geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";
                        OpenAiApi geminiApi = OpenAiApi.builder().apiKey(apiKey).baseUrl(geminiBaseUrl).build();
                        embeddingModel = new OpenAiApi.EmbeddingModel(geminiApi,
                                OpenAiEmbeddingOptions.builder().model(model).build());
                        chatModel = new OpenAiApi.ChatModel(geminiApi,
                                OpenAiChatOptions.builder().model(model).build());
                    }
                    break;
                case "AZURE":
                    OpenAIClientBuilder azClientBuilder = new OpenAIClientBuilder().endpoint(baseUrl)
                            .credential(new AzureKeyCredential(apiKey));
                    embeddingModel = new AzureOpenAiEmbeddingModel(azureClientBuilder.buildClient(),
                            AzureOpenAiEmbeddingOptions.builder().deployementName(model).build());
                    chatModel = new AzureOpenAiChatModel(azureClientBuilder,
                            AzureOpenAiChatOptions.builder().deployementName(model).build(), null,
                            ObservationRegistry.NOOP);
                    break;
                case "OLLAMA":
                    OllamaApi ollamaApi = new OllamaApi(baseUrl);
                    embeddingModel = new OllamaEmbeddingModel(ollamaApi,
                            OllamaEmbeddingOptions.builder().model(model).build());
                    chatModel = new OllamaChatModel(ollamaApi, OllamaChatOptions.builder().model(model).build());
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
