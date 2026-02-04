package com.vectornode.memory.ingest.service;

import com.vectornode.memory.ingest.service.ExtractionService.ExtractedEntity;
import com.vectornode.memory.ingest.service.ExtractionService.ExtractedRelation;
import com.vectornode.memory.ingest.service.ExtractionService.ExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import com.vectornode.memory.config.LLMProvider;

/**
 * Unit tests for ExtractionService.
 * Uses Mockito to mock the static LLMProvider.callLLM method.
 */
@ExtendWith(MockitoExtension.class)
class ExtractionServiceTest {

    private ExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new ExtractionService();
    }

    @Nested
    @DisplayName("extractFromText")
    class ExtractFromTextTests {

        @Test
        @DisplayName("should extract entities and relations from valid LLM response")
        void shouldExtractEntitiesAndRelationsFromValidResponse() {
            String mockResponse = """
                    {
                      "entities": [
                        {"name": "John Doe", "type": "PERSON", "description": "A software engineer"},
                        {"name": "Google", "type": "ORGANIZATION", "description": "Tech company"}
                      ],
                      "relations": [
                        {"source": "John Doe", "target": "Google", "relation": "WORKS_FOR"}
                      ]
                    }
                    """;

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("John Doe works at Google.");

                assertThat(result.getEntities()).hasSize(2);
                assertThat(result.getRelations()).hasSize(1);

                ExtractedEntity personEntity = result.getEntities().stream()
                        .filter(e -> e.getName().equals("John Doe"))
                        .findFirst()
                        .orElseThrow();
                assertThat(personEntity.getType()).isEqualTo("PERSON");
                assertThat(personEntity.getDescription()).isEqualTo("A software engineer");

                ExtractedRelation relation = result.getRelations().get(0);
                assertThat(relation.getSourceName()).isEqualTo("John Doe");
                assertThat(relation.getTargetName()).isEqualTo("Google");
                assertThat(relation.getRelationType()).isEqualTo("WORKS_FOR");
            }
        }

        @Test
        @DisplayName("should handle markdown-wrapped JSON response")
        void shouldHandleMarkdownWrappedJson() {
            String mockResponse = """
                    ```json
                    {
                      "entities": [
                        {"name": "Paris", "type": "LOCATION", "description": "Capital of France"}
                      ],
                      "relations": []
                    }
                    ```
                    """;

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("Paris is beautiful.");

                assertThat(result.getEntities()).hasSize(1);
                assertThat(result.getEntities().get(0).getName()).isEqualTo("Paris");
            }
        }

        @Test
        @DisplayName("should handle simple markdown code block")
        void shouldHandleSimpleMarkdownCodeBlock() {
            String mockResponse = """
                    ```
                    {"entities": [], "relations": []}
                    ```
                    """;

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("Some text");

                assertThat(result.getEntities()).isEmpty();
                assertThat(result.getRelations()).isEmpty();
            }
        }

        @Test
        @DisplayName("should return empty result on LLM failure")
        void shouldReturnEmptyResultOnLLMFailure() {
            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString()))
                        .thenThrow(new RuntimeException("API error"));

                ExtractionResult result = extractionService.extractFromText("Some text");

                assertThat(result.getEntities()).isEmpty();
                assertThat(result.getRelations()).isEmpty();
            }
        }

        @Test
        @DisplayName("should return empty result for null LLM response")
        void shouldReturnEmptyResultForNullResponse() {
            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(null);

                ExtractionResult result = extractionService.extractFromText("Some text");

                assertThat(result.getEntities()).isEmpty();
                assertThat(result.getRelations()).isEmpty();
            }
        }

        @Test
        @DisplayName("should skip entities with empty names")
        void shouldSkipEntitiesWithEmptyNames() {
            String mockResponse = """
                    {
                      "entities": [
                        {"name": "", "type": "PERSON", "description": "Invalid"},
                        {"name": "Valid Entity", "type": "CONCEPT", "description": "Valid"}
                      ],
                      "relations": []
                    }
                    """;

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("Some text");

                assertThat(result.getEntities()).hasSize(1);
                assertThat(result.getEntities().get(0).getName()).isEqualTo("Valid Entity");
            }
        }

        @Test
        @DisplayName("should skip relations with empty source or target")
        void shouldSkipRelationsWithEmptySourceOrTarget() {
            String mockResponse = """
                    {
                      "entities": [],
                      "relations": [
                        {"source": "", "target": "B", "relation": "RELATED"},
                        {"source": "A", "target": "", "relation": "RELATED"},
                        {"source": "A", "target": "B", "relation": "VALID"}
                      ]
                    }
                    """;

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("Some text");

                assertThat(result.getRelations()).hasSize(1);
                assertThat(result.getRelations().get(0).getRelationType()).isEqualTo("VALID");
            }
        }

        @Test
        @DisplayName("should use default type OTHER when type is missing")
        void shouldUseDefaultTypeWhenMissing() {
            String mockResponse = """
                    {
                      "entities": [
                        {"name": "Unknown Thing", "description": "No type specified"}
                      ],
                      "relations": []
                    }
                    """;

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("Some text");

                assertThat(result.getEntities()).hasSize(1);
                assertThat(result.getEntities().get(0).getType()).isEqualTo("OTHER");
            }
        }

        @Test
        @DisplayName("should use default relation type RELATED_TO when missing")
        void shouldUseDefaultRelationTypeWhenMissing() {
            String mockResponse = """
                    {
                      "entities": [],
                      "relations": [
                        {"source": "A", "target": "B"}
                      ]
                    }
                    """;

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("Some text");

                assertThat(result.getRelations()).hasSize(1);
                assertThat(result.getRelations().get(0).getRelationType()).isEqualTo("RELATED_TO");
            }
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            String mockResponse = "{ this is not valid json }";

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("Some text");

                // Should not throw, just return empty result
                assertThat(result.getEntities()).isEmpty();
                assertThat(result.getRelations()).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle missing entities array")
        void shouldHandleMissingEntitiesArray() {
            String mockResponse = """
                    {
                      "relations": [
                        {"source": "A", "target": "B", "relation": "TEST"}
                      ]
                    }
                    """;

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("Some text");

                assertThat(result.getEntities()).isEmpty();
                assertThat(result.getRelations()).hasSize(1);
            }
        }

        @Test
        @DisplayName("should handle missing relations array")
        void shouldHandleMissingRelationsArray() {
            String mockResponse = """
                    {
                      "entities": [
                        {"name": "Test", "type": "CONCEPT", "description": "Test entity"}
                      ]
                    }
                    """;

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.callLLM(anyString())).thenReturn(mockResponse);

                ExtractionResult result = extractionService.extractFromText("Some text");

                assertThat(result.getEntities()).hasSize(1);
                assertThat(result.getRelations()).isEmpty();
            }
        }
    }
}
