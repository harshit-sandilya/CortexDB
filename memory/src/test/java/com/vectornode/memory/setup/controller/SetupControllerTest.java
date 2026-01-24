package com.vectornode.memory.setup.controller;

import com.vectornode.memory.entity.enums.LLMApiProvider;
import com.vectornode.memory.setup.dto.request.SetupRequest;
import com.vectornode.memory.setup.dto.response.SetupResponse;
import com.vectornode.memory.setup.service.SetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SetupController using Mockito without Spring context.
 * This approach avoids the JacksonAutoConfiguration bean conflict issue.
 * //test the controller logic directly with mocked SetupService
 */
@ExtendWith(MockitoExtension.class)
class SetupControllerTest {

    @Mock
    private SetupService setupService;

    @InjectMocks
    private SetupController setupController;

    private SetupRequest validRequest;
    private SetupResponse successResponse;

    @BeforeEach
    void setUp() {
        validRequest = new SetupRequest();
        validRequest.setProvider(LLMApiProvider.GEMINI);
        validRequest.setApiKey("test-api-key");
        validRequest.setModelName("gemini-2.0-flash");

        successResponse = SetupResponse.builder()
                .message("Setup params validated and probed successfully. Backend is ready.")
                .success(true)
                .configuredProvider("GEMINI")
                .configuredModel("gemini-2.0-flash")
                .baseUrl("https://api.gemini.com")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should return 200 OK with success response when configuration succeeds")
    void shouldReturnOkWhenConfigurationSucceeds() {
        when(setupService.configureLLM(any(SetupRequest.class)))
                .thenReturn(successResponse);

        ResponseEntity<SetupResponse> response = setupController.configure(validRequest);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("GEMINI", response.getBody().getConfiguredProvider());
        assertEquals("gemini-2.0-flash", response.getBody().getConfiguredModel());

        verify(setupService, times(1)).configureLLM(any(SetupRequest.class));
    }

    @Test
    @DisplayName("Should return correct response structure with all fields")
    void shouldReturnCorrectResponseStructure() {
        when(setupService.configureLLM(any(SetupRequest.class)))
                .thenReturn(successResponse);

        ResponseEntity<SetupResponse> response = setupController.configure(validRequest);

        assertNotNull(response.getBody());
        assertEquals("Setup params validated and probed successfully. Backend is ready.",
                response.getBody().getMessage());
        assertTrue(response.getBody().isSuccess());
        assertEquals("GEMINI", response.getBody().getConfiguredProvider());
        assertEquals("gemini-2.0-flash", response.getBody().getConfiguredModel());
        assertEquals("https://api.gemini.com", response.getBody().getBaseUrl());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    @DisplayName("Should work with OPENAI provider")
    void shouldWorkWithOpenAIProvider() {
        SetupRequest openAiRequest = new SetupRequest();
        openAiRequest.setProvider(LLMApiProvider.OPENAI);
        openAiRequest.setApiKey("openai-key");
        openAiRequest.setModelName("gpt-4");

        SetupResponse openAiResponse = SetupResponse.builder()
                .message("Setup params validated and probed successfully. Backend is ready.")
                .success(true)
                .configuredProvider("OPENAI")
                .configuredModel("gpt-4")
                .baseUrl("https://api.openai.com")
                .timestamp(Instant.now())
                .build();

        when(setupService.configureLLM(any(SetupRequest.class)))
                .thenReturn(openAiResponse);

        ResponseEntity<SetupResponse> response = setupController.configure(openAiRequest);

        assertNotNull(response.getBody());
        assertEquals("OPENAI", response.getBody().getConfiguredProvider());
        assertEquals("gpt-4", response.getBody().getConfiguredModel());
    }

    @Test
    @DisplayName("Should work with AZURE provider")
    void shouldWorkWithAzureProvider() {
        SetupRequest azureRequest = new SetupRequest();
        azureRequest.setProvider(LLMApiProvider.AZURE);
        azureRequest.setApiKey("azure-key");
        azureRequest.setModelName("gpt-35-turbo");
        azureRequest.setBaseUrl("https://my-azure-endpoint.openai.azure.com");

        SetupResponse azureResponse = SetupResponse.builder()
                .message("Setup params validated and probed successfully. Backend is ready.")
                .success(true)
                .configuredProvider("AZURE")
                .configuredModel("gpt-35-turbo")
                .baseUrl("https://my-azure-endpoint.openai.azure.com")
                .timestamp(Instant.now())
                .build();

        when(setupService.configureLLM(any(SetupRequest.class)))
                .thenReturn(azureResponse);

        ResponseEntity<SetupResponse> response = setupController.configure(azureRequest);

        assertNotNull(response.getBody());
        assertEquals("AZURE", response.getBody().getConfiguredProvider());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    @DisplayName("Should propagate exception when service throws exception")
    void shouldPropagateExceptionWhenServiceThrows() {
        when(setupService.configureLLM(any(SetupRequest.class)))
                .thenThrow(new IllegalStateException("LLMProvider initialization failed"));

        assertThrows(IllegalStateException.class, () -> {
            setupController.configure(validRequest);
        });
    }

    @Test
    @DisplayName("Should propagate IllegalArgumentException from service")
    void shouldPropagateIllegalArgumentException() {
        when(setupService.configureLLM(any(SetupRequest.class)))
                .thenThrow(new IllegalArgumentException("Provider is required"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> setupController.configure(validRequest));

        assertEquals("Provider is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should accept request with OLLAMA provider")
    void shouldAcceptRequestWithOllamaProvider() {
        SetupRequest ollamaRequest = new SetupRequest();
        ollamaRequest.setProvider(LLMApiProvider.OLLAMA);
        ollamaRequest.setModelName("llama2");
        // apiKey and baseUrl are optional for OLLAMA

        SetupResponse ollamaResponse = SetupResponse.builder()
                .message("Setup params validated and probed successfully. Backend is ready.")
                .success(true)
                .configuredProvider("OLLAMA")
                .configuredModel("llama2")
                .baseUrl("http://localhost:11434")
                .timestamp(Instant.now())
                .build();

        when(setupService.configureLLM(any(SetupRequest.class)))
                .thenReturn(ollamaResponse);

        ResponseEntity<SetupResponse> response = setupController.configure(ollamaRequest);

        assertNotNull(response.getBody());
        assertEquals("OLLAMA", response.getBody().getConfiguredProvider());
        assertEquals("http://localhost:11434", response.getBody().getBaseUrl());
    }

    @Test
    @DisplayName("Should call service exactly once per request")
    void shouldCallServiceExactlyOnce() {
        when(setupService.configureLLM(any(SetupRequest.class)))
                .thenReturn(successResponse);

        setupController.configure(validRequest);

        verify(setupService, times(1)).configureLLM(any(SetupRequest.class));
        verifyNoMoreInteractions(setupService);
    }
}
