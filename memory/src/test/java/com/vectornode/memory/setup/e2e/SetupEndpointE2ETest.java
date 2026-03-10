package com.vectornode.memory.setup.e2e;

import com.vectornode.memory.entity.enums.LLMApiProvider;
import com.vectornode.memory.setup.dto.request.SetupRequest;
import com.vectornode.memory.setup.dto.response.SetupResponse;
import com.vectornode.memory.setup.service.SetupService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-End tests for the Setup endpoint.
 * These tests make REAL API calls and require a valid API key.
 * 
 * Configure via .env or environment variables:
 * LLM_PROVIDER — e.g. GEMINI, OPENAI, ANTHROPIC, AZURE, OPENROUTER
 * LLM_API_KEY — API key for the provider
 * LLM_CHAT_MODEL — Chat model name
 * LLM_EMBED_MODEL — Embedding model name
 * 
 * For backward compatibility, GEMINI_API_KEY / GEMINI_CHAT_MODEL /
 * GEMINI_EMBED_MODEL
 * are also supported as fallbacks (provider defaults to GEMINI in that case).
 */
class SetupEndpointE2ETest {

    private SetupService setupService;

    private static Map<String, String> envVars;
    private static String provider;
    private static String apiKey;
    private static String chatModel;
    private static String embedModel;
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

        System.out.println("=== E2E Test Environment ===");
        System.out.println("Environment loaded: " + envLoaded);
        System.out.println("Provider: " + provider);
        System.out.println("API Key present: " + (apiKey != null && !apiKey.isEmpty()));
        System.out.println("Chat Model: " + chatModel);
        System.out.println("Embed Model: " + embedModel);
        System.out.println("============================");
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

    @BeforeEach
    void setUp() {
        setupService = new SetupService();
    }

    private void assumeApiKeyPresent() {
        assumeTrue(apiKey != null && !apiKey.isEmpty(),
                "LLM_API_KEY (or GEMINI_API_KEY) not found in .env or environment variables. Skipping E2E test.");
    }

    /**
     * Helper method to create a SetupRequest with the configured provider and
     * models.
     */
    private SetupRequest createRequest() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.valueOf(provider.toUpperCase()));
        request.setApiKey(apiKey);
        request.setChatModelName(chatModel);
        request.setEmbedModelName(embedModel);
        return request;
    }

    @Test
    @DisplayName("E2E: Should successfully configure LLM provider and return success response")
    void shouldConfigureProviderSuccessfully() {
        assumeApiKeyPresent();

        // Arrange
        SetupRequest request = createRequest();

        // Act
        SetupResponse response = setupService.configureLLM(request);

        // Assert
        System.out.println("=== E2E Response ===");
        System.out.println("Response: " + response);
        System.out.println("====================");

        assertNotNull(response, "Response should not be null");
        assertTrue(response.isSuccess(), "Response should indicate success");
        assertEquals(provider.toUpperCase(), response.getConfiguredProvider(),
                "Provider should match configured provider");
        assertEquals(chatModel, response.getConfiguredChatModel(), "Chat model should match request");
        assertEquals(embedModel, response.getConfiguredEmbedModel(), "Embed model should match request");
        assertNotNull(response.getBaseUrl(), "Base URL should be set");
        assertNotNull(response.getTimestamp(), "Timestamp should be set");
        assertNotNull(response.getMessage(), "Message should be set");

        assertTrue(response.getMessage().contains("Backend is ready"),
                "Success message should indicate backend is ready");
    }

    @Test
    @DisplayName("E2E: Should throw exception for invalid API key")
    void shouldThrowExceptionForInvalidApiKey() {
        // Arrange - use an invalid API key
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.valueOf(provider.toUpperCase()));
        request.setApiKey("invalid-api-key-12345");
        request.setChatModelName(chatModel);
        request.setEmbedModelName(embedModel);

        // Act & Assert - should throw an exception
        Exception exception = assertThrows(Exception.class, () -> {
            setupService.configureLLM(request);
        });

        System.out.println("=== E2E Invalid Key Response ===");
        System.out.println("Exception: " + exception.getMessage());
        System.out.println("================================");

        assertNotNull(exception.getMessage(), "Exception should have a message");
    }

    @Test
    @DisplayName("E2E: Should throw exception for null provider")
    void shouldThrowExceptionForNullProvider() {
        // Arrange - missing provider
        SetupRequest request = new SetupRequest();
        request.setProvider(null);
        request.setApiKey(apiKey);
        request.setChatModelName(chatModel);
        request.setEmbedModelName(embedModel);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> setupService.configureLLM(request));

        System.out.println("=== E2E Null Provider Response ===");
        System.out.println("Exception: " + exception.getMessage());
        System.out.println("==================================");

        assertEquals("Provider is required", exception.getMessage());
    }

    @Test
    @DisplayName("E2E: Should configure with custom base URL")
    void shouldConfigureWithCustomBaseUrl() {
        assumeApiKeyPresent();

        // Arrange
        SetupRequest request = createRequest();
        // Use a custom base URL appropriate for the provider
        String customUrl;
        if (provider.equalsIgnoreCase("OPENROUTER")) {
            customUrl = "https://openrouter.ai/api";
        } else if (provider.equalsIgnoreCase("AZURE")) {
            customUrl = "https://cortexdb.openai.azure.com/";
        } else if (provider.equalsIgnoreCase("GEMINI")) {
            customUrl = "https://generativelanguage.googleapis.com/v1beta/openai/";
        } else { // Default for other providers, e.g., OPENAI
            customUrl = "https://api.openai.com/v1/";
        }
        request.setBaseUrl(customUrl);

        // Act
        SetupResponse response = setupService.configureLLM(request);

        // Assert
        System.out.println("=== E2E Custom Base URL Response ===");
        System.out.println("Response: " + response);
        System.out.println("====================================");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(customUrl, response.getBaseUrl(),
                "Custom base URL should be preserved in response");
    }

    @Test
    @DisplayName("E2E: Response should contain all expected fields")
    void responseShouldContainAllExpectedFields() {
        assumeApiKeyPresent();

        // Arrange
        SetupRequest request = createRequest();

        // Act
        SetupResponse response = setupService.configureLLM(request);

        // Assert
        assertNotNull(response);

        // Verify all fields are present and valid
        assertAll("Response should contain all expected fields",
                () -> assertNotNull(response.getMessage(), "message should not be null"),
                () -> assertNotNull(response.getConfiguredProvider(), "configuredProvider should not be null"),
                () -> assertNotNull(response.getConfiguredChatModel(), "configuredChatModel should not be null"),
                () -> assertNotNull(response.getConfiguredEmbedModel(), "configuredEmbedModel should not be null"),
                () -> assertNotNull(response.getBaseUrl(), "baseUrl should not be null"),
                () -> assertNotNull(response.getTimestamp(), "timestamp should not be null"),
                () -> assertTrue(response.isSuccess(), "success should be true"));
    }

    @Test
    @DisplayName("E2E: Should use default base URL when not provided")
    void shouldUseDefaultBaseUrl() {
        assumeApiKeyPresent();

        // Arrange - no baseUrl set
        SetupRequest request = createRequest();

        // Act
        SetupResponse response = setupService.configureLLM(request);

        // Assert
        System.out.println("=== E2E Default Base URL Response ===");
        System.out.println("Base URL: " + response.getBaseUrl());
        System.out.println("=====================================");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getBaseUrl(), "Should have a default base URL");
    }
}
