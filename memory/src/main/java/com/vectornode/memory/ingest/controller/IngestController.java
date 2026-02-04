package com.vectornode.memory.ingest.controller;

import com.vectornode.memory.ingest.dto.request.IngestContentRequest;
import com.vectornode.memory.ingest.dto.response.IngestResponse;
import com.vectornode.memory.ingest.service.IngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final IngestService ingestService;

    @PostMapping("/document")
    public ResponseEntity<IngestResponse> ingestContent(@Valid @RequestBody IngestContentRequest request) {
        IngestResponse response = ingestService.ingestContent(request);
        return ResponseEntity.ok(response);
    }
}
