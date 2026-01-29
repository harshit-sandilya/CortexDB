package com.vectornode.memory.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "entities", indexes = {
        @Index(name = "idx_entity_name", columnList = "entity_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RagEntity extends BaseEntity {
    @Column(name = "entity_name", nullable = false)
    private String name;

    @Column(name = "entity_type")
    private String type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] vectorEmbedding;

    @ManyToMany
    @JoinTable(name = "entity_context_junction", joinColumns = @JoinColumn(name = "entity_id"), inverseJoinColumns = @JoinColumn(name = "context_id"))
    @Builder.Default
    private List<Context> contexts = new ArrayList<>();
}