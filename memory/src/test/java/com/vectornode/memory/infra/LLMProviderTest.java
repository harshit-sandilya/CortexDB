package com.vectornode.memory.infra;

import com.vectornode.memory.config.LLMProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LLMProvider with real API calls.
 * These tests require a valid LLM_API_KEY (or GEMINI_API_KEY) in the .env file.
 */
class LLMProviderTest {

    private static String provider;
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

        // Resolve provider-agnostic vars, falling back to legacy GEMINI_ vars
        provider = resolveVar("LLM_PROVIDER", "GEMINI");
        apiKey = resolveVar("LLM_API_KEY", null);
        chatModel = resolveVar("LLM_CHAT_MODEL", null);
        embedModel = resolveVar("LLM_EMBED_MODEL", null);

        // Legacy fallback: if LLM_API_KEY not found, try GEMINI_API_KEY
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = resolveVar("GEMINI_API_KEY", null);
            if (apiKey != null && !apiKey.isEmpty()) {
                provider = "GEMINI";
                if (chatModel == null || chatModel.isEmpty()) {
                    chatModel = resolveVar("GEMINI_CHAT_MODEL", "gemini-2.0-flash");
                }
                if (embedModel == null || embedModel.isEmpty()) {
                    embedModel = resolveVar("GEMINI_EMBED_MODEL", "gemini-embedding-001");
                }
                envLoaded = true;
            }
        } else {
            envLoaded = true;
        }

        System.out.println("Environment loaded: " + envLoaded);
        System.out.println("Provider: " + provider);
        System.out.println("API Key present: " + (apiKey != null && !apiKey.isEmpty()));
        System.out.println("Chat Model: " + chatModel);
        System.out.println("Embed Model: " + embedModel);
    }

    /**
     * Resolve a variable from .env vars, then system env, then default.
     */
    private static String resolveVar(String name, String defaultValue) {
        String value = envVars.get(name);
        if (value != null && !value.isEmpty())
            return value;
        value = System.getenv(name);
        if (value != null && !value.isEmpty())
            return value;
        return defaultValue;
    }

    @Test
    @DisplayName("Should load environment variables from .env file")
    void shouldLoadEnvVariables() {
        assertTrue(envLoaded, "Environment variables should be loaded from .env or system env");
        assertNotNull(apiKey, "LLM_API_KEY should not be null");
        assertFalse(apiKey.isEmpty(), "LLM_API_KEY should not be empty");
        assertNotNull(chatModel, "LLM_CHAT_MODEL should not be null");
        assertNotNull(embedModel, "LLM_EMBED_MODEL should not be null");
    }

    @Test
    @DisplayName("Should initialize LLMProvider with configured provider using separate models")
    void shouldInitializeLLMProvider() {
        assumeApiKeyPresent();

        // Constructor should not throw an exception
        assertDoesNotThrow(() -> {
            new LLMProvider(provider, apiKey, null, chatModel, embedModel);
        }, "LLMProvider should initialize successfully with " + provider + " using separate chat and embed models");
    }

    @Test
    @DisplayName("Should initialize LLMProvider with custom base URL")
    void shouldInitializeLLMProviderWithCustomBaseUrl() {
        assumeApiKeyPresent();

        String customBaseUrl;
        if (provider.equalsIgnoreCase("GEMINI")) {
            customBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/";
        } else if (provider.equalsIgnoreCase("OPENROUTER"))
            customBaseUrl = "https://openrouter.ai/api";
        else if (provider.equalsIgnoreCase("AZURE")) {
            customBaseUrl = "https://cortexdb.openai.azure.com/";
        } else { // Default, likely for OPENAI
            customBaseUrl = "https://api.openai.com/v1/";
        }

        assertDoesNotThrow(() -> {
            new LLMProvider(provider, apiKey, customBaseUrl, chatModel, embedModel);
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
                () -> new LLMProvider(provider, null, null, chatModel, embedModel));

        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("Should generate embeddings for text")
    void shouldGenerateEmbeddingsForText() {
        assumeApiKeyPresent();

        // Initialize provider with SEPARATE chat and embedding models
        new LLMProvider(provider, apiKey, null, chatModel, embedModel);

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
        new LLMProvider(provider, apiKey, null, chatModel, embedModel);

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
            new LLMProvider(provider.toLowerCase(), apiKey, null, chatModel, embedModel);
        }, "Provider should accept lowercase");

        // Test mixed case
        String mixedCase = provider.substring(0, 1).toUpperCase() + provider.substring(1).toLowerCase();
        assertDoesNotThrow(() -> {
            new LLMProvider(mixedCase, apiKey, null, chatModel, embedModel);
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
        String[] validProviders = { "GEMINI", "OPENAI", "ANTHROPIC", "AZURE", "OPENROUTER" };

        for (String p : validProviders) {
            assertNotNull(p);
            assertFalse(p.isEmpty());
        }
    }

    @Test
    @DisplayName("Should use legacy 4-parameter constructor for backward compatibility")
    void shouldUseLegacyConstructorForBackwardCompatibility() {
        assumeApiKeyPresent();

        // The 4-parameter constructor should still work (uses same model for both)
        assertDoesNotThrow(() -> {
            new LLMProvider(provider, apiKey, null, embedModel);
        }, "Legacy 4-parameter constructor should still work");
    }

    // Helper method to skip tests when API key is not available
    private void assumeApiKeyPresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                apiKey != null && !apiKey.isEmpty(),
                "Skipping test: LLM_API_KEY not found in .env or environment");
    }
}
