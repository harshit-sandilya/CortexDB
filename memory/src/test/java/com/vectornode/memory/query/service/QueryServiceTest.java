package com.vectornode.memory.query.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.query.dto.QueryRequest;
import com.vectornode.memory.query.dto.QueryResponse;
import com.vectornode.memory.query.repository.ContextRepository;
import com.vectornode.memory.query.repository.EntityRepository;
import com.vectornode.memory.query.repository.KnowledgeBaseRepository;
import com.vectornode.memory.query.repository.RelationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @Mock
    private ContextRepository contextRepository;
    @Mock
    private EntityRepository entityRepository;
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private RelationRepository relationRepository;

    @InjectMocks
    private QueryService queryService;

    private MockedStatic<LLMProvider> llmProviderMock;

    @BeforeEach
    void setUp() {
        llmProviderMock = Mockito.mockStatic(LLMProvider.class);
    }

    @AfterEach
    void tearDown() {
        if (llmProviderMock != null) {
            llmProviderMock.close();
        }
    }

    @Test
    void searchContexts_ShouldReturnResults_WhenMatchesFound() {
        // Arrange
        String query = "test query";
        float[] dummyEmbedding = new float[] { 0.1f, 0.2f, 0.3f };
        UUID contextId = UUID.randomUUID();

        llmProviderMock.when(() -> LLMProvider.getEmbedding(eq(query)))
                .thenReturn(dummyEmbedding);

        Object[] mockRow = new Object[] {
                contextId, // id
                "Chunk content", // text_chunk
                0, // chunk_index
                0.95 // score
        };

        when(contextRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(List.of(mockRow));

        QueryRequest request = new QueryRequest();
        request.setQuery(query);
        request.setLimit(5);

        // Act
        QueryResponse response = queryService.searchContexts(request);

        // Assert
        assertNotNull(response);
        assertEquals(query, response.getQuery());
        assertEquals(1, response.getResults().size());

        QueryResponse.SearchResult result = response.getResults().get(0);
        assertEquals(contextId, result.getId());
        assertEquals(0.95, result.getScore());
        assertEquals("CHUNK", result.getType());

        verify(contextRepository).findSimilarWithScore(anyString(), eq(5));
    }

    @Test
    void searchEntities_ShouldReturnResults_WhenEntitiesFound() {
        // Arrange
        String query = "Elon Musk";
        float[] dummyEmbedding = new float[] { 0.5f, 0.5f, 0.5f };
        UUID entityId = UUID.randomUUID();

        llmProviderMock.when(() -> LLMProvider.getEmbedding(eq(query)))
                .thenReturn(dummyEmbedding);

        Object[] mockRow = new Object[] {
                entityId, // id
                "Elon Musk", // name
                "PERSON", // type
                "CEO of Tesla", // description
                0.88 // score
        };

        when(entityRepository.findSimilarEntitiesWithScore(anyString(), anyInt()))
                .thenReturn(List.of(mockRow));

        QueryRequest request = new QueryRequest();
        request.setQuery(query);
        request.setLimit(3);

        // Act
        QueryResponse response = queryService.searchEntities(request);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals("ENTITY", response.getResults().get(0).getType());
        assertEquals("Elon Musk", response.getResults().get(0).getContent());
    }

    @Test
    void hybridSearch_ShouldCombineVectorAndGraphResults() {
        // Arrange
        String query = "Complex query";
        float[] dummyEmbedding = new float[] { 0.1f };
        UUID contextId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        llmProviderMock.when(() -> LLMProvider.getEmbedding(anyString()))
                .thenReturn(dummyEmbedding);

        // Step 1: Mock Vector Search on Contexts
        Object[] contextRow = new Object[] { contextId, "Context text", 0, 0.9 };
        when(contextRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(List.of(contextRow));

        // Step 2: Mock Entities in that Context
        RagEntity mockEntity = RagEntity.builder()
                .id(entityId)
                .name("Linked Entity")
                .type("TOPIC")
                .build();
        when(entityRepository.findEntitiesForContext(contextId))
                .thenReturn(List.of(mockEntity));

        // Step 3: Mock Vector Search on Entities directly
        when(entityRepository.findSimilarEntitiesWithScore(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        QueryRequest request = new QueryRequest();
        request.setQuery(query);
        request.setLimit(5);

        // Act
        QueryResponse response = queryService.hybridSearch(request);

        // Assert
        assertNotNull(response);
        // Should have 1 context + 1 linked entity = 2 results
        assertEquals(2, response.getResults().size());

        // Verify Context Result
        assertTrue(response.getResults().stream()
                .anyMatch(r -> r.getId().equals(contextId) && r.getType().equals("CHUNK")));

        // Verify Linked Entity Result (Score should be 0.9 * 0.8 = 0.72)
        assertTrue(response.getResults().stream()
                .anyMatch(r -> r.getId().equals(entityId) && r.getType().equals("LINKED_ENTITY")));
    }

    @Test
    void getTwoHopConnections_ShouldReturnEntityNames() {
        // Arrange
        UUID entityId = UUID.randomUUID();
        List<String> connections = List.of("Alice", "Bob");

        when(relationRepository.findTwoHopConnections(entityId))
                .thenReturn(connections);

        // Act
        QueryResponse response = queryService.getTwoHopConnections(entityId);

        // Assert
        assertNotNull(response);
        assertEquals("2hop:" + entityId, response.getQuery());
        assertEquals(2, response.getResults().size());
        assertEquals("TWO_HOP_ENTITY", response.getResults().get(0).getType());
    }

    @Test
    void getEntityByName_ShouldReturnEntity_WhenFound() {
        // Arrange
        String name = "Java";
        RagEntity entity = new RagEntity();
        entity.setName(name);

        when(entityRepository.findByName(name)).thenReturn(Optional.of(entity));

        // Act
        Optional<RagEntity> result = queryService.getEntityByName(name);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(name, result.get().getName());
    }
}
