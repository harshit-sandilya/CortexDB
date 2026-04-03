package com.vectornode.memory.ingest.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.ingest.service.ChunkingService;
import com.vectornode.memory.ingest.service.ContradictionDetector;
import com.vectornode.memory.ingest.service.ExtractionService;
import com.vectornode.memory.ingest.service.IngestionWorker;
import com.vectornode.memory.ingest.service.PageIndexService;
import com.vectornode.memory.query.repository.ContextRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style unit tests for IngestionWorker + ContradictionDetector.
 * Verifies that the ingestion pipeline correctly triggers contradiction
 * detection in both INSERT and MERGE flows.
 */
@ExtendWith(MockitoExtension.class)
class IngestionContradictionIntegrationTest {

        @Mock
        private ChunkingService chunkingService;
        @Mock
        private ExtractionService extractionService;
        @Mock
        private PageIndexService pageIndexService;
        @Mock
        private ContradictionDetector contradictionDetector;
        @Mock
        private ContextRepository contextRepository;
        @Mock
        private EntityManager entityManager;

        private IngestionWorker ingestionWorker;
        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();
                ingestionWorker = new IngestionWorker(
                                chunkingService, extractionService, pageIndexService,
                                contradictionDetector, contextRepository, objectMapper);
                ReflectionTestUtils.setField(ingestionWorker, "entityManager", entityManager);
        }

        @Test
        @DisplayName("INSERT flow should trigger contradiction check after persist+flush")
        void insertFlowShouldTriggerContradictionCheck() {
                UUID kbId = UUID.randomUUID();
                String content = "The capital of France is Paris.";
                float[] mockEmbedding = new float[] { 0.1f, 0.2f, 0.3f };

                ChunkingService.CompressedChunk compressed = new ChunkingService.CompressedChunk(
                                "France's capital is Paris.", List.of("France", "Paris"), "Geography", null);

                KnowledgeBase mockKb = mock(KnowledgeBase.class);
                when(mockKb.getId()).thenReturn(kbId);
                when(entityManager.getReference(KnowledgeBase.class, kbId)).thenReturn(mockKb);
                when(chunkingService.compressPrompt(content)).thenReturn(compressed);

                // No similar chunks → INSERT path
                when(contextRepository.findHighlySimilar(anyString(), anyDouble()))
                                .thenReturn(Collections.emptyList());

                // Simulate persist() assigning an ID to the Context object
                doAnswer(invocation -> {
                        Object arg = invocation.getArgument(0);
                        if (arg instanceof Context ctx && ctx.getId() == null) {
                                ctx.setId(UUID.randomUUID());
                        }
                        return null;
                }).when(entityManager).persist(any());

                // Mock extraction to avoid NPE
                ExtractionService.ExtractionResult result = new ExtractionService.ExtractionResult();
                result.setEntities(new java.util.ArrayList<>());
                result.setRelations(new java.util.ArrayList<>());
                result.setMetadata(new ExtractionService.ExtractedMetadata());
                lenient().when(extractionService.extractFromText(anyString())).thenReturn(result);

                try (MockedStatic<LLMProvider> llm = mockStatic(LLMProvider.class)) {
                        llm.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mockEmbedding);

                        ingestionWorker.processKnowledgeBase(kbId, content);

                        // Verify contradiction detection was triggered with a real UUID
                        verify(contradictionDetector).checkAndPersistAsync(
                                        eq("France's capital is Paris."),
                                        eq(mockEmbedding),
                                        any(UUID.class)
                        );

                        // Verify markResolved was NOT called (only in MERGE flow)
                        verify(contradictionDetector, never()).markResolvedAsync(any(), any());
                }
        }

        @Test
        @DisplayName("MERGE flow should trigger both contradiction check and mark old as resolved")
        void mergeFlowShouldTriggerContradictionCheckAndResolve() {
                UUID kbId = UUID.randomUUID();
                UUID existingId = UUID.randomUUID();
                String content = "The capital of France is Lyon.";
                float[] mockEmbedding = new float[] { 0.1f, 0.2f, 0.3f };
                float[] mergedEmbedding = new float[] { 0.15f, 0.25f, 0.35f };

                ChunkingService.CompressedChunk compressed = new ChunkingService.CompressedChunk(
                                "France's capital is Lyon.", List.of("France", "Lyon"), "Geography", null);

                KnowledgeBase mockKb = mock(KnowledgeBase.class);
                lenient().when(mockKb.getId()).thenReturn(kbId);
                when(entityManager.getReference(KnowledgeBase.class, kbId)).thenReturn(mockKb);
                when(chunkingService.compressPrompt(content)).thenReturn(compressed);

                // Similar chunk found → MERGE path
                Object[] match = new Object[] { existingId, "France's capital is Paris.", 0.92 };
                when(contextRepository.findHighlySimilar(anyString(), anyDouble()))
                                .thenReturn(Collections.singletonList(match));

                // Existing context to merge into
                Context existingContext = new Context();
                existingContext.setId(existingId);
                existingContext.setTextChunk("France's capital is Paris.");
                existingContext.setVectorEmbedding(new float[] { 0.1f, 0.2f, 0.3f });
                existingContext.setMetadata(objectMapper.createObjectNode().put("topic", "Geography"));
                when(entityManager.find(Context.class, existingId)).thenReturn(existingContext);

                // Mock extraction to avoid NPE
                ExtractionService.ExtractionResult result = new ExtractionService.ExtractionResult();
                result.setEntities(new java.util.ArrayList<>());
                result.setRelations(new java.util.ArrayList<>());
                result.setMetadata(new ExtractionService.ExtractedMetadata());
                lenient().when(extractionService.extractFromText(anyString())).thenReturn(result);

                try (MockedStatic<LLMProvider> llm = mockStatic(LLMProvider.class)) {
                        llm.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(mergedEmbedding);
                        llm.when(() -> LLMProvider.callLLM(anyString()))
                                        .thenReturn("Merged: France's capital is Lyon (updated).");

                        ingestionWorker.processKnowledgeBase(kbId, content);

                        // Verify contradiction detection on merged text
                        verify(contradictionDetector).checkAndPersistAsync(
                                        anyString(), // merged text
                                        any(float[].class), // merged embedding
                                        eq(existingId));

                        // Verify old contradictions marked as resolved
                        verify(contradictionDetector).markResolvedAsync(
                                        eq(existingId), eq(existingId));
                }
        }
}
