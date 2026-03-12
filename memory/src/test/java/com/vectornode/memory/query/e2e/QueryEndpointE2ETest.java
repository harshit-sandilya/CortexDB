package com.vectornode.memory.query.e2e;

import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.entity.enums.ConverserRole;
import com.vectornode.memory.query.dto.response.QueryResponse;
import com.vectornode.memory.query.repository.ContextRepository;
import com.vectornode.memory.query.repository.KnowledgeBaseRepository;
import com.vectornode.memory.query.repository.EntityRepository;
import com.vectornode.memory.query.service.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End tests for the Query pipeline.
 * These tests use the real database and service layer to validate
 * the complete query flow from request to response.
 * 
 * Note: Tests that require embedding generation (searchContexts,
 * searchEntities,
 * searchHistory) are excluded because they depend on external LLM APIs which
 * may
 * return different vector dimensions than what's stored in the database schema.
 * 
 * Run with: docker compose run --rm backend-tests
 */
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
class QueryEndpointE2ETest {

    @Autowired
    private QueryService queryService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private ContextRepository contextRepository;

    @Autowired
    private EntityRepository entityRepository;

    private KnowledgeBase savedKb;
    private Context savedContext;
    private RagEntity savedEntity;

    @BeforeEach
    void setUp() {
        // Create test data with 768-dimensional vectors
        savedKb = new KnowledgeBase();
        savedKb.setUid("test-user-e2e");
        savedKb.setConverser(ConverserRole.USER);
        savedKb.setContent("Machine learning models require training data.");
        savedKb.setVectorEmbedding(create768Vector(0.5f));
        savedKb = knowledgeBaseRepository.save(savedKb);

        savedContext = new Context();
        savedContext.setKnowledgeBase(savedKb);
        savedContext.setTextChunk("Neural networks process data in layers.");
        savedContext.setChunkIndex(0);
        savedContext.setVectorEmbedding(create768VectorWithPattern(0.5f, 1));
        savedContext = contextRepository.save(savedContext);

        savedEntity = new RagEntity();
        savedEntity.setName("Neural Network");
        savedEntity.setType("CONCEPT");
        savedEntity.setDescription("A machine learning model inspired by biological neural networks");
        savedEntity.setVectorEmbedding(create768VectorWithPattern(0.5f, 2));
        savedEntity = entityRepository.save(savedEntity);
    }

    // ==================== ENTITY LOOKUP TESTS (no embedding required)
    // ====================

    @Test
    @DisplayName("E2E: getEntityByName should find entity by exact name")
    void getEntityByNameShouldFindEntity() {
        // When
        var result = queryService.getEntityByName("Neural Network");

        // Then
        assertTrue(result.isPresent(), "Entity should be found");
        assertEquals("Neural Network", result.get().getName());
        assertEquals("CONCEPT", result.get().getType());
    }

    @Test
    @DisplayName("E2E: getEntityByName should return empty for non-existent entity")
    void getEntityByNameShouldReturnEmptyForMissing() {
        // When
        var result = queryService.getEntityByName("NonExistentEntity12345");

        // Then
        assertTrue(result.isEmpty(), "Should return empty for non-existent entity");
    }

    @Test
    @DisplayName("E2E: getEntityByNameIgnoreCase should find entity case-insensitively")
    void getEntityByNameIgnoreCaseShouldFindEntity() {
        // When
        var result = queryService.getEntityByNameIgnoreCase("NEURAL NETWORK");

        // Then
        assertTrue(result.isPresent(), "Entity should be found case-insensitively");
        assertEquals("Neural Network", result.get().getName());
    }

    @Test
    @DisplayName("E2E: getEntityIdByName should return entity ID for existing entity")
    void getEntityIdByNameShouldReturnId() {
        // When
        var result = queryService.getEntityIdByName("Neural Network");

        // Then
        assertTrue(result.isPresent(), "Entity ID should be found");
        assertEquals(savedEntity.getId(), result.get());
    }

    @Test
    @DisplayName("E2E: getEntityIdByName should return empty for non-existent entity")
    void getEntityIdByNameShouldReturnEmptyForMissing() {
        // When
        var result = queryService.getEntityIdByName("NonExistentEntity");

        // Then
        assertTrue(result.isEmpty(), "Should return empty for non-existent entity");
    }

    // ==================== HISTORY BY USER TESTS (no embedding required)
    // ====================

    @Test
    @DisplayName("E2E: getHistoryByUser should return user's knowledge bases")
    void getHistoryByUserShouldReturnUserData() {
        // When
        QueryResponse response = queryService.getHistoryByUser("test-user-e2e");

        // Then
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getResults(), "Results list should not be null");
        assertFalse(response.getResults().isEmpty(), "User should have knowledge bases");
    }

    @Test
    @DisplayName("E2E: getHistoryByUser should return empty for unknown user")
    void getHistoryByUserShouldReturnEmptyForUnknownUser() {
        // When
        QueryResponse response = queryService.getHistoryByUser("unknown-user-xyz");

        // Then
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getResults().isEmpty(), "Unknown user should have no data");
    }

    // ==================== GRAPH TRAVERSAL TESTS (no embedding required)
    // ====================

    @Test
    @DisplayName("E2E: getOutgoingConnections should return response for entity")
    void getOutgoingConnectionsShouldReturnResponse() {
        // When
        QueryResponse response = queryService.getOutgoingConnections(savedEntity.getId());

        // Then
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getResults(), "Results list should not be null");
        // May be empty if no relations exist - that's valid
    }

    @Test
    @DisplayName("E2E: getIncomingConnections should return response for entity")
    void getIncomingConnectionsShouldReturnResponse() {
        // When
        QueryResponse response = queryService.getIncomingConnections(savedEntity.getId());

        // Then
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getResults(), "Results list should not be null");
    }

    // ==================== CONTEXTS BY KNOWLEDGE BASE (no embedding required)
    // ====================

    @Test
    @DisplayName("E2E: getContextsByKnowledgeBase should return contexts for KB")
    void getContextsByKnowledgeBaseShouldReturnContexts() {
        // When
        QueryResponse response = queryService.getContextsByKnowledgeBase(savedKb.getId());

        // Then
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getResults(), "Results list should not be null");
        assertFalse(response.getResults().isEmpty(), "KB should have contexts");
    }

    @Test
    @DisplayName("E2E: getSiblingContexts should return sibling contexts")
    void getSiblingContextsShouldReturnSiblings() {
        // When
        QueryResponse response = queryService.getSiblingContexts(savedContext.getId());

        // Then
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getResults(), "Results list should not be null");
    }

    // ==================== HELPER METHODS ====================

    private float[] create768Vector(float value) {
        float[] vector = new float[768];
        Arrays.fill(vector, value);
        return vector;
    }

    private float[] create768VectorWithPattern(float baseValue, int patternIndex) {
        float[] vector = new float[768];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = baseValue + (float) Math.sin((i + patternIndex) * 0.01);
        }
        return vector;
    }
}