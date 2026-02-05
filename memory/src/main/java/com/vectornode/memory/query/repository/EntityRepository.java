package com.vectornode.memory.query.repository;

import com.vectornode.memory.entity.RagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntityRepository extends JpaRepository<RagEntity, UUID> {

        // Find entity by exact name.
        Optional<RagEntity> findByName(String name);

        // Find entity by name (case-insensitive).
        Optional<RagEntity> findByNameIgnoreCase(String name);

        // FETCH ID: Simple lookup to get an ID by name.
        @Query("SELECT e.id FROM RagEntity e WHERE e.name = :name")
        Optional<UUID> findIdByName(@Param("name") String name);

        // DISAMBIGUATE: Finds the correct Entity ID based on vector similarity to
        // context.
        @Query(value = """
                        SELECT * FROM entities
                        WHERE entity_name = :entityName
                        ORDER BY vector_embedding <=> CAST(:contextVector AS vector)
                        LIMIT 1
                        """, nativeQuery = true)
        Optional<RagEntity> disambiguateEntity(
                        @Param("entityName") String entityName,
                        @Param("contextVector") String contextVector);

        // Vector similarity search with scores: Returns [id, entity_name, entity_type,
        // description, similarity_score]
        @Query(value = """
                        SELECT e.id, e.entity_name, e.entity_type, e.description,
                               1 - (e.vector_embedding <=> CAST(:queryVector AS vector)) AS similarity_score
                        FROM entities e
                        ORDER BY e.vector_embedding <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findSimilarEntitiesWithScore(
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        // Vector similarity search for entities (without score).
        @Query(value = """
                        SELECT * FROM entities
                        ORDER BY vector_embedding <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<RagEntity> findSimilarEntities(
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        // GET CONTEXTS: "Show me every time we mentioned [entity]."
        @Query(value = """
                        SELECT c.* FROM contexts c
                        JOIN entity_context_junction ec ON c.id = ec.context_id
                        WHERE ec.entity_id = :entityId
                        """, nativeQuery = true)
        List<Object[]> findContextsForEntity(@Param("entityId") UUID entityId);

        // GET ENTITIES: "What concepts are mentioned in this specific chunk?"
        @Query(value = """
                        SELECT e.* FROM entities e
                        JOIN entity_context_junction ec ON e.id = ec.entity_id
                        WHERE ec.context_id = :contextId
                        """, nativeQuery = true)
        List<RagEntity> findEntitiesForContext(@Param("contextId") UUID contextId);

        // MERGE ENTITIES: Update references when merging entities.
        @Modifying
        @Query(value = """
                        UPDATE entity_context_junction SET entity_id = :targetEntityId
                        WHERE entity_id = :sourceEntityId
                        """, nativeQuery = true)
        void mergeEntities(
                        @Param("sourceEntityId") UUID sourceEntityId,
                        @Param("targetEntityId") UUID targetEntityId);
}