package com.vectornode.memory.ingest.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.ingest.dto.request.IngestDocumentRequest;
import com.vectornode.memory.ingest.dto.response.IngestResponse;
import com.vectornode.memory.ingest.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IngestService.
 */
@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @InjectMocks
    private IngestService ingestService;

    private IngestDocumentRequest request;

    @BeforeEach
    void setUp() {
        request = IngestDocumentRequest.builder()
                .userId("user-123")
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

            KnowledgeBase savedKb = KnowledgeBase.builder()
                    .userId("user-123")
                    .content("This is a test document content.")
                    .vector(mockEmbedding)
                    .build();
            // Simulate saved entity with ID
            savedKb.setId(42);

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);
                when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenReturn(savedKb);

                IngestResponse response = ingestService.ingestDocument(request);

                assertThat(response.getStatus()).isEqualTo("SUCCESS");
                assertThat(response.getKnowledgeBaseId()).isEqualTo("42");
                assertThat(response.getMessage()).contains("successfully");

                // Verify new detailed response fields
                assertThat(response.getKnowledgeBase()).isNotNull();
                assertThat(response.getKnowledgeBase().getId()).isEqualTo(42);
                assertThat(response.getKnowledgeBase().getUserId()).isEqualTo("user-123");
                assertThat(response.getKnowledgeBase().getVectorDimensions()).isEqualTo(3);
                assertThat(response.getProcessingTimeMs()).isNotNull();
                assertThat(response.getEmbeddingTimeMs()).isNotNull();

                // Verify the entity was saved with correct fields
                ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
                verify(knowledgeBaseRepository).save(captor.capture());

                KnowledgeBase capturedKb = captor.getValue();
                assertThat(capturedKb.getUserId()).isEqualTo("user-123");
                assertThat(capturedKb.getContent()).isEqualTo("This is a test document content.");
                assertThat(capturedKb.getVector()).isEqualTo(mockEmbedding);
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

        @Test
        @DisplayName("should throw exception when repository save fails")
        void shouldThrowExceptionWhenRepositorySaveFails() {
            float[] mockEmbedding = new float[] { 0.1f, 0.2f, 0.3f };

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);
                when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
                        .thenThrow(new RuntimeException("Database error"));

                assertThatThrownBy(() -> ingestService.ingestDocument(request))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Ingestion failed");
            }
        }
    }
}
