package com.aditya.hello.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Entity
@Table(name = "contexts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Context {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uniqueContextId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_base_id")
    private KnowledgeBase knowledgeBase;

    @Column(name = "context_data", columnDefinition = "TEXT")
    private String contextData;

    @Column(columnDefinition = "vector")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] vectorEmbedding;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    private Instant timestamp;

    @ManyToMany(mappedBy = "contexts")
    private List<RagEntity> entities;

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }
}
