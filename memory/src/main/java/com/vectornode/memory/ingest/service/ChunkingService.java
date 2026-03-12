package com.vectornode.memory.ingest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits large text into smaller chunks suitable for embedding and retrieval.
 */
@Service
@Slf4j
public class ChunkingService {

    // Default chunk size (characters)
    public static final int DEFAULT_CHUNK_SIZE = 1000;
    // Overlap between chunks for context continuity
    public static final int DEFAULT_OVERLAP = 200;

    /**
     * Splits text into overlapping chunks.
     *
     * @param text      The source text.
     * @param chunkSize Maximum characters per chunk (default: 1000).
     * @param overlap   Characters of overlap between consecutive chunks (default:
     *                  200).
     * @return List of text chunks.
     */
    public List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        // Use defaults if invalid values passed
        if (chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        if (overlap < 0) {
            overlap = DEFAULT_OVERLAP;
        }

        // Ensure overlap is smaller than chunk size to prevent infinite loops
        overlap = Math.min(overlap, chunkSize - 1);

        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        int previousStart = -1;

        while (start < text.length() && start != previousStart) {
            previousStart = start;
            int end = Math.min(start + chunkSize, text.length());

            // Try to break at sentence boundary if possible
            if (end < text.length()) {
                int sentenceEnd = findSentenceBoundary(text, start, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // If we've reached the end, break
            if (end >= text.length()) {
                break;
            }

            // Move start, accounting for overlap, but ensure forward progress
            int nextStart = end - overlap;
            if (nextStart <= start) {
                // Ensure we always move forward by at least 1 character
                nextStart = start + 1;
            }
            start = nextStart;
        }

        log.debug("Chunked text of length {} into {} chunks", text.length(), chunks.size());
        return chunks;
    }

    /**
     * Finds the best sentence boundary within the given range.
     */
    private int findSentenceBoundary(String text, int start, int end) {
        // Look for sentence endings: . ! ?
        int lastPeriod = text.lastIndexOf('.', end);
        int lastExclaim = text.lastIndexOf('!', end);
        int lastQuestion = text.lastIndexOf('?', end);

        int best = Math.max(lastPeriod, Math.max(lastExclaim, lastQuestion));

        // Must be after start and within reasonable range
        if (best > start + 100) {
            return best + 1; // Include the punctuation
        }

        return end;
    }

    public record CompressedChunk(String restatement, List<String> keywords, String topic, String timestamp) {
    }

    /**
     * SimpleMem: "Semantic Structured Compression"
     * Compresses an episodic prompt into a standalone fact with resolved
     * coreferences
     * and explicit timestamps, plus core keywords.
     */
    public CompressedChunk compressPrompt(String promptRaw) {
        log.info("Compressing prompt into standalone fact");

        String prompt = """
                You are a semantic memory compression engine (SimpleMem).
                Rewrite the following episodic prompt into a single "lossless restatement"—a standalone fact.

                Rules:
                1. Resolve all pronouns (he/she/it) to explicit entity names if possible.
                2. Convert relative terms ("today", "yesterday") into absolute ISO-8601 timestamps using the current time as a reference.
                3. Keep it concise.

                Output your response strictly as valid JSON enclosed in triple backticks ````json ... ````:
                {
                  "restatement": "The standalone fact",
                  "keywords": ["keyword1", "keyword2", "keyword3"],
                  "topic": "The core topic",
                  "timestamp": "ISO-8601 timestamp"
                }

                Input Prompt:
                """
                + promptRaw;

        try {
            String llmResponse = com.vectornode.memory.config.LLMProvider.callLLM(prompt);
            return parseCompressedChunk(llmResponse, promptRaw);
        } catch (Exception e) {
            log.error("Failed to compress prompt via LLM. Falling back to raw text.", e);
            return new CompressedChunk(promptRaw, new ArrayList<>(), "General",
                    java.time.Instant.now().toString());
        }
    }

    private CompressedChunk parseCompressedChunk(String llmResponse, String fallbackText) {
        try {
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

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(llmResponse, CompressedChunk.class);
        } catch (Exception e) {
            log.error("Failed to parse CompressedChunk JSON. Falling back to raw text.", e);
            return new CompressedChunk(fallbackText, new ArrayList<>(), "Unknown Option",
                    java.time.Instant.now().toString());
        }
    }
}
