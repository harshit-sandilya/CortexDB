package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vectornode.memory.config.LLMProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Uses LLM to extract entities, relations, and metadata from text.
 */
@Service
@Slf4j
public class ExtractionService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXTRACTION_PROMPT = """
            You are a knowledge graph extraction system. Extract entities, relations, and metadata from the following text.

            TEXT:
            %s

            Respond ONLY with valid JSON in this exact format (no markdown, no explanation):
            {
              "entities": [
                {"name": "Entity Name", "type": "PERSON|ORGANIZATION|LOCATION|CONCEPT|EVENT|OTHER", "description": "Brief description"}
              ],
              "relations": [
                {"source": "Source Entity Name", "target": "Target Entity Name", "relation": "RELATION_TYPE"}
              ],
              "metadata": {
                "topics": ["main topic 1", "main topic 2"],
                "keywords": ["keyword1", "keyword2", "keyword3"],
                "sentiment": "POSITIVE|NEGATIVE|NEUTRAL|MIXED",
                "language": "en",
                "contentType": "NARRATIVE|TECHNICAL|CONVERSATIONAL|FACTUAL|OTHER",
                "summary": "One sentence summary of the content"
              }
            }

            Rules:
            - Extract only clearly stated entities and relationships
            - Use simple, normalized entity names
            - Relation types should be uppercase with underscores (e.g., WORKS_FOR, LOCATED_IN, PART_OF)
            - For metadata, extract key topics, relevant keywords, and overall sentiment
            - If no entities found, return empty arrays
            - Metadata fields are optional but try to extract what you can
            """;

    /**
     * Extracts entities, relations, and metadata from the given text using LLM.
     *
     * @param text The source text.
     * @return Extraction result containing entities, relations, and metadata.
     */
    public ExtractionResult extractFromText(String text) {
        return extractFromText(text, null);
    }

    /**
     * Extracts entities, relations, and metadata from the given text using LLM.
     * Optionally includes existing KB metadata for context.
     *
     * @param text       The source text.
     * @param kbMetadata Optional existing metadata from knowledge base.
     * @return Extraction result containing entities, relations, and metadata.
     */
    public ExtractionResult extractFromText(String text, Map<String, Object> kbMetadata) {
        log.debug("Extracting entities and metadata from text of length: {}", text.length());

        try {
            // Include KB metadata context if available
            String contextText = text;
            if (kbMetadata != null && !kbMetadata.isEmpty()) {
                String metadataContext = objectMapper.writeValueAsString(kbMetadata);
                contextText = "EXISTING METADATA:\n" + metadataContext + "\n\nCONTENT:\n" + text;
            }

            String prompt = String.format(EXTRACTION_PROMPT, contextText);
            String response = LLMProvider.callLLM(prompt);

            // Clean response (remove markdown code blocks if present)
            response = cleanJsonResponse(response);

            return parseResponse(response);
        } catch (Exception e) {
            log.error("Extraction failed: {}", e.getMessage(), e);
            return new ExtractionResult(); // Return empty result on failure
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null)
            return "{}";

        // Remove markdown code blocks
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }

        return response.trim();
    }

    private ExtractionResult parseResponse(String json) {
        ExtractionResult result = new ExtractionResult();

        try {
            JsonNode root = objectMapper.readTree(json);

            // Parse entities
            if (root.has("entities") && root.get("entities").isArray()) {
                for (JsonNode entityNode : root.get("entities")) {
                    ExtractedEntity entity = new ExtractedEntity();
                    entity.setName(entityNode.path("name").asText(""));
                    entity.setType(entityNode.path("type").asText("OTHER"));
                    entity.setDescription(entityNode.path("description").asText(""));

                    if (!entity.getName().isEmpty()) {
                        result.getEntities().add(entity);
                    }
                }
            }

            // Parse relations
            if (root.has("relations") && root.get("relations").isArray()) {
                for (JsonNode relNode : root.get("relations")) {
                    ExtractedRelation relation = new ExtractedRelation();
                    relation.setSourceName(relNode.path("source").asText(""));
                    relation.setTargetName(relNode.path("target").asText(""));
                    relation.setRelationType(relNode.path("relation").asText("RELATED_TO"));

                    if (!relation.getSourceName().isEmpty() && !relation.getTargetName().isEmpty()) {
                        result.getRelations().add(relation);
                    }
                }
            }

            // Parse metadata
            if (root.has("metadata") && root.get("metadata").isObject()) {
                JsonNode metaNode = root.get("metadata");
                ExtractedMetadata metadata = result.getMetadata();

                // Topics
                if (metaNode.has("topics") && metaNode.get("topics").isArray()) {
                    for (JsonNode topic : metaNode.get("topics")) {
                        metadata.getTopics().add(topic.asText());
                    }
                }

                // Keywords
                if (metaNode.has("keywords") && metaNode.get("keywords").isArray()) {
                    for (JsonNode keyword : metaNode.get("keywords")) {
                        metadata.getKeywords().add(keyword.asText());
                    }
                }

                // Simple string fields
                metadata.setSentiment(metaNode.path("sentiment").asText("NEUTRAL"));
                metadata.setLanguage(metaNode.path("language").asText("en"));
                metadata.setContentType(metaNode.path("contentType").asText("OTHER"));
                metadata.setSummary(metaNode.path("summary").asText(""));
            }

            log.debug("Parsed {} entities, {} relations, {} topics, {} keywords",
                    result.getEntities().size(),
                    result.getRelations().size(),
                    result.getMetadata().getTopics().size(),
                    result.getMetadata().getKeywords().size());
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Converts extracted metadata to a JsonNode for storage.
     */
    public ObjectNode metadataToJson(ExtractedMetadata metadata) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("topics", objectMapper.valueToTree(metadata.getTopics()));
        node.set("keywords", objectMapper.valueToTree(metadata.getKeywords()));
        node.put("sentiment", metadata.getSentiment());
        node.put("language", metadata.getLanguage());
        node.put("contentType", metadata.getContentType());
        node.put("summary", metadata.getSummary());
        return node;
    }

    // --- Data classes ---

    @Data
    public static class ExtractionResult {
        private List<ExtractedEntity> entities = new ArrayList<>();
        private List<ExtractedRelation> relations = new ArrayList<>();
        private ExtractedMetadata metadata = new ExtractedMetadata();
    }

    @Data
    public static class ExtractedEntity {
        private String name;
        private String type;
        private String description;
    }

    @Data
    public static class ExtractedRelation {
        private String sourceName;
        private String targetName;
        private String relationType;
    }

    @Data
    public static class ExtractedMetadata {
        private List<String> topics = new ArrayList<>();
        private List<String> keywords = new ArrayList<>();
        private String sentiment = "NEUTRAL";
        private String language = "en";
        private String contentType = "OTHER";
        private String summary = "";
    }
}
