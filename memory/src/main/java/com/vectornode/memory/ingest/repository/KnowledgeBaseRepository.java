package com.vectornode.memory.ingest.repository;

import com.vectornode.memory.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for knowledge_base table.
 * Queries match queries.pdf specification.
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Integer> {

        // ============ From queries.pdf Section 2: Knowledge Base Operations
        // ============

        /**
         * FETCH BY USER: Retrieves all history for a specific external user.
         * SELECT * FROM knowledge_base WHERE user_id = ? ORDER BY created_at DESC;
         */
        List<KnowledgeBase> findByUserIdOrderByCreatedAtDesc(String userId);

        /**
         * SEMANTIC SEARCH: Finds similar past queries.
         * SELECT content, user_id FROM knowledge_base ORDER BY vector <=>
         * '[Query_Vector]' LIMIT 5;
         */
        @Query(value = """
                        SELECT * FROM knowledge_base
                        ORDER BY vector <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<KnowledgeBase> findSimilar(
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        /**
         * FETCH BY TIME: Gets queries from the last N hours.
         * SELECT * FROM knowledge_base WHERE created_at > NOW() - INTERVAL '24 hours';
         */
        @Query(value = """
                        SELECT * FROM knowledge_base
                        WHERE created_at > NOW() - INTERVAL :hours HOUR
                        ORDER BY created_at DESC
                        """, nativeQuery = true)
        List<KnowledgeBase> findRecent(@Param("hours") int hours);

        /**
         * Alternative: Find entries since a specific timestamp.
         */
        List<KnowledgeBase> findByCreatedAtAfter(Instant since);

        /**
         * DELETE USER DATA: "Right to be Forgotten" - Deletes everything for a user.
         * DELETE FROM knowledge_base WHERE user_id = ?;
         * Note: Due to ON DELETE CASCADE, this will automatically wipe linked Contexts.
         */
        @Modifying
        @Query("DELETE FROM KnowledgeBase kb WHERE kb.userId = :userId")
        void deleteByUserId(@Param("userId") String userId);
}
