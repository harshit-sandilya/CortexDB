package com.vectornode.memory.query.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

    @NotBlank(message = "Query text cannot be blank")
    private String query;

    @Builder.Default
    private int limit = 5;

    @Builder.Default
    private double minRelevance = 0.7;

    private Map<String, Object> filters;

    // Optional: Choose strategy if we implement multiple (e.g., VECTOR, GRAPH,
    // HYBRID)
    // private SearchStrategy strategy;
}
