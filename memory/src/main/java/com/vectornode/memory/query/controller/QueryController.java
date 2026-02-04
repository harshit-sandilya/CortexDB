package com.vectornode.memory.query.controller;

import com.vectornode.memory.query.dto.QueryRequest;
import com.vectornode.memory.query.dto.QueryResponse;
import com.vectornode.memory.query.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    // ==================== CONTEXT SEARCH ====================

    @PostMapping("/contexts")
    public ResponseEntity<QueryResponse> searchContexts(@Valid @RequestBody QueryRequest request) {
        QueryResponse response = queryService.searchContexts(request);
        return ResponseEntity.ok(response);
    }

    // ==================== ENTITY SEARCH ====================

    @PostMapping("/entities")
    public ResponseEntity<QueryResponse> searchEntities(@Valid @RequestBody QueryRequest request) {
        QueryResponse response = queryService.searchEntities(request);
        return ResponseEntity.ok(response);
    }

    // ==================== HISTORY SEARCH ====================

    @PostMapping("/history")
    public ResponseEntity<QueryResponse> searchHistory(@Valid @RequestBody QueryRequest request) {
        QueryResponse response = queryService.searchHistory(request);
        return ResponseEntity.ok(response);
    }

    // ==================== GRAPH TRAVERSAL ====================

    @GetMapping("/graph/outgoing/{entityId}")
    public ResponseEntity<QueryResponse> getOutgoingConnections(@PathVariable UUID entityId) {
        QueryResponse response = queryService.getOutgoingConnections(entityId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/graph/incoming/{entityId}")
    public ResponseEntity<QueryResponse> getIncomingConnections(@PathVariable UUID entityId) {
        QueryResponse response = queryService.getIncomingConnections(entityId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/graph/2hop/{entityId}")
    public ResponseEntity<QueryResponse> getTwoHopConnections(@PathVariable UUID entityId) {
        QueryResponse response = queryService.getTwoHopConnections(entityId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/graph/top")
    public ResponseEntity<QueryResponse> getTopRelations(@RequestParam(defaultValue = "10") int limit) {
        QueryResponse response = queryService.getTopRelations(limit);
        return ResponseEntity.ok(response);
    }

    // ==================== HYBRID SEARCH ====================

    @PostMapping("/hybrid")
    public ResponseEntity<QueryResponse> hybridSearch(@Valid @RequestBody QueryRequest request) {
        QueryResponse response = queryService.hybridSearch(request);
        return ResponseEntity.ok(response);
    }
}
