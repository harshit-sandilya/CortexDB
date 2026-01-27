package com.vectornode.memory.infra;

import com.vectornode.memory.setup.service.LLMProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LLMProvider with Gemini API.
 * These tests require a valid GEMINI_API_KEY in the .env file.
 */
class LLMProviderTest {

    private static String apiKey;
    private static String chatModel;
    private static String embedModel;
    private static Map<String, String> envVars;
    private static boolean envLoaded = false;

    @BeforeAll
    static void loadEnvFile() {
        envVars = new HashMap<>();

        // Try to load .env file from project root
        Path envPath = Paths.get("").toAbsolutePath();

        // Navigate up to find .env file (handles running from different directories)
        while (envPath != null) {
            Path envFile = envPath.resolve(".env");
            if (Files.exists(envFile)) {
                try {
                    Files.lines(envFile).forEach(line -> {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                            int eqIndex = line.indexOf("=");
                            String key = line.substring(0, eqIndex).trim();
                            String value = line.substring(eqIndex + 1).trim();
                            envVars.put(key, value);
                        }
                    });
                    envLoaded = true;
                    break;
                } catch (IOException e) {
                    System.err.println("Failed to read .env file: " + e.getMessage());
                }
            }
            envPath = envPath.getParent();
        }

        // Fall back to system environment variables if .env not found
        if (!envLoaded) {
            String sysApiKey = System.getenv("GEMINI_API_KEY");
            String sysChatModel = System.getenv("GEMINI_CHAT_MODEL");
            String sysEmbedModel = System.getenv("GEMINI_EMBED_MODEL");

            if (sysApiKey != null) {
                envVars.put("GEMINI_API_KEY", sysApiKey);
                envVars.put("GEMINI_CHAT_MODEL", sysChatModel != null ? sysChatModel : "gemini-2.0-flash");
                envVars.put("GEMINI_EMBED_MODEL", sysEmbedModel != null ? sysEmbedModel : "text-embedding-004");
                envLoaded = true;
            }
        }

        apiKey = envVars.get("GEMINI_API_KEY");
        chatModel = envVars.getOrDefault("GEMINI_CHAT_MODEL", "gemini-2.0-flash");
        embedModel = envVars.getOrDefault("GEMINI_EMBED_MODEL", "text-embedding-004");

        System.out.println("Environment loaded: " + envLoaded);
        System.out.println("API Key present: " + (apiKey != null && !apiKey.isEmpty()));
        System.out.println("Chat Model: " + chatModel);
        System.out.println("Embed Model: " + embedModel);
    }

    @Test
    @DisplayName("Should load environment variables from .env file")
    void shouldLoadEnvVariables() {
        assertTrue(envLoaded, "Environment variables should be loaded from .env or system env");
        assertNotNull(apiKey, "GEMINI_API_KEY should not be null");
        assertFalse(apiKey.isEmpty(), "GEMINI_API_KEY should not be empty");
        assertNotNull(chatModel, "GEMINI_CHAT_MODEL should not be null");
        assertNotNull(embedModel, "GEMINI_EMBED_MODEL should not be null");
    }

    @Test
    @DisplayName("Should initialize LLMProvider with Gemini configuration using separate models")
    void shouldInitializeLLMProviderWithGemini() {
        assumeApiKeyPresent();

        // Constructor should not throw an exception - using new 5-parameter constructor
        assertDoesNotThrow(() -> {
            new LLMProvider("GEMINI", apiKey, null, chatModel, embedModel);
        }, "LLMProvider should initialize successfully with Gemini using separate chat and embed models");
    }

    @Test
    @DisplayName("Should initialize LLMProvider with custom base URL")
    void shouldInitializeLLMProviderWithCustomBaseUrl() {
        assumeApiKeyPresent();

        String customBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/";

        assertDoesNotThrow(() -> {
            new LLMProvider("GEMINI", apiKey, customBaseUrl, chatModel, embedModel);
        }, "LLMProvider should initialize with custom base URL");
    }

    @Test
    @DisplayName("Should throw exception for unsupported provider")
    void shouldThrowExceptionForUnsupportedProvider() {
        assumeApiKeyPresent();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new LLMProvider("UNSUPPORTED_PROVIDER", apiKey, null, chatModel, embedModel));

        assertTrue(exception.getMessage().contains("Unsupported provider"));
    }

    @Test
    @DisplayName("Should throw exception for null API key")
    void shouldThrowExceptionForNullApiKey() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new LLMProvider("GEMINI", null, null, chatModel, embedModel));

        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("Should generate embeddings for text")
    void shouldGenerateEmbeddingsForText() {
        assumeApiKeyPresent();

        // Initialize provider with SEPARATE chat and embedding models
        new LLMProvider("GEMINI", apiKey, null, chatModel, embedModel);

        String testText = "This is a test sentence for embedding generation.";

        float[] embedding = assertDoesNotThrow(
                () -> LLMProvider.getEmbedding(testText),
                "getEmbedding should not throw an exception");

        assertNotNull(embedding, "Embedding should not be null");
        assertTrue(embedding.length > 0, "Embedding should have dimensions");

        System.out.println("Embedding dimensions: " + embedding.length);
    }

    @Test
    @DisplayName("Should call LLM and get response")
    void shouldCallLLMAndGetResponse() {
        assumeApiKeyPresent();

        // Initialize provider with SEPARATE chat and embedding models
        new LLMProvider("GEMINI", apiKey, null, chatModel, embedModel);

        String prompt = "Reply with exactly one word: Hello";

        String response = assertDoesNotThrow(
                () -> LLMProvider.callLLM(prompt),
                "callLLM should not throw an exception");

        assertNotNull(response, "Response should not be null");
        assertFalse(response.isEmpty(), "Response should not be empty");

        System.out.println("LLM Response: " + response);
    }

    @Test
    @DisplayName("Should handle provider case insensitivity")
    void shouldHandleProviderCaseInsensitivity() {
        assumeApiKeyPresent();

        // Test lowercase
        assertDoesNotThrow(() -> {
            new LLMProvider("gemini", apiKey, null, chatModel, embedModel);
        }, "Provider should accept lowercase");

        // Test mixed case
        assertDoesNotThrow(() -> {
            new LLMProvider("Gemini", apiKey, null, chatModel, embedModel);
        }, "Provider should accept mixed case");
    }

    @Test
    @DisplayName("Should throw exception when calling getEmbedding before initialization")
    void shouldThrowExceptionWhenEmbeddingModelNotInitialized() throws Exception {
        // Use reflection to reset the static embeddingModel field to null
        java.lang.reflect.Field embeddingModelField = LLMProvider.class.getDeclaredField("embeddingModel");
        embeddingModelField.setAccessible(true);
        Object originalValue = embeddingModelField.get(null);

        try {
            // Set embeddingModel to null to simulate uninitialized state
            embeddingModelField.set(null, null);

            // Now calling getEmbedding should throw IllegalArgumentException (wraps
            // IllegalStateException)
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> LLMProvider.getEmbedding("test text"));

            assertTrue(exception.getMessage().contains("EmbeddingModel not initialized"),
                    "Exception message should indicate EmbeddingModel not initialized");
        } finally {
            // Restore original value to not affect other tests
            embeddingModelField.set(null, originalValue);
        }
    }

    @Test
    @DisplayName("Should throw exception when calling callLLM before initialization")
    void shouldThrowExceptionWhenChatClientNotInitialized() throws Exception {
        // Use reflection to reset the static chatClient field to null
        java.lang.reflect.Field chatClientField = LLMProvider.class.getDeclaredField("chatClient");
        chatClientField.setAccessible(true);
        Object originalValue = chatClientField.get(null);

        try {
            // Set chatClient to null to simulate uninitialized state
            chatClientField.set(null, null);

            // Now calling callLLM should throw IllegalArgumentException (wraps
            // IllegalStateException)
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> LLMProvider.callLLM("Hello"));

            assertTrue(exception.getMessage().contains("ChatClient not initialized"),
                    "Exception message should indicate ChatClient not initialized");
        } finally {
            // Restore original value to not affect other tests
            chatClientField.set(null, originalValue);
        }
    }

    @Test
    @DisplayName("Provider constants should be valid")
    void providerConstantsShouldBeValid() {
        // Test valid provider strings
        String[] validProviders = { "OPENAI", "GEMINI", "AZURE" };

        for (String provider : validProviders) {
            assertNotNull(provider);
            assertFalse(provider.isEmpty());
        }
    }

    @Test
    @DisplayName("Should use legacy 4-parameter constructor for backward compatibility")
    void shouldUseLegacyConstructorForBackwardCompatibility() {
        assumeApiKeyPresent();

        // The 4-parameter constructor should still work (uses same model for both)
        assertDoesNotThrow(() -> {
            new LLMProvider("GEMINI", apiKey, null, embedModel);
        }, "Legacy 4-parameter constructor should still work");
    }

    // Helper method to skip tests when API key is not available
    private void assumeApiKeyPresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                apiKey != null && !apiKey.isEmpty(),
                "Skipping test: GEMINI_API_KEY not found in .env or environment");
    }
}
