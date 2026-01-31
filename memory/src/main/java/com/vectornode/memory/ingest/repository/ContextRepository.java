package com.vectornode.memory.ingest.repository;

import com.vectornode.memory.entity.Context;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for contexts table.
 * Queries match queries.pdf specification.
 */
@Repository
public interface ContextRepository extends JpaRepository<Context, Integer> {

        // ============ From queries.pdf Section 3: Context Operations ============

        /**
         * Find all contexts for a specific knowledge base.
         */
        List<Context> findByKnowledgeBaseId(Integer kbId);

        /**
         * FETCH BY TIME RANGE: Retrieves chunks created within a specific time window.
         * SELECT context_data, created_at FROM contexts
         * WHERE created_at >= NOW() - INTERVAL '7 days' ORDER BY created_at DESC;
         */
        @Query(value = """
                        SELECT * FROM contexts
                        WHERE created_at >= NOW() - INTERVAL :days DAY
                        ORDER BY created_at DESC
                        """, nativeQuery = true)
        List<Context> findRecentContexts(@Param("days") int days);

        /**
         * TIME RANGE FETCH: Retrieves chunks from a specific date range.
         * SELECT context_data FROM contexts WHERE created_at BETWEEN ? AND ?;
         */
        @Query(value = """
                        SELECT * FROM contexts
                        WHERE created_at BETWEEN :startDate AND :endDate
                        ORDER BY created_at DESC
                        """, nativeQuery = true)
        List<Context> findByDateRange(
                        @Param("startDate") String startDate,
                        @Param("endDate") String endDate);

        /**
         * FETCH PARENT: Finds the original KB that generated this chunk.
         * SELECT kb.content FROM knowledge_base kb JOIN contexts c ON kb.id = c.kb_id
         * WHERE c.id = ?;
         * Note: Use this via navigation: context.getKnowledgeBase()
         */

        /**
         * VECTOR SEARCH (Core RAG): Finds the most relevant chunks for a new question.
         * SELECT context_data, metadata FROM contexts ORDER BY vector <=>
         * '[New_Question_Vector]' LIMIT 5;
         */
        @Query(value = """
                        SELECT * FROM contexts
                        ORDER BY vector <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Context> findSimilar(
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        /**
         * RECENT VECTORS (Hybrid): Finds semantically similar chunks, but only if
         * recent.
         * SELECT context_data FROM contexts WHERE created_at > NOW() - INTERVAL '30
         * days'
         * ORDER BY vector <=> '[Query_Vector]' LIMIT 5;
         */
        @Query(value = """
                        SELECT * FROM contexts
                        WHERE created_at > NOW() - INTERVAL :days DAY
                        ORDER BY vector <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Context> findRecentSimilar(
                        @Param("days") int days,
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        /**
         * FETCH SIBLINGS: Gets other chunks from the same document/query.
         * SELECT * FROM contexts WHERE kb_id = (SELECT kb_id FROM contexts WHERE id =
         * ?) ORDER BY id ASC;
         */
        @Query(value = """
                        SELECT * FROM contexts
                        WHERE kb_id = (SELECT kb_id FROM contexts WHERE id = :contextId)
                        ORDER BY id ASC
                        """, nativeQuery = true)
        List<Context> findSiblingContexts(@Param("contextId") Integer contextId);
}
