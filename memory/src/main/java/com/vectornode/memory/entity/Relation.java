package com.vectornode.memory.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Relation entity representing edges in the knowledge graph.
 * Now includes an ID field for easier management.
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
public class Relation extends BaseEntity {

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
}
