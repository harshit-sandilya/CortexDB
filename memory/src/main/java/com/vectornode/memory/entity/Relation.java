package com.vectornode.memory.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Relation entity representing edges in the knowledge graph.
 * Uses composite primary key (source_entity_id, target_entity_id,
 * relation_type).
 * No separate ID field as per schema specification.
 */
@Entity
@Table(name = "relations", indexes = {
        @Index(name = "idx_source_target", columnList = "source_entity_id, target_entity_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Relation {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_entity_id", nullable = false)
    private RagEntity sourceEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_entity_id", nullable = false)
    private RagEntity targetEntity;

    @Column(name = "relation_type", nullable = false)
    private String relationType;

    @Column(name = "edge_weight")
    private int edgeWeight;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode metadata;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
