package com.vectornode.memory.ingest.service;

import com.vectornode.memory.config.LLMProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ExtractionService using real LLM API calls.
 * These tests require a valid GEMINI_API_KEY in environment or .env file.
 * 
 * Run with: mvn test -Dtest=ExtractionServiceIntegrationTest
 */
@DisplayName("ExtractionService Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExtractionServiceIntegrationTest {

    private static ExtractionService extractionService;
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
                extractionService = new ExtractionService();
                initialized = true;
                System.out.println("✅ LLMProvider initialized for integration tests");
            } catch (Exception e) {
                System.err.println("❌ Failed to initialize LLMProvider: " + e.getMessage());
            }
        } else {
            System.out.println("⏭ Skipping integration tests - GEMINI_API_KEY not found");
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
    @DisplayName("Should extract entities from simple text using real LLM")
    void shouldExtractEntitiesFromSimpleText() {
        assumeInitialized();

        String text = "John Smith works at Google as a software engineer in Mountain View, California.";

        ExtractionService.ExtractionResult result = extractionService.extractFromText(text);

        assertThat(result).isNotNull();
        assertThat(result.getEntities()).isNotEmpty();

        // Verify we got meaningful entities
        boolean hasPersonEntity = result.getEntities().stream()
                .anyMatch(e -> e.getName().toLowerCase().contains("john") ||
                        e.getType().equalsIgnoreCase("PERSON"));
        boolean hasOrgEntity = result.getEntities().stream()
                .anyMatch(e -> e.getName().toLowerCase().contains("google") ||
                        e.getType().equalsIgnoreCase("ORGANIZATION"));

        System.out.println("📋 Extracted entities:");
        result.getEntities()
                .forEach(e -> System.out.printf("   - %s (%s): %s%n", e.getName(), e.getType(), e.getDescription()));

        assertThat(hasPersonEntity || hasOrgEntity)
                .as("Should extract at least John or Google as entities")
                .isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("Should extract relations from text using real LLM")
    void shouldExtractRelationsFromText() {
        assumeInitialized();

        String text = "Elon Musk is the CEO of Tesla and SpaceX. Tesla manufactures electric vehicles.";

        ExtractionService.ExtractionResult result = extractionService.extractFromText(text);

        assertThat(result).isNotNull();

        System.out.println("📋 Extracted entities:");
        result.getEntities().forEach(e -> System.out.printf("   - %s (%s)%n", e.getName(), e.getType()));

        System.out.println("🔗 Extracted relations:");
        result.getRelations().forEach(r -> System.out.printf("   - %s --[%s]--> %s%n",
                r.getSourceName(), r.getRelationType(), r.getTargetName()));

        // LLM should extract relationships between entities
        if (!result.getRelations().isEmpty()) {
            assertThat(result.getRelations().get(0).getSourceName()).isNotBlank();
            assertThat(result.getRelations().get(0).getTargetName()).isNotBlank();
            assertThat(result.getRelations().get(0).getRelationType()).isNotBlank();
        }
    }

    @Test
    @Order(3)
    @DisplayName("Should handle complex multi-entity text")
    void shouldHandleComplexText() {
        assumeInitialized();

        String text = """
                Apple Inc., founded by Steve Jobs, Steve Wozniak, and Ronald Wayne in 1976,
                is headquartered in Cupertino, California. Tim Cook has been the CEO since 2011.
                Apple's main competitors include Microsoft, Google, and Samsung.
                """;

        ExtractionService.ExtractionResult result = extractionService.extractFromText(text);

        assertThat(result).isNotNull();
        assertThat(result.getEntities()).hasSizeGreaterThanOrEqualTo(2);

        System.out.println("📋 Complex extraction results:");
        System.out.println("   Entities: " + result.getEntities().size());
        System.out.println("   Relations: " + result.getRelations().size());

        result.getEntities().forEach(e -> System.out.printf("   - %s (%s)%n", e.getName(), e.getType()));
    }

    @Test
    @Order(4)
    @DisplayName("Should return empty result for irrelevant text")
    void shouldHandleIrrelevantText() {
        assumeInitialized();

        String text = "The weather is nice today. It might rain tomorrow.";

        ExtractionService.ExtractionResult result = extractionService.extractFromText(text);

        assertThat(result).isNotNull();
        // This text has no clear entities - result might be empty or minimal
        System.out.println("📋 Irrelevant text extraction:");
        System.out.println("   Entities: " + result.getEntities().size());
        System.out.println("   Relations: " + result.getRelations().size());
    }

    @Test
    @Order(5)
    @DisplayName("Should measure extraction performance")
    void shouldMeasureExtractionPerformance() {
        assumeInitialized();

        String text = "Microsoft was founded by Bill Gates and Paul Allen in 1975.";

        long startTime = System.currentTimeMillis();
        ExtractionService.ExtractionResult result = extractionService.extractFromText(text);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(result).isNotNull();

        System.out.printf("⏱ Extraction completed in %d ms%n", duration);
        System.out.println("   Entities: " + result.getEntities().size());
        System.out.println("   Relations: " + result.getRelations().size());

        // Performance expectation: should complete within reasonable time
        assertThat(duration).as("Extraction should complete within 30 seconds")
                .isLessThan(30000);
    }
}
