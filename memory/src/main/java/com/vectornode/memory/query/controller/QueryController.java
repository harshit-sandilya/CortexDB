package com.vectornode.memory.query.controller;

import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.entity.Relation;
import com.vectornode.memory.query.dto.QueryRequest;
import com.vectornode.memory.query.dto.QueryResponse;
import com.vectornode.memory.query.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    // ==================== CONTEXT ENDPOINTS ====================

    // Semantic search on contexts
    @PostMapping("/contexts")
    public ResponseEntity<QueryResponse> searchContexts(@Valid @RequestBody QueryRequest request) {
        return ResponseEntity.ok(queryService.searchContexts(request));
    }

    // Get all contexts for a knowledge base
    @GetMapping("/contexts/kb/{kbId}")
    public ResponseEntity<QueryResponse> getContextsByKnowledgeBase(@PathVariable UUID kbId) {
        return ResponseEntity.ok(queryService.getContextsByKnowledgeBase(kbId));
    }

    // Get recent contexts (last N days)
    @GetMapping("/contexts/recent")
    public ResponseEntity<QueryResponse> getRecentContexts(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(queryService.getRecentContexts(days));
    }

    // Get contexts by date range
    @GetMapping("/contexts/range")
    public ResponseEntity<QueryResponse> getContextsByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return ResponseEntity.ok(queryService.getContextsByDateRange(startDate, endDate));
    }

    // Search recent contexts with vector similarity
    @PostMapping("/contexts/recent")
    public ResponseEntity<QueryResponse> searchRecentContexts(
            @Valid @RequestBody QueryRequest request,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(queryService.searchRecentContexts(request, days));
    }

    // Get sibling contexts (other chunks from same document)
    @GetMapping("/contexts/siblings/{contextId}")
    public ResponseEntity<QueryResponse> getSiblingContexts(@PathVariable UUID contextId) {
        return ResponseEntity.ok(queryService.getSiblingContexts(contextId));
    }

    // ==================== ENTITY ENDPOINTS ====================

    // Semantic search on entities
    @PostMapping("/entities")
    public ResponseEntity<QueryResponse> searchEntities(@Valid @RequestBody QueryRequest request) {
        return ResponseEntity.ok(queryService.searchEntities(request));
    }

    // Find entity by exact name
    @GetMapping("/entities/name/{name}")
    public ResponseEntity<RagEntity> getEntityByName(@PathVariable String name) {
        return queryService.getEntityByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Find entity by name (case-insensitive)
    @GetMapping("/entities/name-ignore-case/{name}")
    public ResponseEntity<RagEntity> getEntityByNameIgnoreCase(@PathVariable String name) {
        return queryService.getEntityByNameIgnoreCase(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get entity ID by name
    @GetMapping("/entities/id/{name}")
    public ResponseEntity<Map<String, UUID>> getEntityIdByName(@PathVariable String name) {
        return queryService.getEntityIdByName(name)
                .map(id -> ResponseEntity.ok(Map.of("id", id)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Disambiguate entity using vector similarity
    @PostMapping("/entities/disambiguate")
    public ResponseEntity<RagEntity> disambiguateEntity(
            @RequestParam String entityName,
            @RequestBody String contextText) {
        return queryService.disambiguateEntity(entityName, contextText)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get all contexts where an entity is mentioned
    @GetMapping("/entities/{entityId}/contexts")
    public ResponseEntity<QueryResponse> getContextsForEntity(@PathVariable UUID entityId) {
        return ResponseEntity.ok(queryService.getContextsForEntity(entityId));
    }

    // Get all entities mentioned in a context
    @GetMapping("/contexts/{contextId}/entities")
    public ResponseEntity<QueryResponse> getEntitiesForContext(@PathVariable UUID contextId) {
        return ResponseEntity.ok(queryService.getEntitiesForContext(contextId));
    }

    // Merge two entities
    @PostMapping("/entities/merge")
    public ResponseEntity<Void> mergeEntities(
            @RequestParam UUID sourceEntityId,
            @RequestParam UUID targetEntityId) {
        queryService.mergeEntities(sourceEntityId, targetEntityId);
        return ResponseEntity.ok().build();
    }

    // ==================== KNOWLEDGE BASE / HISTORY ENDPOINTS ====================

    // Semantic search on knowledge bases
    @PostMapping("/history")
    public ResponseEntity<QueryResponse> searchHistory(@Valid @RequestBody QueryRequest request) {
        return ResponseEntity.ok(queryService.searchHistory(request));
    }

    // Get all history for a user
    @GetMapping("/history/user/{uid}")
    public ResponseEntity<QueryResponse> getHistoryByUser(@PathVariable String uid) {
        return ResponseEntity.ok(queryService.getHistoryByUser(uid));
    }

    // Get recent knowledge bases (last N hours)
    @GetMapping("/history/recent")
    public ResponseEntity<QueryResponse> getRecentKnowledgeBases(@RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(queryService.getRecentKnowledgeBases(hours));
    }

    // Get knowledge bases since a timestamp
    @GetMapping("/history/since")
    public ResponseEntity<QueryResponse> getKnowledgeBasesSince(@RequestParam String since) {
        Instant timestamp = Instant.parse(since);
        return ResponseEntity.ok(queryService.getKnowledgeBasesSince(timestamp));
    }

    // Delete all data for a user (GDPR)
    @DeleteMapping("/history/user/{uid}")
    public ResponseEntity<Void> deleteUserData(@PathVariable String uid) {
        queryService.deleteUserData(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== GRAPH / RELATION ENDPOINTS ====================

    // Get outgoing relations for an entity
    @GetMapping("/graph/outgoing/{entityId}")
    public ResponseEntity<QueryResponse> getOutgoingConnections(@PathVariable UUID entityId) {
        return ResponseEntity.ok(queryService.getOutgoingConnections(entityId));
    }

    // Get incoming relations for an entity
    @GetMapping("/graph/incoming/{entityId}")
    public ResponseEntity<QueryResponse> getIncomingConnections(@PathVariable UUID entityId) {
        return ResponseEntity.ok(queryService.getIncomingConnections(entityId));
    }

    // Get 2-hop connections
    @GetMapping("/graph/2hop/{entityId}")
    public ResponseEntity<QueryResponse> getTwoHopConnections(@PathVariable UUID entityId) {
        return ResponseEntity.ok(queryService.getTwoHopConnections(entityId));
    }

    // Get top/strongest relations
    @GetMapping("/graph/top")
    public ResponseEntity<QueryResponse> getTopRelations(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(queryService.getTopRelations(limit));
    }

    // Get relations by source entity
    @GetMapping("/graph/source/{sourceId}")
    public ResponseEntity<List<Relation>> getRelationsBySource(@PathVariable UUID sourceId) {
        return ResponseEntity.ok(queryService.getRelationsBySource(sourceId));
    }

    // Get relations by target entity
    @GetMapping("/graph/target/{targetId}")
    public ResponseEntity<List<Relation>> getRelationsByTarget(@PathVariable UUID targetId) {
        return ResponseEntity.ok(queryService.getRelationsByTarget(targetId));
    }

    // Get relations by type
    @GetMapping("/graph/type/{relationType}")
    public ResponseEntity<List<Relation>> getRelationsByType(@PathVariable String relationType) {
        return ResponseEntity.ok(queryService.getRelationsByType(relationType));
    }

    // ==================== HYBRID SEARCH ====================

    @PostMapping("/hybrid")
    public ResponseEntity<QueryResponse> hybridSearch(@Valid @RequestBody QueryRequest request) {
        return ResponseEntity.ok(queryService.hybridSearch(request));
    }
}
