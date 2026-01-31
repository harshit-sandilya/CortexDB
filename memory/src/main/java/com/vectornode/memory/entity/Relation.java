package com.vectornode.memory.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Represents a relation/edge in the knowledge graph.
 * Uses composite primary key: (source_id, target_id, relation_type)
 */
@Entity
@Table(name = "relations")
@IdClass(RelationId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Relation {

    @Id
    @Column(name = "source_id")
    private Integer sourceId;

    @Id
    @Column(name = "target_id")
    private Integer targetId;

    @Id
    @Column(name = "relation_type")
    private String relationType;

    @Column(name = "edge_weight")
    @Builder.Default
    private Integer edgeWeight = 1;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode metadata;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private Instant createdAt;

    // Relationships for navigation (not part of PK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", insertable = false, updatable = false)
    private RagEntity sourceEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", insertable = false, updatable = false)
    private RagEntity targetEntity;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
