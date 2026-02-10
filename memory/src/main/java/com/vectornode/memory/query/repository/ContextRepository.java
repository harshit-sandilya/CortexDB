package com.vectornode.memory.query.repository;

import com.vectornode.memory.entity.Context;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContextRepository extends JpaRepository<Context, UUID> {

        // Find all contexts for a specific knowledge base.
        List<Context> findByKnowledgeBaseId(UUID kbId);

        // FETCH BY TIME RANGE: Retrieves chunks created within a specific time window.
        @Query(value = """
                        SELECT * FROM contexts
                        WHERE created_at >= NOW() - INTERVAL ':days days'
                        ORDER BY created_at DESC
                        """, nativeQuery = true)
        List<Context> findRecentContexts(@Param("days") int days);

        // TIME RANGE FETCH: Retrieves chunks from a specific date range.
        @Query(value = """
                        SELECT * FROM contexts
                        WHERE created_at BETWEEN CAST(:startDate AS TIMESTAMPTZ) AND CAST(:endDate AS TIMESTAMPTZ)
                        ORDER BY created_at DESC
                        """, nativeQuery = true)
        List<Context> findByDateRange(
                        @Param("startDate") String startDate,
                        @Param("endDate") String endDate);

        // VECTOR SEARCH with score: Returns [id, text_chunk, chunk_index,
        // similarity_score]
        @Query(value = """
                        SELECT c.id, c.text_chunk, c.chunk_index,
                               1 - (c.vector_embedding <=> CAST(:queryVector AS vector)) AS similarity_score
                        FROM contexts c
                        ORDER BY c.vector_embedding <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findSimilarWithScore(
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        // VECTOR SEARCH (without score): For backward compatibility.
        @Query(value = """
                        SELECT * FROM contexts
                        ORDER BY vector_embedding <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Context> findSimilar(
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        // RECENT VECTORS (Hybrid): Finds semantically similar chunks, but only recent
        // ones.
        // Returns [id, text_chunk, chunk_index, similarity_score]
        @Query(value = """
                        SELECT c.id, c.text_chunk, c.chunk_index,
                               1 - (c.vector_embedding <=> CAST(:queryVector AS vector)) AS similarity_score
                        FROM contexts c
                        WHERE c.created_at > NOW() - INTERVAL ':days days'
                        ORDER BY c.vector_embedding <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findRecentSimilarWithScore(
                        @Param("days") int days,
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        // FETCH SIBLINGS: Gets other chunks from the same document/query.
        @Query(value = """
                        SELECT * FROM contexts
                        WHERE kb_id = (SELECT kb_id FROM contexts WHERE id = :contextId)
                        ORDER BY chunk_index ASC
                        """, nativeQuery = true)
        List<Context> findSiblingContexts(@Param("contextId") UUID contextId);
}