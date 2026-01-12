package com.aditya.hello.route.query.controller;

import com.aditya.hello.dto.QueryRequest;
import com.aditya.hello.entity.Context;
import com.aditya.hello.entity.KnowledgeBase;
import com.aditya.hello.route.query.service.QueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    @PostMapping("/similar")
    public ResponseEntity<List<Context>> similarVectors(@RequestBody QueryRequest request) {
        if (request.getVector() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                queryService.findSimilarContexts(request.getVector(), request.getLimit() > 0 ? request.getLimit() : 5));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<KnowledgeBase>> fetchByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(queryService.fetchByUserId(userId));
    }

    @PostMapping("/time")
    public ResponseEntity<List<KnowledgeBase>> fetchByTime(@RequestBody QueryRequest request) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(queryService.fetchByTime(request.getStartTime(), request.getEndTime()));
    }

    @GetMapping("/relation/{entityId}")
    public ResponseEntity<List<UUID>> getRelatedEntities(@PathVariable UUID entityId,
            @RequestParam(defaultValue = "1") int depth) {
        return ResponseEntity.ok(queryService.findRelatedEntities(entityId, depth));
    }
}
