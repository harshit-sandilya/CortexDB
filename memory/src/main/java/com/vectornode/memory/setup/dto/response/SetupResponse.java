package com.vectornode.memory.setup.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SetupResponse {
    private String message;
    private boolean success;
    private String configuredProvider;
    private String configuredChatModel;
    private String configuredEmbedModel;
    private String baseUrl;
    private Instant timestamp;
}
