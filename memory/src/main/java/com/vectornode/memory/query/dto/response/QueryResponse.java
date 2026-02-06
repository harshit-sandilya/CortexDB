package com.vectornode.memory.query.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class QueryResponse {

    private String query;
    private List<SearchResult> results;
    private long processingTimeMs;

    @Data
    @Builder
    public static class SearchResult {
        private UUID id;
        private String content; // Text chunk or entity name
        private double score; // Similarity score
        private String type; // "CHUNK", "ENTITY", "RELATION"
        private Map<String, Object> metadata;
    }
}