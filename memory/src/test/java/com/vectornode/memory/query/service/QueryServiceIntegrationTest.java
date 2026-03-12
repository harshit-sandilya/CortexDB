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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

// Note: When running via 'docker compose run backend-tests', the compose.yaml
// provides database connection via SPRING_DATASOURCE_* environment variables.
// TestcontainersConfiguration is not imported because Docker-in-Docker is not supported.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoints.enabled-by-default=false",
        "management.endpoint.health.enabled=false",
        // Dummy Azure OpenAI endpoint to satisfy autoconfiguration
        "spring.ai.azure.openai.endpoint=https://dummy.openai.azure.com",
        "spring.ai.azure.openai.api-key=dummy-key",
        "spring.ai.openai.api-key=dummy-key",
        "spring.ai.openai.base-url=https://dummy.openai.com"
})
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

    /**
     * Creates a 768-dimensional vector filled with the given value
     */
    private float[] create768Vector(float value) {
        float[] vector = new float[768];
        Arrays.fill(vector, value);
        return vector;
    }

    /**
     * Creates a 768-dimensional vector with a pattern for similarity testing
     */
    private float[] create768VectorWithPattern(float baseValue, int patternIndex) {
        float[] vector = new float[768];
        for (int i = 0; i < vector.length; i++) {
            // Create a simple pattern based on index
            vector[i] = baseValue + (float) Math.sin((i + patternIndex) * 0.01);
        }
        return vector;
    }

    @BeforeEach
    void setUp() {
        // Setup initial data with 768-dimensional vectors to match schema
        kb = new KnowledgeBase();
        kb.setUid("user-1");
        kb.setConverser(ConverserRole.USER);
        kb.setContent("Test content about graph databases");
        kb.setVectorEmbedding(create768Vector(0.1f));
        // Note: createdAt is set automatically by @PrePersist
        knowledgeBaseRepository.save(kb);

        context1 = new Context();
        context1.setKnowledgeBase(kb);
        context1.setTextChunk("Graph databases use nodes and edges.");
        // Create a vector similar to what we'll use for queries
        context1.setVectorEmbedding(create768VectorWithPattern(0.9f, 0));
        context1.setChunkIndex(0);
        contextRepository.save(context1);

        Context context2 = new Context();
        context2.setKnowledgeBase(kb);
        context2.setTextChunk("Relational databases use tables.");
        // Create a dissimilar vector
        context2.setVectorEmbedding(create768VectorWithPattern(-0.5f, 100));
        context2.setChunkIndex(1);
        contextRepository.save(context2);

        entityA = new RagEntity();
        entityA.setName("GraphDB");
        entityA.setType("TECHNOLOGY");
        entityA.setDescription("A type of NoSQL database");
        entityA.setVectorEmbedding(create768Vector(0.5f));
        entityRepository.save(entityA);

        entityB = new RagEntity();
        entityB.setName("Nodes");
        entityB.setType("CONCEPT");
        entityB.setDescription("Entities in a graph");
        entityB.setVectorEmbedding(create768VectorWithPattern(0.5f, 50));
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
            // Use a 768-dimensional vector with similar pattern to context1
            float[] queryVector = create768VectorWithPattern(0.9f, 0);
            mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(queryVector);

            QueryRequest request = QueryRequest.builder()
                    .query("Graph info")
                    .limit(5)
                    .build();

            QueryResponse response = queryService.searchContexts(request);

            assertThat(response.getResults()).isNotEmpty();
            // Should find context1 first (most similar)
            QueryResponse.SearchResult firstResult = response.getResults().get(0);
            assertThat(firstResult.getContent()).contains("Graph databases");
            assertThat(firstResult.getType()).isEqualTo("CHUNK");
        }
    }

    @Test
    @DisplayName("Should find entities by name")
    void shouldFindEntitiesByName() {
        try (MockedStatic<LLMProvider> mockedLLM = mockStatic(LLMProvider.class)) {
            mockedLLM.when(() -> LLMProvider.getEmbedding(anyString())).thenReturn(create768Vector(0.5f));

            QueryRequest request = QueryRequest.builder()
                    .query("GraphDB")
                    .limit(5)
                    .build();

            QueryResponse response = queryService.searchEntities(request);

            assertThat(response.getResults()).isNotEmpty();
            // Check if any result contains "GraphDB"
            boolean hasGraphDB = response.getResults().stream()
                    .anyMatch(r -> r.getContent().contains("GraphDB"));
            assertThat(hasGraphDB).isTrue();
        }
    }

    @Test
    @DisplayName("Should traverse outgoing connections")
    void shouldTraverseOutgoingConnections() {
        QueryResponse response = queryService.getOutgoingConnections(entityA.getId());

        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getResults().get(0).getContent()).isEqualTo("Nodes");
        assertThat(response.getResults().get(0).getMetadata()).containsEntry("relationType", "USES");
    }

    @Test
    @DisplayName("Should traverse incoming connections")
    void shouldTraverseIncomingConnections() {
        QueryResponse response = queryService.getIncomingConnections(entityB.getId());

        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getResults().get(0).getContent()).isEqualTo("GraphDB");
        assertThat(response.getResults().get(0).getMetadata()).containsEntry("relationType", "USES");
    }

    @Test
    @DisplayName("Should retrieve recent history")
    void shouldRetrieveRecentHistory() {
        QueryResponse response = queryService.getHistoryByUser("user-1");

        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getQuery()).isEqualTo("user:user-1");
        // The content field for KB search results is typically the 'content' text
        assertThat(response.getResults().get(0).getContent()).contains("Test content");
    }
}