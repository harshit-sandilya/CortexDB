package com.vectornode.memory.ingest.dto.request;

import com.vectornode.memory.entity.enums.ConverserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class IngestContentRequest {
    @NotBlank(message = "UID cannot be blank")
    private String uid;

    @NotNull(message = "Converser role cannot be null")
    private ConverserRole converser;

    @NotBlank(message = "Content cannot be blank")
    private String content;

    private Map<String, Object> metadata;
}
