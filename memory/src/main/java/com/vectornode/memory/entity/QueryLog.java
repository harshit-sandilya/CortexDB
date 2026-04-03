package com.vectornode.memory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the query_logs table.
 * Captures every query for intent-based re-ranking (Query Intent Memory).
 */
@Entity
@Table(name = "query_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "uid")
    private String uid;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "query_embedding", columnDefinition = "vector(768)", nullable = false)
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] queryEmbedding;

    /** JSON array of UUID strings — e.g. ["uuid1","uuid2"] */
    @Column(name = "retrieved_context_ids", columnDefinition = "TEXT")
    private String retrievedContextIds;

    @Column(name = "session_continued", nullable = false)
    @Builder.Default
    private boolean sessionContinued = false;

    @Column(name = "follow_up_count", nullable = false)
    @Builder.Default
    private int followUpCount = 0;

    @Column(name = "last_follow_up_at")
    private Instant lastFollowUpAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
