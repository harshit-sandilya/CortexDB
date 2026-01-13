package com.vectornode.memory.setup.dto.request;

import com.vectornode.memory.entity.SetupConfiguration.Provider;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetupRequest {
    @NotNull
    private Provider provider;

    private String apiKey;

    @NotNull
    private String modelName;

    private String baseUrl;
}
