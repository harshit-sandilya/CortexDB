package com.vectornode.memory.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "entities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RagEntity extends BaseEntity {

    @Column(name = "entity_name")
    private String entityName;

    @Column(name = "vector", columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] vector;

    // Note: entity type is stored in metadata JSONB, e.g., {"type": "Company"}

    @ManyToMany
    @JoinTable(name = "entity_contexts", joinColumns = @JoinColumn(name = "entity_id"), inverseJoinColumns = @JoinColumn(name = "context_id"))
    @Builder.Default
    private List<Context> contexts = new ArrayList<>();
}
