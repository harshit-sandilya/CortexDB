package com.vectornode.cortexdb.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vectornode.cortexdb.config.HttpClientWrapper;
import com.vectornode.cortexdb.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SetupApi and IngestApi with mocked HttpClientWrapper.
 */
@ExtendWith(MockitoExtension.class)
class SetupIngestApiTest {

    @Mock
    private HttpClientWrapper http;

    private SetupApi setupApi;
    private IngestApi ingestApi;

    @BeforeEach
    void setUp() {
        setupApi = new SetupApi(http);
        ingestApi = new IngestApi(http);
    }

    // ── SetupApi ─────────────────────────────────────────────────

    @Test
    void configure_sendsCorrectRequest() {
        SetupResponse mockResponse = new SetupResponse();
        mockResponse.setSuccess(true);
        mockResponse.setConfiguredProvider("GEMINI");

        when(http.post(eq("/api/setup"), any(SetupRequest.class), eq(SetupResponse.class)))
                .thenReturn(mockResponse);

        SetupResponse result = setupApi.configure(
                LLMApiProvider.GEMINI, "test-key",
                "gemini-2.0-flash", "gemini-embedding-001");

        assertTrue(result.isSuccess());
        assertEquals("GEMINI", result.getConfiguredProvider());
        verify(http).post(eq("/api/setup"), any(SetupRequest.class), eq(SetupResponse.class));
    }

    @Test
    void configure_withBaseUrl() {
        SetupResponse mockResponse = new SetupResponse();
        mockResponse.setSuccess(true);

        when(http.post(eq("/api/setup"), any(SetupRequest.class), eq(SetupResponse.class)))
                .thenReturn(mockResponse);

        SetupResponse result = setupApi.configure(
                LLMApiProvider.AZURE, "test-key",
                "gpt-4", "text-embedding-ada-002", "https://my-azure.openai.azure.com/");

        assertTrue(result.isSuccess());
    }

    @Test
    void configure_withStringProvider() {
        SetupResponse mockResponse = new SetupResponse();
        mockResponse.setSuccess(true);

        when(http.post(eq("/api/setup"), any(SetupRequest.class), eq(SetupResponse.class)))
                .thenReturn(mockResponse);

        SetupResponse result = setupApi.configure("gemini", "test-key",
                "gemini-2.0-flash", "gemini-embedding-001");

        assertTrue(result.isSuccess());
    }

    // ── IngestApi ────────────────────────────────────────────────

    @Test
    void document_sendsCorrectRequest() {
        IngestResponse mockResponse = new IngestResponse();
        mockResponse.setStatus("SUCCESS");
        mockResponse.setMessage("Ingested successfully");

        when(http.post(eq("/api/ingest/document"), any(IngestRequest.class), eq(IngestResponse.class)))
                .thenReturn(mockResponse);

        IngestResponse result = ingestApi.document("user-1", ConverserRole.USER,
                "Hello world");

        assertEquals("SUCCESS", result.getStatus());
        verify(http).post(eq("/api/ingest/document"), any(IngestRequest.class), eq(IngestResponse.class));
    }

    @Test
    void document_withMetadata() {
        IngestResponse mockResponse = new IngestResponse();
        mockResponse.setStatus("SUCCESS");

        when(http.post(eq("/api/ingest/document"), any(IngestRequest.class), eq(IngestResponse.class)))
                .thenReturn(mockResponse);

        IngestResponse result = ingestApi.document("user-1", ConverserRole.AGENT,
                "AI response", Map.of("source", "test"));

        assertEquals("SUCCESS", result.getStatus());
    }

    @Test
    void document_withStringConverser() {
        IngestResponse mockResponse = new IngestResponse();
        mockResponse.setStatus("SUCCESS");

        when(http.post(eq("/api/ingest/document"), any(IngestRequest.class), eq(IngestResponse.class)))
                .thenReturn(mockResponse);

        IngestResponse result = ingestApi.document("user-1", "user", "Hello");

        assertEquals("SUCCESS", result.getStatus());
    }
}
