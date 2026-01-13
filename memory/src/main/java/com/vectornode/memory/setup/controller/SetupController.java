package com.vectornode.memory.setup.controller;

import com.vectornode.memory.setup.dto.request.SetupRequest;
import com.vectornode.memory.setup.dto.response.SetupResponse;
import com.vectornode.memory.setup.service.SetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @PostMapping
    public ResponseEntity<SetupResponse> configure(@Valid @RequestBody SetupRequest request) {
        return ResponseEntity.ok(setupService.configureLLM(request));
    }
}
