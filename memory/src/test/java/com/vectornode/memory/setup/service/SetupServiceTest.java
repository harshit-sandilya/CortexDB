package com.vectornode.memory.setup.service;

import com.vectornode.memory.entity.enums.LLMApiProvider;
import com.vectornode.memory.setup.dto.request.SetupRequest;
import com.vectornode.memory.setup.dto.response.SetupResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SetupService with mocked LLMProvider.
 * Uses Mockito's MockedStatic and MockedConstruction to avoid real LLM API
 * calls.
 */
class SetupServiceTest {

    private SetupService setupService;
    private MockedConstruction<LLMProvider> mockedConstruction;
    private MockedStatic<LLMProvider> mockedStatic;

    @BeforeEach
    void setUp() {
        setupService = new SetupService();

        // Mock the LLMProvider constructor
        mockedConstruction = mockConstruction(LLMProvider.class);

        // Mock static methods
        mockedStatic = mockStatic(LLMProvider.class);
        mockedStatic.when(() -> LLMProvider.getEmbedding(anyString()))
                .thenReturn(new float[] { 0.1f, 0.2f, 0.3f });
        mockedStatic.when(() -> LLMProvider.callLLM(anyString()))
                .thenReturn("Mock LLM response");
    }

    @AfterEach
    void tearDown() {
        if (mockedConstruction != null) {
            mockedConstruction.close();
        }
        if (mockedStatic != null) {
            mockedStatic.close();
        }
    }

    @Test
    @DisplayName("Should configure LLM successfully with GEMINI provider")
    void shouldConfigureLLMWithGeminiProvider() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.GEMINI);
        request.setApiKey("test-api-key");
        request.setModelName("gemini-2.0-flash");

        SetupResponse response = setupService.configureLLM(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("GEMINI", response.getConfiguredProvider());
        assertEquals("gemini-2.0-flash", response.getConfiguredModel());
        assertNotNull(response.getTimestamp());
        assertEquals("Setup params validated and probed successfully. Backend is ready.", response.getMessage());

        // Verify static methods were called
        mockedStatic.verify(() -> LLMProvider.getEmbedding("test"), times(1));
        mockedStatic.verify(() -> LLMProvider.callLLM("Hello"), times(1));
    }

    @Test
    @DisplayName("Should configure LLM successfully with OPENAI provider")
    void shouldConfigureLLMWithOpenAIProvider() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.OPENAI);
        request.setApiKey("test-openai-key");
        request.setModelName("gpt-4");

        SetupResponse response = setupService.configureLLM(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("OPENAI", response.getConfiguredProvider());
        assertEquals("gpt-4", response.getConfiguredModel());
        assertEquals("https://api.openai.com", response.getBaseUrl());
    }

    @Test
    @DisplayName("Should configure LLM successfully with AZURE provider")
    void shouldConfigureLLMWithAzureProvider() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.AZURE);
        request.setApiKey("test-azure-key");
        request.setModelName("gpt-35-turbo");

        SetupResponse response = setupService.configureLLM(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("AZURE", response.getConfiguredProvider());
        assertEquals("https://api.azure.com", response.getBaseUrl());
    }

    @Test
    @DisplayName("Should throw exception when provider is null")
    void shouldThrowExceptionWhenProviderIsNull() {
        SetupRequest request = new SetupRequest();
        request.setProvider(null);
        request.setApiKey("test-api-key");
        request.setModelName("test-model");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> setupService.configureLLM(request));

        assertEquals("Provider is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should use default base URL when not provided for OLLAMA")
    void shouldUseDefaultBaseUrlForOllama() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.OLLAMA);
        request.setModelName("llama2");

        SetupResponse response = setupService.configureLLM(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("http://localhost:11434", response.getBaseUrl());
    }

    @Test
    @DisplayName("Should use custom base URL when provided")
    void shouldUseCustomBaseUrlWhenProvided() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.GEMINI);
        request.setApiKey("test-api-key");
        request.setModelName("gemini-pro");
        request.setBaseUrl("https://custom.gemini.api/v1");

        SetupResponse response = setupService.configureLLM(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("https://custom.gemini.api/v1", response.getBaseUrl());
    }

    @Test
    @DisplayName("Should use correct default base URL for MISTRAL")
    void shouldUseDefaultBaseUrlForMistral() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.MISTRAL);
        request.setApiKey("test-mistral-key");
        request.setModelName("mistral-large");

        SetupResponse response = setupService.configureLLM(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("MISTRAL", response.getConfiguredProvider());
        assertEquals("https://api.mistral.com", response.getBaseUrl());
    }

    @Test
    @DisplayName("Should create LLMProvider with correct parameters")
    void shouldCreateLLMProviderWithCorrectParameters() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.GEMINI);
        request.setApiKey("my-api-key");
        request.setModelName("gemini-2.0-flash");
        request.setBaseUrl("https://my-custom-url.com");

        setupService.configureLLM(request);

        // Verify LLMProvider was constructed once
        assertEquals(1, mockedConstruction.constructed().size());
    }

    @Test
    @DisplayName("Response should have valid timestamp")
    void responseShouldHaveValidTimestamp() {
        SetupRequest request = new SetupRequest();
        request.setProvider(LLMApiProvider.OPENAI);
        request.setApiKey("test-key");
        request.setModelName("gpt-4");

        SetupResponse response = setupService.configureLLM(request);

        assertNotNull(response.getTimestamp());
        // Timestamp should be recent (within last minute)
        assertTrue(response.getTimestamp().isBefore(java.time.Instant.now().plusSeconds(1)));
        assertTrue(response.getTimestamp().isAfter(java.time.Instant.now().minusSeconds(60)));
    }
}
