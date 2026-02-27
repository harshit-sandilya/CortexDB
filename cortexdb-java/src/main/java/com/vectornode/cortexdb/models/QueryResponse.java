package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Response from query endpoints that return search results.
 * Mirrors the backend {@code QueryResponse} DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryResponse {

    private String query;
    private List<SearchResult> results;
    private long processingTimeMs;

    public QueryResponse() {
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> results) {
        this.results = results;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
}
