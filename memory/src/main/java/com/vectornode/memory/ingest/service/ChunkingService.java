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
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    // Overlap between chunks for context continuity
    private static final int DEFAULT_OVERLAP = 200;

    /**
     * Splits text into overlapping chunks.
     *
     * @param text The source text to chunk.
     * @return List of text chunks.
     */
    public List<String> chunkText(String text) {
        return chunkText(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * Splits text into overlapping chunks with custom parameters.
     *
     * @param text      The source text.
     * @param chunkSize Maximum characters per chunk.
     * @param overlap   Characters of overlap between consecutive chunks.
     * @return List of text chunks.
     */
    public List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        while (start < text.length()) {
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

            // Move start, accounting for overlap
            start = end - overlap;
            if (start <= 0 || start >= text.length()) {
                break;
            }
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
}
