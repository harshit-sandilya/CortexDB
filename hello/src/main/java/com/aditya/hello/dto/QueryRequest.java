package com.aditya.hello.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class QueryRequest {
    private String userId;
    private float[] vector;
    private Instant startTime;
    private Instant endTime;
    private int limit;
}
