package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.ingest.repository.ContextRepository;
import com.vectornode.memory.ingest.repository.EntityRepository;
import com.vectornode.memory.ingest.repository.KnowledgeBaseRepository;
import com.vectornode.memory.ingest.repository.RelationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IngestionWorker.
 */
@ExtendWith(MockitoExtension.class)
class IngestionWorkerTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private ContextRepository contextRepository;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private RelationRepository relationRepository;

    @Mock
    private ChunkingService chunkingService;

    @Mock
    private ExtractionService extractionService;

    private IngestionWorker ingestionWorker;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ingestionWorker = new IngestionWorker(
                knowledgeBaseRepository,
                contextRepository,
                entityRepository,
                relationRepository,
                chunkingService,
                extractionService,
                objectMapper);
    }

    @Nested
    @DisplayName("handleKbCreated")
    class HandleKbCreatedTests {

        @Test
        @DisplayName("should skip processing when knowledge base not found")
        void shouldSkipWhenKnowledgeBaseNotFound() {
            when(knowledgeBaseRepository.findById(999)).thenReturn(Optional.empty());

            ingestionWorker.handleKbCreated(999);

            verify(chunkingService, never()).chunkText(anyString());
            verify(contextRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("should chunk content and save contexts")
        void shouldChunkContentAndSaveContexts() {
            KnowledgeBase kb = KnowledgeBase.builder()
                    .content("This is test content. More content here.")
                    .build();
            kb.setId(1);

            List<String> chunks = List.of("This is test content.", "More content here.");
            float[] mockEmbedding = new float[] { 0.1f, 0.2f, 0.3f };

            when(knowledgeBaseRepository.findById(1)).thenReturn(Optional.of(kb));
            when(chunkingService.chunkText("This is test content. More content here.")).thenReturn(chunks);

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);

                ingestionWorker.handleKbCreated(1);

                ArgumentCaptor<List<Context>> captor = ArgumentCaptor.forClass(List.class);
                verify(contextRepository).saveAll(captor.capture());

                List<Context> savedContexts = captor.getValue();
                assertThat(savedContexts).hasSize(2);
                assertThat(savedContexts.get(0).getContextData()).isEqualTo("This is test content.");
                assertThat(savedContexts.get(1).getContextData()).isEqualTo("More content here.");
            }
        }

        @Test
        @DisplayName("should add metadata to contexts")
        void shouldAddMetadataToContexts() {
            KnowledgeBase kb = KnowledgeBase.builder()
                    .content("Test content.")
                    .build();
            kb.setId(1);

            when(knowledgeBaseRepository.findById(1)).thenReturn(Optional.of(kb));
            when(chunkingService.chunkText(anyString())).thenReturn(List.of("Test content."));

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(new float[] { 0.1f });

                ingestionWorker.handleKbCreated(1);

                ArgumentCaptor<List<Context>> captor = ArgumentCaptor.forClass(List.class);
                verify(contextRepository).saveAll(captor.capture());

                Context savedContext = captor.getValue().get(0);
                assertThat(savedContext.getMetadata()).isNotNull();
                assertThat(savedContext.getMetadata().get("source_kb_id").asInt()).isEqualTo(1);
                assertThat(savedContext.getMetadata().get("index").asInt()).isEqualTo(0);
            }
        }
    }

    @Nested
    @DisplayName("handleContextCreated")
    class HandleContextCreatedTests {

        @Test
        @DisplayName("should skip processing when context not found")
        void shouldSkipWhenContextNotFound() {
            when(contextRepository.findById(999)).thenReturn(Optional.empty());

            ingestionWorker.handleContextCreated(999);

            verify(extractionService, never()).extractFromText(anyString());
        }

        @Test
        @DisplayName("should extract entities and save them")
        void shouldExtractEntitiesAndSaveThem() {
            KnowledgeBase kb = KnowledgeBase.builder().build();
            kb.setId(1);

            Context ctx = Context.builder()
                    .knowledgeBase(kb)
                    .contextData("John works at Google.")
                    .build();
            ctx.setId(100);

            ExtractionService.ExtractionResult result = new ExtractionService.ExtractionResult();
            ExtractionService.ExtractedEntity entity = new ExtractionService.ExtractedEntity();
            entity.setName("John");
            entity.setType("PERSON");
            entity.setDescription("A person");
            result.getEntities().add(entity);

            float[] mockEmbedding = new float[] { 0.1f, 0.2f };

            when(contextRepository.findById(100)).thenReturn(Optional.of(ctx));
            when(extractionService.extractFromText("John works at Google.")).thenReturn(result);
            when(entityRepository.findByEntityName("John")).thenReturn(Optional.empty());

            RagEntity savedEntity = RagEntity.builder().entityName("John").build();
            savedEntity.setId(10);
            when(entityRepository.save(any(RagEntity.class))).thenReturn(savedEntity);

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);

                ingestionWorker.handleContextCreated(100);

                verify(entityRepository).save(any(RagEntity.class));
            }
        }

        @Test
        @DisplayName("should link existing entity to context instead of creating new one")
        void shouldLinkExistingEntityToContext() {
            KnowledgeBase kb = KnowledgeBase.builder().build();
            kb.setId(1);

            Context ctx = Context.builder()
                    .knowledgeBase(kb)
                    .contextData("John is here.")
                    .build();
            ctx.setId(100);

            ExtractionService.ExtractionResult result = new ExtractionService.ExtractionResult();
            ExtractionService.ExtractedEntity extractedEntity = new ExtractionService.ExtractedEntity();
            extractedEntity.setName("John");
            extractedEntity.setType("PERSON");
            extractedEntity.setDescription("A person");
            result.getEntities().add(extractedEntity);

            RagEntity existingEntity = RagEntity.builder().entityName("John").build();
            existingEntity.setId(10);

            when(contextRepository.findById(100)).thenReturn(Optional.of(ctx));
            when(extractionService.extractFromText("John is here.")).thenReturn(result);
            when(entityRepository.findByEntityName("John")).thenReturn(Optional.of(existingEntity));
            when(entityRepository.save(any(RagEntity.class))).thenReturn(existingEntity);

            ingestionWorker.handleContextCreated(100);

            // Should save the existing entity (with updated context link)
            verify(entityRepository).save(existingEntity);
        }

        @Test
        @DisplayName("should use upsertRelation for relations")
        void shouldUseUpsertRelationForRelations() {
            KnowledgeBase kb = KnowledgeBase.builder().build();
            kb.setId(1);

            Context ctx = Context.builder()
                    .knowledgeBase(kb)
                    .contextData("John works at Google.")
                    .build();
            ctx.setId(100);

            ExtractionService.ExtractionResult result = new ExtractionService.ExtractionResult();

            ExtractionService.ExtractedEntity entity1 = new ExtractionService.ExtractedEntity();
            entity1.setName("John");
            entity1.setType("PERSON");
            entity1.setDescription("Person");
            result.getEntities().add(entity1);

            ExtractionService.ExtractedEntity entity2 = new ExtractionService.ExtractedEntity();
            entity2.setName("Google");
            entity2.setType("ORGANIZATION");
            entity2.setDescription("Company");
            result.getEntities().add(entity2);

            ExtractionService.ExtractedRelation relation = new ExtractionService.ExtractedRelation();
            relation.setSourceName("John");
            relation.setTargetName("Google");
            relation.setRelationType("WORKS_FOR");
            result.getRelations().add(relation);

            float[] mockEmbedding = new float[] { 0.1f };

            RagEntity savedJohn = RagEntity.builder().entityName("John").build();
            savedJohn.setId(10);

            RagEntity savedGoogle = RagEntity.builder().entityName("Google").build();
            savedGoogle.setId(20);

            when(contextRepository.findById(100)).thenReturn(Optional.of(ctx));
            when(extractionService.extractFromText(anyString())).thenReturn(result);
            when(entityRepository.findByEntityName("John")).thenReturn(Optional.empty());
            when(entityRepository.findByEntityName("Google")).thenReturn(Optional.empty());
            when(entityRepository.save(any(RagEntity.class)))
                    .thenReturn(savedJohn)
                    .thenReturn(savedGoogle);

            try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
                mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);

                ingestionWorker.handleContextCreated(100);

                verify(relationRepository).upsertRelation(10, 20, "WORKS_FOR");
            }
        }
    }
}
