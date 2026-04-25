package com.vectornode.memory.query.repository;

import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.enums.ConverserRole;
import com.vectornode.memory.config.LLMProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for vector search functionality at the repository level
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class VectorSearchIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchIntegrationTest.class);

    @Autowired
    private ContextRepository contextRepository;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Test
    void testVectorSearchAtRepositoryLevel() {
        log.info("🧪 Testing vector search at repository level...");

        // Clean database
        contextRepository.deleteAll();
        knowledgeBaseRepository.deleteAll();

        // Create test knowledge base
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUid("vector-test-user");
        kb.setConverser(ConverserRole.USER);
        kb.setContent("Vector search test knowledge base");
        knowledgeBaseRepository.save(kb);

        // Create test contexts with manual embeddings
        String[] testContents = {
            "Vector databases store and search high-dimensional embeddings efficiently.",
            "Pinecone, Weaviate, and Milvus are popular vector database solutions.",
            "Approximate nearest neighbor search enables fast similarity queries.",
            "Cosine similarity measures the angle between vectors in high-dimensional space.",
            "Hybrid search combines vector and keyword search for better results."
        };

        for (int i = 0; i < testContents.length; i++) {
            Context context = new Context();
            context.setKnowledgeBase(kb);
            context.setTextChunk(testContents[i]);
            context.setChunkIndex(i);

            // Generate real embedding using LLMProvider
            try {
                float[] embedding = LLMProvider.getEmbedding(testContents[i]);
                context.setVectorEmbedding(embedding);
                log.info("📊 Generated embedding for context {}: {} dimensions", i, embedding.length);
            } catch (Exception e) {
                log.error("❌ Failed to generate embedding for context {}: {}", i, e.getMessage());
                throw e;
            }

            contextRepository.save(context);
        }

        log.info("✅ Created {} test contexts with vector embeddings", testContents.length);

        // Now test vector search
        String query = "What are vector databases?";

        try {
            // Generate query embedding
            float[] queryEmbedding = LLMProvider.getEmbedding(query);
            String vectorString = vectorToString(queryEmbedding);

            log.info("🔍 Searching for: {}", query);
            log.info("📊 Query vector: {} dimensions", queryEmbedding.length);

            // Perform vector search
            List<Object[]> results = contextRepository.findSimilarWithScore(vectorString, 3);

            log.info("🎯 Vector search returned {} results:", results.size());

            assertNotNull(results, "Results should not be null");
            assertFalse(results.isEmpty(), "Should find similar contexts");

            // Log and verify results
            for (Object[] row : results) {
                UUID id = (UUID) row[0];
                String content = (String) row[1];
                double score = ((Number) row[3]).doubleValue();

                log.info("   - Context ID: {}, Score: {}, Content: {}",
                        id, score, content.substring(0, Math.min(50, content.length())) + "...");

                assertTrue(score > 0, "Similarity score should be positive");
                assertTrue(score <= 1.0, "Similarity score should be <= 1.0");
            }

            log.info("✅ Vector search at repository level working perfectly!");

        } catch (Exception e) {
            log.error("❌ Vector search failed: {}", e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private String vectorToString(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    @Test
    void testDatabaseConnectivity() {
        log.info("🧪 Testing basic database connectivity...");

        try {
            long contextCount = contextRepository.count();
            long kbCount = knowledgeBaseRepository.count();

            log.info("✅ Database connection successful!");
            log.info("   Contexts: {}", contextCount);
            log.info("   Knowledge Bases: {}", kbCount);

            if (contextCount == 0) {
                log.warn("⚠️  Database is empty! Vector search will not work without data.");
            } else {
                log.info("✅ Database has data, vector search should work.");
            }

        } catch (Exception e) {
            log.error("❌ Database connectivity failed: {}", e.getMessage());
            throw e;
        }
    }
}