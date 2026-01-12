package com.aditya.hello.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Data
public class IngestionRequest {
    private String userId;
    private String text;
    // Optional: if the client computes embeddings, otherwise server does it.
    // simpler to assume server handles it or mocking it for now.
    private float[] vector;
    private String metadata; // JSON string
}
