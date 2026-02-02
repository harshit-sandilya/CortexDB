package com.vectornode.memory.entity;

import jakarta.persistence.*;
import lombok.*;
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
@Builder
public class Context extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kb_id", nullable = false)
    private KnowledgeBase knowledgeBase;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String textChunk;

    @Column(columnDefinition = "vector(1536)", nullable = false)
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] vectorEmbedding;

    @Column(name = "chunk_index")
    private int chunkIndex;

    @ManyToMany(mappedBy = "contexts")
    @Builder.Default
    private List<RagEntity> relatedEntities = new ArrayList<>();
}