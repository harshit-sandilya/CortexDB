package com.aditya.hello.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Entity
@Table(name = "entities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uniqueEntityId;

    @Column(name = "entity_name", nullable = false)
    private String entityName;

    @Column(columnDefinition = "vector")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] vectorEmbedding;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    private Instant timestamp;

    @ManyToMany
    @JoinTable(name = "entity_context_junction", joinColumns = @JoinColumn(name = "entity_id"), inverseJoinColumns = @JoinColumn(name = "context_id"))
    private List<Context> contexts;

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }
}
