package com.vectornode.memory.ingest.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.enums.ConverserRole;
import com.vectornode.memory.ingest.dto.request.IngestContentRequest;
import com.vectornode.memory.ingest.dto.response.IngestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for IngestService.
 */
@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @InjectMocks
    private IngestService ingestService;

    private IngestContentRequest request;

    @BeforeEach
    void setUp() {
        request = IngestContentRequest.builder()
                .uid("user-123")
                .converser(ConverserRole.USER)
                .content("This is a test document content.")
                .build();
    }

    @Nested
    @DisplayName("ingestDocument")
    class IngestDocumentTests {

        @Test
        @DisplayName("should successfully ingest document and return response")
        void shouldSuccessfullyIngestDocument() {
            float[] mockEmbedding = new float[] { 0.1f, 0.2f, 0.3f };

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);

                IngestResponse response = ingestService.ingestDocument(request);

                assertThat(response.getStatus()).isEqualTo("SUCCESS");
                assertThat(response.getKnowledgeBaseId()).isNotNull();
                assertThat(response.getMessage()).contains("successfully");
                assertThat(response.getEmbeddingDimensions()).isEqualTo(3);
                assertThat(response.getProcessingTimeMs()).isNotNull();
                assertThat(response.getEmbeddingTimeMs()).isNotNull();
            }
        }

        @Test
        @DisplayName("should throw exception when embedding generation fails")
        void shouldThrowExceptionWhenEmbeddingFails() {
            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString()))
                        .thenThrow(new RuntimeException("Embedding API error"));

                assertThatThrownBy(() -> ingestService.ingestDocument(request))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Ingestion failed");
            }
        }
    }
}
