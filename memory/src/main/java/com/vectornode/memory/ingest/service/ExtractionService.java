package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Uses LLM to extract entities and relations from text.
 */
@Service
@Slf4j
public class ExtractionService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXTRACTION_PROMPT = """
            You are a knowledge graph extraction system. Extract entities and relations from the following text.

            TEXT:
            %s

            Respond ONLY with valid JSON in this exact format (no markdown, no explanation):
            {
              "entities": [
                {"name": "Entity Name", "type": "PERSON|ORGANIZATION|LOCATION|CONCEPT|EVENT|OTHER", "description": "Brief description"}
              ],
              "relations": [
                {"source": "Source Entity Name", "target": "Target Entity Name", "relation": "RELATION_TYPE"}
              ]
            }

            Rules:
            - Extract only clearly stated entities and relationships
            - Use simple, normalized entity names
            - Relation types should be uppercase with underscores (e.g., WORKS_FOR, LOCATED_IN, PART_OF)
            - If no entities found, return empty arrays
            """;

    /**
     * Extracts entities and relations from the given text using LLM.
     *
     * @param text The source text.
     * @return Extraction result containing entities and relations.
     */
    public ExtractionResult extractFromText(String text) {
        log.debug("Extracting entities from text of length: {}", text.length());

        try {
            String prompt = String.format(EXTRACTION_PROMPT, text);
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

            log.debug("Parsed {} entities and {} relations", result.getEntities().size(), result.getRelations().size());
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
        }

        return result;
    }

    // --- Data classes ---

    @Data
    public static class ExtractionResult {
        private List<ExtractedEntity> entities = new ArrayList<>();
        private List<ExtractedRelation> relations = new ArrayList<>();
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
}
