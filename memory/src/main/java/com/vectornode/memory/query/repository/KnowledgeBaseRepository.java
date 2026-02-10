package com.vectornode.memory.query.repository;

import com.vectornode.memory.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {

        // FETCH BY UID: Retrieves all history for a specific external user.
        List<KnowledgeBase> findByUidOrderByCreatedAtDesc(String uid);

        // SEMANTIC SEARCH with scores: Returns [id, content, uid, converser,
        // similarity_score]
        @Query(value = """
                        SELECT kb.id, kb.content, kb.uid, kb.converser,
                               1 - (kb.vector_embedding <=> CAST(:queryVector AS vector)) AS similarity_score
                        FROM knowledge_bases kb
                        ORDER BY kb.vector_embedding <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findSimilarWithScore(
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        // SEMANTIC SEARCH (without score): For backward compatibility
        @Query(value = """
                        SELECT * FROM knowledge_bases
                        ORDER BY vector_embedding <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<KnowledgeBase> findSimilar(
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        // FETCH BY TIME: Gets entries from the last N hours.
        @Query(value = """
                        SELECT * FROM knowledge_bases
                        WHERE created_at > NOW() - INTERVAL ':hours hours'
                        ORDER BY created_at DESC
                        """, nativeQuery = true)
        List<KnowledgeBase> findRecent(@Param("hours") int hours);

        // Find entries since a specific timestamp.
        List<KnowledgeBase> findByCreatedAtAfter(Instant since);

        // DELETE USER DATA: "Right to be Forgotten" - Deletes everything for a user.
        // Note: Due to ON DELETE CASCADE, this will automatically wipe linked Contexts.
        @Modifying
        @Query("DELETE FROM KnowledgeBase kb WHERE kb.uid = :uid")
        void deleteByUid(@Param("uid") String uid);
}