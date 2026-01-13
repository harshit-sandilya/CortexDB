package com.vectornode.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "setup_configurations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetupConfiguration extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "base_url")
    private String baseUrl; // For Ollama or custom endpoints

    @Column(name = "is_active")
    private boolean isActive;

    public enum Provider {
        OPENAI, AZURE, OLLAMA
    }
}
