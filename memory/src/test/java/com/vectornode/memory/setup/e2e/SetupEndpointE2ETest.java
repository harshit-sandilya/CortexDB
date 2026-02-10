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
 * These tests make REAL API calls to Gemini and require a valid API key.
 * 
 * This test class directly tests the SetupService without loading the full
 * Spring context, avoiding the ErrorMvcAutoConfiguration conflict while still
 * making real API calls.
 */
class SetupEndpointE2ETest {

    private SetupService setupService;

    private static Map<String, String> envVars;
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

        // Fall back to system environment variables if .env not found
        if (!envLoaded) {
            String sysApiKey = System.getenv("GEMINI_API_KEY");
            String sysChatModel = System.getenv("GEMINI_CHAT_MODEL");
            String sysEmbedModel = System.getenv("GEMINI_EMBED_MODEL");

            if (sysApiKey != null) {
                envVars.put("GEMINI_API_KEY", sysApiKey);
                envVars.put("GEMINI_CHAT_MODEL", sysChatModel != null ? sysChatModel : "gemini-2.0-flash");
                envVars.put("GEMINI_EMBED_MODEL", sysEmbedModel != null ? sysEmbedModel : "gemini-embedding-001");
                envLoaded = true;
            }
        }

        apiKey = envVars.get("GEMINI_API_KEY");
        chatModel = envVars.getOrDefault("GEMINI_CHAT_MODEL", "gemini-2.0-flash");
        embedModel = envVars.getOrDefault("GEMINI_EMBED_MODEL", "gemini-embedding-001");

        System.out.println("=== E2E Test Environment ===");
        System.out.println("Environment loaded: " + envLoaded);
        System.out.println("API Key present: " + (apiKey != null && !apiKey.isEmpty()));
        System.out.println("Chat Model: " + chatModel);
        System.out.println("Embed Model: " + embedModel);
        System.out.println("============================");
    }

    @BeforeEach
    void setUp() {
        setupService = new SetupService();
    }

    private void assumeApiKeyPresent() {
        assumeTrue(apiKey != null && !apiKey.isEmpty(),
                "GEMINI_API_KEY not found in .env or environment variables. Skipping E2E test.");
    }

    /**
     * Helper method to create a SetupRequest with proper chat and embed models
     */
    private SetupRequest createGeminiRequest() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.GEMINI);
        request.setApiKey(apiKey);
        request.setChatModelName(chatModel);
        request.setEmbedModelName(embedModel);
        return request;
    }

    @Test
    @DisplayName("E2E: Should successfully configure Gemini provider and return success response")
    void shouldConfigureGeminiProviderSuccessfully() {
        assumeApiKeyPresent();

        // Arrange - use separate chat and embed models
        SetupRequest request = createGeminiRequest();

        // Act
        SetupResponse response = setupService.configureLLM(request);

        // Assert
        System.out.println("=== E2E Response ===");
        System.out.println("Response: " + response);
        System.out.println("====================");

        assertNotNull(response, "Response should not be null");
        assertTrue(response.isSuccess(), "Response should indicate success");
        assertEquals("GEMINI", response.getConfiguredProvider(), "Provider should be GEMINI");
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
        request.setProvider(LLMApiProvider.GEMINI);
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
        SetupRequest request = createGeminiRequest();
        request.setBaseUrl("https://generativelanguage.googleapis.com/v1beta/openai/");

        // Act
        SetupResponse response = setupService.configureLLM(request);

        // Assert
        System.out.println("=== E2E Custom Base URL Response ===");
        System.out.println("Response: " + response);
        System.out.println("====================================");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/",
                response.getBaseUrl(),
                "Custom base URL should be preserved in response");
    }

    @Test
    @DisplayName("E2E: Response should contain all expected fields")
    void responseShouldContainAllExpectedFields() {
        assumeApiKeyPresent();

        // Arrange
        SetupRequest request = createGeminiRequest();

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
    @DisplayName("E2E: Should use default base URL for Gemini when not provided")
    void shouldUseDefaultBaseUrlForGemini() {
        assumeApiKeyPresent();

        // Arrange - no baseUrl set
        SetupRequest request = createGeminiRequest();

        // Act
        SetupResponse response = setupService.configureLLM(request);

        // Assert
        System.out.println("=== E2E Default Base URL Response ===");
        System.out.println("Base URL: " + response.getBaseUrl());
        System.out.println("=====================================");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/", response.getBaseUrl(),
                "Should use default Gemini base URL");
    }
}
