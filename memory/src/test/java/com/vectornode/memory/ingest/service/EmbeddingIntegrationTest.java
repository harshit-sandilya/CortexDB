package com.vectornode.memory.ingest.service;

import com.vectornode.memory.config.LLMProvider;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for embedding generation using real LLM API calls.
 * These tests require a valid GEMINI_API_KEY in environment or .env file.
 * 
 * Run with: mvn test -Dtest=EmbeddingIntegrationTest
 */
@DisplayName("Embedding Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmbeddingIntegrationTest {

    private static String apiKey;
    private static boolean initialized = false;

    @BeforeAll
    static void setUpLLMProvider() {
        Map<String, String> envVars = loadEnvFile();

        apiKey = envVars.get("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }

        if (apiKey != null && !apiKey.isEmpty()) {
            String chatModel = envVars.get("GEMINI_CHAT_MODEL");
            if (chatModel == null || chatModel.isEmpty()) {
                chatModel = System.getenv("GEMINI_CHAT_MODEL");
                if (chatModel == null || chatModel.isEmpty()) {
                    chatModel = "gemini-2.0-flash";
                }
            }

            String embedModel = envVars.get("GEMINI_EMBED_MODEL");
            if (embedModel == null || embedModel.isEmpty()) {
                embedModel = System.getenv("GEMINI_EMBED_MODEL");
                if (embedModel == null || embedModel.isEmpty()) {
                    embedModel = "gemini-embedding-001";
                }
            }

            try {
                new LLMProvider("GEMINI", apiKey, null, chatModel, embedModel);
                initialized = true;
                System.out.println("LLMProvider initialized for embedding tests");
            } catch (Exception e) {
                System.err.println("Failed to initialize LLMProvider: " + e.getMessage());
            }
        } else {
            System.out.println("Skipping integration tests - GEMINI_API_KEY not found");
        }
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> envVars = new HashMap<>();
        Path envPath = Paths.get("").toAbsolutePath();

        while (envPath != null) {
            Path envFile = envPath.resolve(".env");
            if (Files.exists(envFile)) {
                try {
                    Files.lines(envFile).forEach(line -> {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                            int eqIndex = line.indexOf("=");
                            String key = line.substring(0, eqIndex).trim();
                            String value = line.substring(eqIndex + 1).trim();
                            envVars.put(key, value);
                        }
                    });
                    break;
                } catch (IOException e) {
                    // Ignore
                }
            }
            envPath = envPath.getParent();
        }
        return envVars;
    }

    private void assumeInitialized() {
        Assumptions.assumeTrue(initialized,
                "Skipping: LLMProvider not initialized (GEMINI_API_KEY missing)");
    }

    @Test
    @Order(1)
    @DisplayName("Should generate embeddings with correct dimensions")
    void shouldGenerateEmbeddingsWithCorrectDimensions() {
        assumeInitialized();

        String text = "This is a test sentence for embedding generation.";

        float[] embedding = LLMProvider.getEmbedding(text);

        assertThat(embedding).isNotNull();
        assertThat(embedding.length).isGreaterThan(0);

        // Gemini text-embedding-004 typically produces 768-dimensional vectors
        System.out.printf("Generated embedding with %d dimensions%n", embedding.length);

        // First few values should be valid floats
        assertThat(Float.isFinite(embedding[0])).isTrue();
        assertThat(Float.isFinite(embedding[embedding.length - 1])).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("Should generate different embeddings for different texts")
    void shouldGenerateDifferentEmbeddingsForDifferentTexts() {
        assumeInitialized();

        String text1 = "The quick brown fox jumps over the lazy dog.";
        String text2 = "Artificial intelligence is transforming technology.";

        float[] embedding1 = LLMProvider.getEmbedding(text1);
        float[] embedding2 = LLMProvider.getEmbedding(text2);

        assertThat(embedding1).isNotNull();
        assertThat(embedding2).isNotNull();
        assertThat(embedding1.length).isEqualTo(embedding2.length);

        // Calculate cosine similarity - should be different for different texts
        double similarity = cosineSimilarity(embedding1, embedding2);
        System.out.printf("Cosine similarity between different texts: %.4f%n", similarity);

        // Different texts should have similarity less than 0.95 (not identical)
        assertThat(similarity).as("Different texts should have different embeddings")
                .isLessThan(0.95);
    }

    @Test
    @Order(3)
    @DisplayName("Should generate similar embeddings for semantically similar texts")
    void shouldGenerateSimilarEmbeddingsForSimilarTexts() {
        assumeInitialized();

        String text1 = "The cat is sleeping on the couch.";
        String text2 = "A cat sleeps on the sofa.";

        float[] embedding1 = LLMProvider.getEmbedding(text1);
        float[] embedding2 = LLMProvider.getEmbedding(text2);

        assertThat(embedding1).isNotNull();
        assertThat(embedding2).isNotNull();

        double similarity = cosineSimilarity(embedding1, embedding2);
        System.out.printf("Cosine similarity between similar texts: %.4f%n", similarity);

        // Similar texts should have high similarity (> 0.7)
        assertThat(similarity).as("Similar texts should have similar embeddings")
                .isGreaterThan(0.7);
    }

    @Test
    @Order(4)
    @DisplayName("Should handle long text for embedding")
    void shouldHandleLongTextForEmbedding() {
        assumeInitialized();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("This is sentence number ").append(i).append(". ");
        }
        String longText = sb.toString();

        long startTime = System.currentTimeMillis();
        float[] embedding = LLMProvider.getEmbedding(longText);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(embedding).isNotNull();
        assertThat(embedding.length).isGreaterThan(0);

        System.out.printf("Generated embedding for long text (%d chars) in %d ms%n",
                longText.length(), duration);
        System.out.printf("   Dimensions: %d%n", embedding.length);
    }

    @Test
    @Order(5)
    @DisplayName("Should generate embeddings for entity names")
    void shouldGenerateEmbeddingsForEntityNames() {
        assumeInitialized();

        String entityText = "Google A multinational technology company specializing in Internet services";

        float[] embedding = LLMProvider.getEmbedding(entityText);

        assertThat(embedding).isNotNull();
        assertThat(embedding.length).isGreaterThan(100); // Should have meaningful dimensions

        System.out.printf("Entity embedding generated: %d dimensions%n", embedding.length);
    }

    @Test
    @Order(6)
    @DisplayName("Should measure embedding generation performance")
    void shouldMeasureEmbeddingPerformance() {
        assumeInitialized();

        String text = "Performance test for embedding generation using the Gemini API.";

        // Warm up
        LLMProvider.getEmbedding("warmup");

        // Measure
        long startTime = System.currentTimeMillis();
        int iterations = 3;
        for (int i = 0; i < iterations; i++) {
            LLMProvider.getEmbedding(text + " iteration " + i);
        }
        long totalDuration = System.currentTimeMillis() - startTime;
        long avgDuration = totalDuration / iterations;

        System.out.printf("Average embedding generation time: %d ms (over %d iterations)%n",
                avgDuration, iterations);

        // Should complete within reasonable time per call
        assertThat(avgDuration).as("Each embedding should complete within 10 seconds")
                .isLessThan(10000);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length)
            return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0)
            return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
