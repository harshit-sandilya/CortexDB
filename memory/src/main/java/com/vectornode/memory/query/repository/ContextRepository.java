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
                     WHERE created_at >= NOW() - (INTERVAL '1 day' * :days)
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
                     WHERE c.created_at > NOW() - (INTERVAL '1 day' * :days)
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

       // HIGHLY SIMILAR: Used for Online Semantic Synthesis. Finds chunks with
       // similarity > threshold.
       // Returns [id, text_chunk, similarity_score]
       @Query(value = """
                     SELECT c.id, c.text_chunk, 1 - (c.vector_embedding <=> CAST(:queryVector AS vector)) AS similarity_score
                     FROM contexts c
                     WHERE (1 - (c.vector_embedding <=> CAST(:queryVector AS vector))) >= :threshold
                     ORDER BY similarity_score DESC
                     LIMIT 1
                     """, nativeQuery = true)
       List<Object[]> findHighlySimilar(
                     @Param("queryVector") String queryVector,
                     @Param("threshold") double threshold);

       // SIMILARITY BAND: Finds chunks in a similarity range [minSim, maxSim),
       // excluding a specific context (self). Used by ContradictionDetector.
       @Query(value = """
                     SELECT c.id, c.text_chunk,
                            1 - (c.vector_embedding <=> CAST(:queryVector AS vector)) AS similarity_score
                     FROM contexts c
                     WHERE c.id != :excludeId
                       AND (1 - (c.vector_embedding <=> CAST(:queryVector AS vector)))
                           BETWEEN :minSim AND :maxSim
                     ORDER BY similarity_score DESC
                     LIMIT :limit
                     """, nativeQuery = true)
       List<Object[]> findInSimilarityBand(
                     @Param("queryVector") String queryVector,
                     @Param("minSim") double minSimilarity,
                     @Param("maxSim") double maxSimilarity,
                     @Param("limit") int limit,
                     @Param("excludeId") UUID excludeId);

       // CONTRADICTION LOOKUP: Finds all contradictions involving a specific context.
       // Returns [id, context_id_a, context_id_b, contradiction_summary, severity,
       // resolved, text_a, text_b]
       @Query(value = """
                     SELECT cc.id, cc.context_id_a, cc.context_id_b,
                            cc.contradiction_summary, cc.severity, cc.resolved,
                            ca.text_chunk AS text_a,
                            cb.text_chunk AS text_b
                     FROM context_contradictions cc
                     JOIN contexts ca ON cc.context_id_a = ca.id
                     JOIN contexts cb ON cc.context_id_b = cb.id
                     WHERE (cc.context_id_a = :contextId OR cc.context_id_b = :contextId)
                       AND cc.resolved = false
                     """, nativeQuery = true)
       List<Object[]> findContradictionsForContext(
                     @Param("contextId") UUID contextId);

       // ALL CONTRADICTIONS: Paginated list of all unresolved contradictions.
       // Returns [id, context_id_a, context_id_b, contradiction_summary, severity,
       // created_at, text_a, text_b]
       @Query(value = """
                     SELECT cc.id, cc.context_id_a, cc.context_id_b,
                            cc.contradiction_summary, cc.severity, cc.created_at,
                            ca.text_chunk AS text_a,
                            cb.text_chunk AS text_b
                     FROM context_contradictions cc
                     JOIN contexts ca ON cc.context_id_a = ca.id
                     JOIN contexts cb ON cc.context_id_b = cb.id
                     WHERE cc.resolved = false
                     ORDER BY cc.created_at DESC
                     """, nativeQuery = true)
       List<Object[]> findAllUnresolvedContradictions();

       // RESOLVE: Mark a contradiction as resolved.
       @org.springframework.data.jpa.repository.Modifying
       @Query(value = """
                     UPDATE context_contradictions
                     SET resolved = true
                     WHERE id = :contradictionId
                     """, nativeQuery = true)
       void resolveContradictionById(@Param("contradictionId") UUID contradictionId);
}