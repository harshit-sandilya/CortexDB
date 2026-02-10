package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IngestionWorker.
 */
@ExtendWith(MockitoExtension.class)
class IngestionWorkerTest {

    @Mock
    private ChunkingService chunkingService;

    @Mock
    private ExtractionService extractionService;

    @Mock
    private EntityManager entityManager;

    private IngestionWorker ingestionWorker;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ingestionWorker = new IngestionWorker(chunkingService, extractionService, objectMapper);
        ReflectionTestUtils.setField(ingestionWorker, "entityManager", entityManager);
    }

    @Nested
    @DisplayName("processKnowledgeBase")
    class ProcessKnowledgeBaseTests {

        @Test
        @DisplayName("should skip processing when content is null")
        void shouldSkipWhenContentIsNull() {
            ingestionWorker.processKnowledgeBase(UUID.randomUUID(), null);
            verify(chunkingService, never()).chunkText(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should skip processing when content is blank")
        void shouldSkipWhenContentIsBlank() {
            ingestionWorker.processKnowledgeBase(UUID.randomUUID(), "   ");
            verify(chunkingService, never()).chunkText(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should chunk content and generate embeddings")
        void shouldChunkContentAndGenerateEmbeddings() {
            UUID kbId = UUID.randomUUID();
            String content = "This is test content. More content here.";
            List<String> chunks = List.of("This is test content.", "More content here.");
            float[] mockEmbedding = new float[] { 0.1f, 0.2f, 0.3f };

            // Mock KnowledgeBase reference
            com.vectornode.memory.entity.KnowledgeBase mockKb = mock(com.vectornode.memory.entity.KnowledgeBase.class);
            when(mockKb.getId()).thenReturn(kbId);
            when(entityManager.getReference(com.vectornode.memory.entity.KnowledgeBase.class, kbId)).thenReturn(mockKb);

            when(chunkingService.chunkText(eq(content), anyInt(), anyInt())).thenReturn(chunks);

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);

                ingestionWorker.processKnowledgeBase(kbId, content);

                verify(chunkingService).chunkText(eq(content), anyInt(), anyInt());
                // LLMProvider.getEmbedding called once per chunk
                mockedLLM.verify(() -> LLMProvider.getEmbedding(anyString()), times(2));
                // Verify Context entities were persisted
                verify(entityManager, times(chunks.size())).persist(any(com.vectornode.memory.entity.Context.class));
            }
        }
    }

    @Nested
    @DisplayName("processContext")
    class ProcessContextTests {

        @Test
        @DisplayName("should skip processing when text chunk is null")
        void shouldSkipWhenTextChunkIsNull() {
            ingestionWorker.processContext(UUID.randomUUID(), UUID.randomUUID(), null);
            verify(extractionService, never()).extractFromText(anyString());
        }

        @Test
        @DisplayName("should skip processing when text chunk is blank")
        void shouldSkipWhenTextChunkIsBlank() {
            ingestionWorker.processContext(UUID.randomUUID(), UUID.randomUUID(), "   ");
            verify(extractionService, never()).extractFromText(anyString());
        }

        @Test
        @DisplayName("should extract entities and relations with embeddings")
        void shouldExtractEntitiesAndRelations() {
            UUID contextId = UUID.randomUUID();
            UUID kbId = UUID.randomUUID();
            String textChunk = "John works at Google.";
            float[] mockEmbedding = new float[] { 0.1f, 0.2f };

            // Mock Context reference
            com.vectornode.memory.entity.Context mockContext = mock(com.vectornode.memory.entity.Context.class);
            when(entityManager.getReference(com.vectornode.memory.entity.Context.class, contextId))
                    .thenReturn(mockContext);

            // Mock TypedQuery for entity lookup
            jakarta.persistence.TypedQuery<com.vectornode.memory.entity.RagEntity> mockQuery = mock(
                    jakarta.persistence.TypedQuery.class);
            when(entityManager.createQuery(anyString(), eq(com.vectornode.memory.entity.RagEntity.class)))
                    .thenReturn(mockQuery);
            when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
            when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);
            when(mockQuery.getResultList()).thenReturn(java.util.Collections.emptyList()); // No existing entities

            // Mock TypedQuery for relation lookup
            jakarta.persistence.TypedQuery<com.vectornode.memory.entity.Relation> mockRelationQuery = mock(
                    jakarta.persistence.TypedQuery.class);
            when(entityManager.createQuery(anyString(), eq(com.vectornode.memory.entity.Relation.class)))
                    .thenReturn(mockRelationQuery);
            when(mockRelationQuery.setParameter(anyString(), any())).thenReturn(mockRelationQuery);
            when(mockRelationQuery.getResultList()).thenReturn(java.util.Collections.emptyList());

            ExtractionService.ExtractionResult result = new ExtractionService.ExtractionResult();

            ExtractionService.ExtractedEntity entity1 = new ExtractionService.ExtractedEntity();
            entity1.setName("John");
            entity1.setType("PERSON");
            entity1.setDescription("A person");
            result.getEntities().add(entity1);

            ExtractionService.ExtractedEntity entity2 = new ExtractionService.ExtractedEntity();
            entity2.setName("Google");
            entity2.setType("ORGANIZATION");
            entity2.setDescription("A company");
            result.getEntities().add(entity2);

            ExtractionService.ExtractedRelation relation = new ExtractionService.ExtractedRelation();
            relation.setSourceName("John");
            relation.setTargetName("Google");
            relation.setRelationType("WORKS_FOR");
            result.getRelations().add(relation);

            when(extractionService.extractFromText(textChunk)).thenReturn(result);

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);

                ingestionWorker.processContext(contextId, kbId, textChunk);

                verify(extractionService).extractFromText(textChunk);
                // LLMProvider.getEmbedding called once per entity
                mockedLLM.verify(() -> LLMProvider.getEmbedding(anyString()), times(2));
                // Verify entities and relations were persisted
                verify(entityManager, times(2)).persist(any(com.vectornode.memory.entity.RagEntity.class));
                verify(entityManager, times(1)).persist(any(com.vectornode.memory.entity.Relation.class));
            }
        }
    }
}
