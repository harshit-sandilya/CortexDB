package com.vectornode.memory.query.repository;

import com.vectornode.memory.entity.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the query_logs table.
 * Supports Query Intent Memory: finding similar past queries,
 * detecting follow-ups, and marking sessions as continued.
 */
@Repository
public interface QueryLogRepository extends JpaRepository<QueryLog, UUID> {

        /**
         * Find past queries whose embeddings have cosine similarity >= threshold
         * to the given query vector.
         * Returns [id, query_text, retrieved_context_ids, session_continued,
         * follow_up_count, similarity_score].
         */
        @Query(value = """
                        SELECT ql.id, ql.query_text, ql.retrieved_context_ids,
                               ql.session_continued, ql.follow_up_count,
                               1 - (ql.query_embedding <=> CAST(:queryVector AS vector)) AS similarity_score
                        FROM query_logs ql
                        WHERE (1 - (ql.query_embedding <=> CAST(:queryVector AS vector))) >= :threshold
                        ORDER BY similarity_score DESC
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findSimilarPastQueries(
                        @Param("queryVector") String queryVector,
                        @Param("threshold") double threshold,
                        @Param("limit") int limit);

        /**
         * Find the most recent query from the same user within a time window.
         * Used for follow-up detection.
         */
        @Query(value = """
                        SELECT * FROM query_logs
                        WHERE uid = :uid
                          AND created_at < :now
                          AND created_at >= :windowStart
                        ORDER BY created_at DESC
                        LIMIT 1
                        """, nativeQuery = true)
        Optional<QueryLog> findRecentByUid(
                        @Param("uid") String uid,
                        @Param("now") Instant now,
                        @Param("windowStart") Instant windowStart);

        /**
         * Mark a previous query as session_continued and increment follow_up_count.
         */
        @Modifying
        @Query(value = """
                        UPDATE query_logs
                        SET session_continued = TRUE,
                            follow_up_count   = follow_up_count + 1,
                            last_follow_up_at = :now
                        WHERE id = :id
                        """, nativeQuery = true)
        void markSessionContinued(
                        @Param("id") UUID id,
                        @Param("now") Instant now);

        /**
         * Find all query logs whose retrieved_context_ids JSON contains the given
         * contextId string.
         * Used for intent stats calculation.
         * Returns [session_continued, follow_up_count] for each matching log.
         */
        @Query(value = """
                        SELECT ql.session_continued, ql.follow_up_count
                        FROM query_logs ql
                        WHERE ql.retrieved_context_ids LIKE CONCAT('%', :contextId, '%')
                        """, nativeQuery = true)
        List<Object[]> findLogsContainingContext(@Param("contextId") String contextId);
}
