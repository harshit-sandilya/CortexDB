package com.vectornode.memory.setup.dto.request;

import com.vectornode.memory.entity.enums.LLMApiProvider;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetupRequest {
    @NotNull
    private LLMApiProvider provider;

    private String apiKey;

    @NotNull
    private String chatModelName;

    @NotNull
    private String embedModelName;

    private String baseUrl;
}
