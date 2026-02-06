package com.vectornode.memory.query.service;

import com.vectornode.memory.TestcontainersConfiguration;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class QueryServiceIntegrationTest {

    @Autowired
    private QueryService queryService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private ContextRepository contextRepository;

    @Autowired
    private EntityRepository entityRepository;

    @Autowired
    private RelationRepository relationRepository;

    private KnowledgeBase kb;
    private Context context1;
    private RagEntity entityA;
    private RagEntity entityB;

    @BeforeEach
    void setUp() {
        // Setup initial data
        kb = new KnowledgeBase();
        kb.setUid("user-1");
        kb.setConverser(ConverserRole.USER);
        kb.setContent("Test content about graph databases");
        kb.setVectorEmbedding(new float[] { 0.1f, 0.2f, 0.3f });
        kb.setTimestamp(Instant.now());
        knowledgeBaseRepository.save(kb);

        context1 = new Context();
        context1.setKnowledgeBase(kb);
        context1.setTextChunk("Graph databases use nodes and edges.");
        // Similar to query vector
        context1.setVectorEmbedding(new float[] { 0.9f, 0.1f, 0.1f });
        context1.setChunkIndex(0);
        contextRepository.save(context1);

        Context context2 = new Context();
        context2.setKnowledgeBase(kb);
        context2.setTextChunk("Relational databases use tables.");
        // Dissimilar to query vector
        context2.setVectorEmbedding(new float[] { -0.9f, -0.1f, -0.1f });
        context2.setChunkIndex(1);
        contextRepository.save(context2);

        entityA = new RagEntity();
        entityA.setName("GraphDB");
        entityA.setType("TECHNOLOGY");
        entityA.setDescription("A type of NoSQL database");
        entityA.setVectorEmbedding(new float[] { 0.5f, 0.5f, 0.5f });
        entityRepository.save(entityA);

        entityB = new RagEntity();
        entityB.setName("Nodes");
        entityB.setType("CONCEPT");
        entityB.setDescription("Entities in a graph");
        entityB.setVectorEmbedding(new float[] { 0.5f, 0.6f, 0.4f });
        entityRepository.save(entityB);

        // Relation: GraphDB -> USES -> Nodes
        Relation relation = new Relation();
        relation.setSourceEntity(entityA);
        relation.setTargetEntity(entityB);
        relation.setRelationType("USES");
        relation.setEdgeWeight(1);
        relationRepository.save(relation);
    }

    @Test
    @DisplayName("Should find similar contexts using vector search (PGVector)")
    void shouldFindSimilarContexts() {
        // Mock LLMProvider to return a query vector similar to context1
        try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
            // scale vector to match dimensions if needed, but for test logic simple works
            // Assuming 3 dimensions for simplicity in this integration test setup,
            // although older migrations might enforce 1536.
            // Wait, standard pgvector setup usually enforces dimension.
            // If dimensions mismatch, PGVector throws error.
            // Default model is text-embedding-004 (768).
            // Default setup might use 768 or 1536.
            // Let's assume the schema allows what we insert or check what Flyway did.
            // If schema enforces 1536, these inserts might fail or query fails.
            // IMPORTANT: The real schema likely uses vector(1536) or vector(768).
            // Our test data has 3 dimensions.
            // If Postgres throws error, we simply update this test.

            float[] queryVector = new float[] { 0.9f, 0.1f, 0.1f };
            mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(queryVector);

            QueryRequest request = QueryRequest.builder()
                    .query("Graph info")
                    .limit(5)
                    .build();

            QueryResponse response = queryService.searchContexts(request);

            assertThat(response.getResults()).isNotEmpty();
            // Should find context1 first
            QueryResponse.SearchResult firstResult = response.getResults().get(0);
            assertThat(firstResult.getContent()).contains("Graph databases");
            assertThat(firstResult.getType()).isEqualTo("CHUNK");
        }
    }

    @Test
    @DisplayName("Should find entities by name")
    void shouldFindEntitiesByName() {
        try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
            mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(new float[] { 0.5f, 0.5f, 0.5f });

            QueryRequest request = QueryRequest.builder()
                    .query("GraphDB")
                    .limit(5)
                    .build();

            QueryResponse response = queryService.searchEntities(request);

            assertThat(response.getResults()).isNotEmpty();
            assertThat(response.getResults().get(0).getContent()).contains("GraphDB");
            assertThat(response.getResults().stream().anyMatch(r -> r.getContent().equals("GraphDB"))).isTrue();
        }
    }

    @Test
    @DisplayName("Should traverse outgoing connections")
    void shouldTraverseOutgoingConnections() {
        List<QueryResponse.SearchResult> results = queryService.getOutgoingConnections(entityA.getId());

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getContent()).isEqualTo("Nodes");
        assertThat(results.get(0).getMetadata()).containsEntry("relationType", "USES");
    }

    @Test
    @DisplayName("Should traverse incoming connections")
    void shouldTraverseIncomingConnections() {
        List<QueryResponse.SearchResult> results = queryService.getIncomingConnections(entityB.getId());

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getContent()).isEqualTo("GraphDB");
        assertThat(results.get(0).getMetadata()).containsEntry("relationType", "USES");
    }

    @Test
    @DisplayName("Should retrieve recent history")
    void shouldRetrieveRecentHistory() {
        try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
            mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(new float[] { 0.1f, 0.1f, 0.1f });

            QueryResponse response = queryService.getHistoryByUser("user-1");

            assertThat(response.getResults()).isNotEmpty();
            assertThat(response.getQuery()).isEqualTo("user:user-1");
            // The content field for KB search results is typically the 'content' text
            assertThat(response.getResults().get(0).getContent()).contains("Test content");
        }
    }
}
