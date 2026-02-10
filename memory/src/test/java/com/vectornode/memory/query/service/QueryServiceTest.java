package com.vectornode.memory.query.service;

import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.entity.Relation;
import com.vectornode.memory.entity.enums.ConverserRole;
import com.vectornode.memory.query.dto.request.QueryRequest;
import com.vectornode.memory.query.dto.response.QueryResponse;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    // ==================== CONTEXT TESTS ====================

    @Test
    void searchContexts_ShouldReturnResults_WhenMatchesFound() {
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
                .thenReturn(Collections.singletonList(mockRow));

        QueryRequest request = new QueryRequest();
        request.setQuery(query);
        request.setLimit(5);

        QueryResponse response = queryService.searchContexts(request);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals(0.95, response.getResults().get(0).getScore());
    }

    @Test
    void getContextsByKnowledgeBase_ShouldReturnContexts() {
        UUID kbId = UUID.randomUUID();
        Context context = new Context();
        context.setId(UUID.randomUUID());
        context.setTextChunk("Sample Text");
        context.setChunkIndex(1);

        when(contextRepository.findByKnowledgeBaseId(kbId)).thenReturn(List.of(context));

        QueryResponse response = queryService.getContextsByKnowledgeBase(kbId);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals(1.0, response.getResults().get(0).getScore());
        assertEquals("CHUNK", response.getResults().get(0).getType());
    }

    @Test
    void getRecentContexts_ShouldReturnContexts() {
        Context context = new Context();
        context.setId(UUID.randomUUID());
        context.setTextChunk("Recent Text");

        when(contextRepository.findRecentContexts(7)).thenReturn(List.of(context));

        QueryResponse response = queryService.getRecentContexts(7);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
    }

    @Test
    void searchRecentContexts_ShouldReturnResultsWithScores() {
        String query = "recent query";
        float[] dummyEmbedding = new float[] { 0.1f };
        UUID contextId = UUID.randomUUID();

        llmProviderMock.when(() -> LLMProvider.getEmbedding(eq(query)))
                .thenReturn(dummyEmbedding);

        Object[] mockRow = new Object[] { contextId, "Recent Chunk", 0, 0.85 };

        when(contextRepository.findRecentSimilarWithScore(eq(7), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(mockRow));

        QueryRequest request = new QueryRequest();
        request.setQuery(query);
        request.setLimit(5);

        QueryResponse response = queryService.searchRecentContexts(request, 7);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals(0.85, response.getResults().get(0).getScore());
    }

    @Test
    void getSiblingContexts_ShouldReturnSiblings() {
        UUID contextId = UUID.randomUUID();
        Context sibling = new Context();
        sibling.setId(UUID.randomUUID());
        sibling.setTextChunk("Sibling Text");
        sibling.setChunkIndex(2);

        when(contextRepository.findSiblingContexts(contextId)).thenReturn(List.of(sibling));

        QueryResponse response = queryService.getSiblingContexts(contextId);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals("SIBLING_CHUNK", response.getResults().get(0).getType());
    }

    // ==================== ENTITY TESTS ====================

    @Test
    void searchEntities_ShouldReturnResults_WhenEntitiesFound() {
        String query = "Elon Musk";
        float[] dummyEmbedding = new float[] { 0.5f };
        UUID entityId = UUID.randomUUID();

        llmProviderMock.when(() -> LLMProvider.getEmbedding(eq(query)))
                .thenReturn(dummyEmbedding);

        Object[] mockRow = new Object[] { entityId, "Elon Musk", "PERSON", "CEO", 0.88 };

        when(entityRepository.findSimilarEntitiesWithScore(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(mockRow));

        QueryRequest request = new QueryRequest();
        request.setQuery(query);

        QueryResponse response = queryService.searchEntities(request);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals("ENTITY", response.getResults().get(0).getType());
    }

    @Test
    void getEntityByName_ShouldReturnEntity() {
        String name = "Java";
        RagEntity entity = new RagEntity();
        entity.setName(name);
        when(entityRepository.findByName(name)).thenReturn(Optional.of(entity));

        Optional<RagEntity> result = queryService.getEntityByName(name);

        assertTrue(result.isPresent());
        assertEquals(name, result.get().getName());
    }

    @Test
    void disambiguateEntity_ShouldReturnEntity() {
        String entityName = "Apple";
        String context = "Technology company";
        float[] dummyEmbedding = new float[] { 0.9f };
        RagEntity entity = new RagEntity();
        entity.setName("Apple Inc.");

        llmProviderMock.when(() -> LLMProvider.getEmbedding(eq(context)))
                .thenReturn(dummyEmbedding);

        when(entityRepository.disambiguateEntity(eq(entityName), anyString()))
                .thenReturn(Optional.of(entity));

        Optional<RagEntity> result = queryService.disambiguateEntity(entityName, context);

        assertTrue(result.isPresent());
        assertEquals("Apple Inc.", result.get().getName());
    }

    @Test
    void getContextsForEntity_ShouldReturnContexts() {
        UUID entityId = UUID.randomUUID();
        Object[] mockRow = new Object[] { UUID.randomUUID(), "Context mentioning entity" };

        when(entityRepository.findContextsForEntity(entityId)).thenReturn(Collections.singletonList(mockRow));

        QueryResponse response = queryService.getContextsForEntity(entityId);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals("ENTITY_CONTEXT", response.getResults().get(0).getType());
    }

    @Test
    void mergeEntities_ShouldCallRepository() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        queryService.mergeEntities(source, target);

        verify(entityRepository).mergeEntities(source, target);
    }

    // ==================== KNOWLEDGE BASE TESTS ====================

    @Test
    void searchHistory_ShouldReturnResults() {
        String query = "past conversion";
        float[] dummyEmbedding = new float[] { 0.1f };

        llmProviderMock.when(() -> LLMProvider.getEmbedding(eq(query)))
                .thenReturn(dummyEmbedding);

        Object[] mockRow = new Object[] { UUID.randomUUID(), "Chat log", "user1", "bot", 0.75 };

        when(knowledgeBaseRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(mockRow));

        QueryRequest request = new QueryRequest();
        request.setQuery(query);

        QueryResponse response = queryService.searchHistory(request);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals(0.75, response.getResults().get(0).getScore());
    }

    @Test
    void getHistoryByUser_ShouldReturnHistory() {
        String uid = "user123";
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(UUID.randomUUID());
        kb.setContent("History");
        kb.setConverser(ConverserRole.AGENT);

        when(knowledgeBaseRepository.findByUidOrderByCreatedAtDesc(uid)).thenReturn(List.of(kb));

        QueryResponse response = queryService.getHistoryByUser(uid);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
    }

    @Test
    void deleteUserData_ShouldCallRepository() {
        String uid = "user_delete";
        queryService.deleteUserData(uid);
        verify(knowledgeBaseRepository).deleteByUid(uid);
    }

    // ==================== RELATION TESTS ====================

    @Test
    void getOutgoingConnections_ShouldReturnRelations() {
        UUID entityId = UUID.randomUUID();
        Object[] mockRow = new Object[] { "KNOWS", "Target Entity", 0.8 };

        when(relationRepository.findOutgoingRelations(entityId)).thenReturn(Collections.singletonList(mockRow));

        QueryResponse response = queryService.getOutgoingConnections(entityId);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals("RELATION", response.getResults().get(0).getType());
    }

    @Test
    void getTwoHopConnections_ShouldReturnEntityNames() {
        UUID entityId = UUID.randomUUID();
        List<String> connections = List.of("FriendOfFriend");

        when(relationRepository.findTwoHopConnections(entityId)).thenReturn(connections);

        QueryResponse response = queryService.getTwoHopConnections(entityId);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertEquals(0.5, response.getResults().get(0).getScore()); // Score fixed to 0.5
    }

    @Test
    void getTopRelations_ShouldReturnRelations() {
        Object[] mockRow = new Object[] { "Source", "LOVES", "Target", 0.9 };

        when(relationRepository.findTopRelations(10)).thenReturn(Collections.singletonList(mockRow));

        QueryResponse response = queryService.getTopRelations(10);

        assertNotNull(response);
        assertEquals(1, response.getResults().size());
        assertTrue(response.getResults().get(0).getContent().contains("->"));
    }

    // ==================== HYBRID SEARCH TEST ====================

    @Test
    void hybridSearch_ShouldCombineResults() {
        String query = "Complex query";
        UUID contextId = UUID.randomUUID();

        llmProviderMock.when(() -> LLMProvider.getEmbedding(anyString()))
                .thenReturn(new float[] { 0.1f });

        // Context Result
        Object[] contextRow = new Object[] { contextId, "Context text", 0, 0.9 };
        when(contextRepository.findSimilarWithScore(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(contextRow));

        // Linked Entity Result
        RagEntity mockEntity = new RagEntity();
        mockEntity.setId(UUID.randomUUID());
        mockEntity.setName("Linked");
        when(entityRepository.findEntitiesForContext(contextId)).thenReturn(List.of(mockEntity));

        // Direct Entity Result
        when(entityRepository.findSimilarEntitiesWithScore(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        QueryRequest request = new QueryRequest();
        request.setQuery(query);

        QueryResponse response = queryService.hybridSearch(request);

        assertEquals(2, response.getResults().size()); // 1 context + 1 linked entity
    }
}
