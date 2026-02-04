package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.enums.ConverserRole;
import com.vectornode.memory.ingest.dto.request.IngestContentRequest;
import com.vectornode.memory.ingest.dto.response.IngestResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    private EntityManager entityManager;

    @Spy
    private ObjectMapper objectMapper;

    private IngestService ingestService;

    private IngestContentRequest request;

    @BeforeEach
    void setUp() {
        ingestService = new IngestService(objectMapper);
        ReflectionTestUtils.setField(ingestService, "entityManager", entityManager);

        request = IngestContentRequest.builder()
                .uid("user-123")
                .converser(ConverserRole.USER)
                .content("This is a test document content.")
                .build();
    }

    @Nested
    @DisplayName("ingestContent")
    class IngestContentTests {

        @Test
        @DisplayName("should successfully persist KnowledgeBase and return IngestResponse")
        void shouldSuccessfullyIngestContent() {
            float[] mockEmbedding = new float[] { 0.1f, 0.2f, 0.3f };

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);

                IngestResponse result = ingestService.ingestContent(request);

                // Verify EntityManager interactions
                verify(entityManager).persist(any(KnowledgeBase.class));
                verify(entityManager).flush();

                // Verify response
                assertThat(result).isNotNull();
                assertThat(result.getStatus()).isEqualTo("SUCCESS");
                assertThat(result.getMessage()).isEqualTo("Document ingested successfully");
                assertThat(result.getProcessingTimeMs()).isNotNull();
                assertThat(result.getEmbeddingTimeMs()).isNotNull();

                // Verify embedded KnowledgeBase entity
                KnowledgeBase kb = result.getKnowledgeBase();
                assertThat(kb).isNotNull();
                assertThat(kb.getUid()).isEqualTo("user-123");
                assertThat(kb.getConverser()).isEqualTo(ConverserRole.USER);
                assertThat(kb.getContent()).isEqualTo("This is a test document content.");
                assertThat(kb.getVectorEmbedding()).isEqualTo(mockEmbedding);
            }
        }

        @Test
        @DisplayName("should throw exception when embedding generation fails")
        void shouldThrowExceptionWhenEmbeddingFails() {
            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString()))
                        .thenThrow(new RuntimeException("Embedding API error"));

                assertThatThrownBy(() -> ingestService.ingestContent(request))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Ingestion failed");

                // Verify no persistence occurred
                verify(entityManager, never()).persist(any());
            }
        }
    }
}
