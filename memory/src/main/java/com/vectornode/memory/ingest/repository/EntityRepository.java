package com.vectornode.memory.ingest.repository;

import com.vectornode.memory.entity.RagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for entities table.
 * Queries match queries.pdf specification.
 */
@Repository
public interface EntityRepository extends JpaRepository<RagEntity, Integer> {

        // ============ From queries.pdf Section 4: Entity Operations ============

        /**
         * Find entity by exact name.
         */
        Optional<RagEntity> findByEntityName(String entityName);

        /**
         * Find entity by name (case-insensitive).
         */
        Optional<RagEntity> findByEntityNameIgnoreCase(String entityName);

        /**
         * FETCH ID: Simple lookup to get an ID by name.
         * SELECT id FROM entities WHERE entity_name = ?;
         */
        @Query("SELECT e.id FROM RagEntity e WHERE e.entityName = :entityName")
        Optional<Integer> findIdByEntityName(@Param("entityName") String entityName);

        /**
         * DISAMBIGUATE: Finds the correct Entity ID based on vector similarity to
         * context.
         * SELECT id, entity_name FROM entities WHERE entity_name = ?
         * ORDER BY vector <=> '[Current_Context_Vector]' LIMIT 1;
         */
        @Query(value = """
                        SELECT * FROM entities
                        WHERE entity_name = :entityName
                        ORDER BY vector <=> CAST(:contextVector AS vector)
                        LIMIT 1
                        """, nativeQuery = true)
        Optional<RagEntity> disambiguateEntity(
                        @Param("entityName") String entityName,
                        @Param("contextVector") String contextVector);

        /**
         * Vector similarity search for entities.
         */
        @Query(value = """
                        SELECT * FROM entities
                        ORDER BY vector <=> CAST(:queryVector AS vector)
                        LIMIT :limit
                        """, nativeQuery = true)
        List<RagEntity> findSimilarEntities(
                        @Param("queryVector") String queryVector,
                        @Param("limit") int limit);

        // ============ From queries.pdf Section 5: Junction Operations ============

        /**
         * GET CONTEXTS: "Show me every time we mentioned [entity]."
         * SELECT c.context_data FROM contexts c
         * JOIN entity_contexts ec ON c.id = ec.context_id WHERE ec.entity_id = ?;
         */
        @Query(value = """
                        SELECT c.* FROM contexts c
                        JOIN entity_contexts ec ON c.id = ec.context_id
                        WHERE ec.entity_id = :entityId
                        """, nativeQuery = true)
        List<Object[]> findContextsForEntity(@Param("entityId") Integer entityId);

        /**
         * GET ENTITIES: "What concepts are mentioned in this specific chunk?"
         * SELECT e.entity_name FROM entities e
         * JOIN entity_contexts ec ON e.id = ec.entity_id WHERE ec.context_id = ?;
         */
        @Query(value = """
                        SELECT e.* FROM entities e
                        JOIN entity_contexts ec ON e.id = ec.entity_id
                        WHERE ec.context_id = :contextId
                        """, nativeQuery = true)
        List<RagEntity> findEntitiesForContext(@Param("contextId") Integer contextId);

        /**
         * MERGE ENTITIES: Update references when merging entities.
         * UPDATE entity_contexts SET entity_id = ? WHERE entity_id = ?;
         */
        @Modifying
        @Query(value = """
                        UPDATE entity_contexts SET entity_id = :targetEntityId
                        WHERE entity_id = :sourceEntityId
                        """, nativeQuery = true)
        void mergeEntities(
                        @Param("sourceEntityId") Integer sourceEntityId,
                        @Param("targetEntityId") Integer targetEntityId);
}
