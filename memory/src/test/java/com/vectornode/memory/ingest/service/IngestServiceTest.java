package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.enums.ConverserRole;
import com.vectornode.memory.ingest.dto.request.IngestDocumentRequest;
import com.vectornode.memory.ingest.dto.request.IngestPromptRequest;
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

    @BeforeEach
    void setUp() {
        ingestService = new IngestService(objectMapper);
        ReflectionTestUtils.setField(ingestService, "entityManager", entityManager);
    }

    @Nested
    @DisplayName("processPrompt")
    class ProcessPromptTests {

        private IngestPromptRequest promptRequest;

        @BeforeEach
        void setUpPrompt() {
            promptRequest = IngestPromptRequest.builder()
                    .uid("user-123")
                    .converser(ConverserRole.USER)
                    .text("This is a test prompt content.")
                    .build();
        }

        @Test
        @DisplayName("should successfully persist KnowledgeBase and return IngestResponse for prompt")
        void shouldSuccessfullyIngestPrompt() {
            float[] mockEmbedding = new float[] { 0.1f, 0.2f, 0.3f };

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);

                IngestResponse result = ingestService.processPrompt(promptRequest);

                // Verify EntityManager interactions
                verify(entityManager).persist(any(KnowledgeBase.class));
                verify(entityManager).flush();

                // Verify response
                assertThat(result).isNotNull();
                assertThat(result.getStatus()).isEqualTo("SUCCESS");
                assertThat(result.getMessage()).isEqualTo("Prompt ingested successfully");
                assertThat(result.getProcessingTimeMs()).isNotNull();
                assertThat(result.getEmbeddingTimeMs()).isNotNull();

                // Verify embedded KnowledgeBase entity
                KnowledgeBase kb = result.getKnowledgeBase();
                assertThat(kb).isNotNull();
                assertThat(kb.getUid()).isEqualTo("user-123");
                assertThat(kb.getConverser()).isEqualTo(ConverserRole.USER);
                assertThat(kb.getContent()).isEqualTo("This is a test prompt content.");
                assertThat(kb.getVectorEmbedding()).isEqualTo(mockEmbedding);
            }
        }

        @Test
        @DisplayName("should throw exception when embedding generation fails for prompt")
        void shouldThrowExceptionWhenEmbeddingFails() {
            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString()))
                        .thenThrow(new RuntimeException("Embedding API error"));

                assertThatThrownBy(() -> ingestService.processPrompt(promptRequest))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Ingestion failed");

                // Verify no persistence occurred
                verify(entityManager, never()).persist(any());
            }
        }
    }

    @Nested
    @DisplayName("processDocument")
    class ProcessDocumentTests {

        private IngestDocumentRequest documentRequest;

        @BeforeEach
        void setUpDocument() {
            documentRequest = IngestDocumentRequest.builder()
                    .uid("user-456")
                    .documentTitle("Test Document")
                    .documentText("This is the full text of the document.")
                    .build();
        }

        @Test
        @DisplayName("should successfully persist KnowledgeBase and return IngestResponse for document")
        void shouldSuccessfullyIngestDocument() {
            IngestResponse result = ingestService.processDocument(documentRequest);

            // Verify EntityManager interactions
            verify(entityManager).persist(any(KnowledgeBase.class));
            verify(entityManager).flush();

            // Verify response
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getMessage()).isEqualTo("Document ingested successfully");
            assertThat(result.getProcessingTimeMs()).isNotNull();
            assertThat(result.getEmbeddingTimeMs()).isEqualTo(0L);

            // Verify embedded KnowledgeBase entity
            KnowledgeBase kb = result.getKnowledgeBase();
            assertThat(kb).isNotNull();
            assertThat(kb.getUid()).isEqualTo("user-456");
            assertThat(kb.getConverser()).isEqualTo(ConverserRole.DOCUMENT);
            assertThat(kb.getContent()).isEqualTo("This is the full text of the document.");
            assertThat(kb.getVectorEmbedding()).hasSize(768); // Assuming 768 is default empty array size created in processDocument
        }
    }
}
