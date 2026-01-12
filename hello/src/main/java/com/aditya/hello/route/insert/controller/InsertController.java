package com.aditya.hello.route.insert.controller;

import com.aditya.hello.dto.IngestionRequest;
import com.aditya.hello.route.insert.service.InsertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insert")
@RequiredArgsConstructor
public class InsertController {

    private final InsertService insertService;

    @PostMapping
    public ResponseEntity<String> insertData(@RequestBody IngestionRequest request) {
        String kbId = insertService.ingestData(request);
        return ResponseEntity.accepted().body("Data ingestion started. KnowledgeBase ID: " + kbId);
    }
}
