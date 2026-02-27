package com.vectornode.cortexdb.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Request for query endpoints that accept a search body.
 * Mirrors the backend {@code QueryRequest} DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryRequest {

    private String query;
    private int limit = 5;
    private double minRelevance = 0.7;
    private Map<String, Object> filters;

    public QueryRequest() {
    }

    public QueryRequest(String query, int limit, double minRelevance,
            Map<String, Object> filters) {
        this.query = query;
        this.limit = limit;
        this.minRelevance = minRelevance;
        this.filters = filters;
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public double getMinRelevance() {
        return minRelevance;
    }

    public void setMinRelevance(double minRelevance) {
        this.minRelevance = minRelevance;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }
}
