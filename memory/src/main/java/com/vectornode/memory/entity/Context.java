package com.vectornode.memory.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contexts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Context extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kb_id")
    private KnowledgeBase knowledgeBase;

    @Column(name = "context_data", columnDefinition = "TEXT")
    private String contextData;

    @Column(name = "vector", columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] vector;

    @ManyToMany(mappedBy = "contexts")
    @Builder.Default
    private List<RagEntity> relatedEntities = new ArrayList<>();
}
