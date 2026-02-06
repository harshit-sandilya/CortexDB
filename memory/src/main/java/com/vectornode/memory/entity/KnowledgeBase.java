package com.vectornode.memory.entity;

import com.vectornode.memory.entity.enums.ConverserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "knowledge_bases", indexes = {
        @Index(name = "idx_kb_uid", columnList = "uid"),
        @Index(name = "idx_kb_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeBase extends BaseEntity {
    @Column(nullable = false)
    private String uid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConverserRole converser;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "vector(3072)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] vectorEmbedding;

    @OneToMany(mappedBy = "knowledgeBase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Context> contexts = new ArrayList<>();
}
