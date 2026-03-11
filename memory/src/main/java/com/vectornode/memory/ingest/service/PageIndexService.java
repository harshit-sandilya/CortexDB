package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for extracting hierarchical "Table of Contents" trees from large
 * documents
 * according to the PageIndex logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageIndexService {

    private final ObjectMapper objectMapper;

    @Data
    public static class DocumentNode {
        private String title;
        private String summary;
        private String content;
        private List<DocumentNode> children = new ArrayList<>();
    }

    public DocumentNode generateDocumentTree(String documentText) {
        log.info("Generating document tree for text of length: {}", documentText.length());

        String prompt = """
                Analyze the following document and output a nested JSON structure representing its Table of Contents.
                The JSON MUST strictly follow this exact structure, enclosed in triple backticks ````json ... ````:
                {
                  "title": "Root Document Title",
                  "summary": "A brief summary of the entire document.",
                  "content": "The introductory text of the document before the first section.",
                  "children": [
                     {
                       "title": "Section 1 Title",
                       "summary": "Summary of Section 1",
                       "content": "Full exact content of section 1",
                       "children": [ ... any subsections ... ]
                     }
                  ]
                }

                If there are no sections, just put all the text in the root "content" field and leave "children" empty [].
                Ensure all text from the document is fully preserved within the "content" fields of the respective nodes.
                Ensure valid JSON formatting.

                Document:
                """
                + documentText;

        String llmResponse = LLMProvider.callLLM(prompt);
        return parseLlmResponse(llmResponse);
    }

    private DocumentNode parseLlmResponse(String llmResponse) {
        try {
            // Find the JSON block in the response
            int startIndex = llmResponse.indexOf("```json");
            if (startIndex != -1) {
                startIndex += 7;
                int endIndex = llmResponse.lastIndexOf("```");
                if (endIndex > startIndex) {
                    llmResponse = llmResponse.substring(startIndex, endIndex).trim();
                } else {
                    llmResponse = llmResponse.substring(startIndex).trim();
                }
            } else {
                // Try without specific language tag
                startIndex = llmResponse.indexOf("```");
                if (startIndex != -1) {
                    startIndex += 3;
                    int endIndex = llmResponse.lastIndexOf("```");
                    if (endIndex > startIndex) {
                        llmResponse = llmResponse.substring(startIndex, endIndex).trim();
                    } else {
                        llmResponse = llmResponse.substring(startIndex).trim();
                    }
                }
            }

            return objectMapper.readValue(llmResponse, DocumentNode.class);

        } catch (Exception e) {
            log.error("Failed to parse LLM JSON response for document tree. Raw response: {}", llmResponse, e);
            throw new RuntimeException("Failed to parse document tree from LLM", e);
        }
    }
}
