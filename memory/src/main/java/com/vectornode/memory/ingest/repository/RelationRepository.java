package com.vectornode.memory.ingest.repository;

import com.vectornode.memory.entity.Relation;
import com.vectornode.memory.entity.RelationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for relations table.
 * Queries match queries.pdf specification.
 */
@Repository
public interface RelationRepository extends JpaRepository<Relation, RelationId> {

        // ============ From queries.pdf Section 6: Relation Operations ============

        /**
         * UPSERT RELATION: Inserts a relation. If it exists, increments the weight.
         * INSERT INTO relations (source_id, target_id, relation_type, edge_weight)
         * VALUES (?, ?, ?, 1)
         * ON CONFLICT (source_id, target_id, relation_type)
         * DO UPDATE SET edge_weight = relations.edge_weight + 1;
         */
        @Modifying
        @Query(value = """
                        INSERT INTO relations (source_id, target_id, relation_type, edge_weight, created_at)
                        VALUES (:sourceId, :targetId, :relationType, 1, NOW())
                        ON CONFLICT (source_id, target_id, relation_type)
                        DO UPDATE SET edge_weight = relations.edge_weight + 1
                        """, nativeQuery = true)
        void upsertRelation(
                        @Param("sourceId") Integer sourceId,
                        @Param("targetId") Integer targetId,
                        @Param("relationType") String relationType);

        /**
         * FROM (Outgoing): Finds what an entity connects to.
         * SELECT r.relation_type, t.entity_name, r.edge_weight FROM relations r
         * JOIN entities t ON r.target_id = t.id WHERE r.source_id = ?
         * ORDER BY r.edge_weight DESC;
         */
        @Query(value = """
                        SELECT r.relation_type, t.entity_name, r.edge_weight
                        FROM relations r
                        JOIN entities t ON r.target_id = t.id
                        WHERE r.source_id = :sourceId
                        ORDER BY r.edge_weight DESC
                        """, nativeQuery = true)
        List<Object[]> findOutgoingRelations(@Param("sourceId") Integer sourceId);

        /**
         * TO (Incoming): Finds what connects to an entity.
         * SELECT s.entity_name, r.relation_type, r.edge_weight FROM relations r
         * JOIN entities s ON r.source_id = s.id WHERE r.target_id = ?;
         */
        @Query(value = """
                        SELECT s.entity_name, r.relation_type, r.edge_weight
                        FROM relations r
                        JOIN entities s ON r.source_id = s.id
                        WHERE r.target_id = :targetId
                        """, nativeQuery = true)
        List<Object[]> findIncomingRelations(@Param("targetId") Integer targetId);

        /**
         * TRAVERSAL (Graph RAG): Finds "2-hop" connections (Friends of Friends).
         * SELECT t.entity_name FROM relations r1
         * JOIN relations r2 ON r1.target_id = r2.source_id
         * JOIN entities t ON r2.target_id = t.id
         * WHERE r1.source_id = ?;
         */
        @Query(value = """
                        SELECT t.entity_name FROM relations r1
                        JOIN relations r2 ON r1.target_id = r2.source_id
                        JOIN entities t ON r2.target_id = t.id
                        WHERE r1.source_id = :sourceId
                        """, nativeQuery = true)
        List<String> findTwoHopConnections(@Param("sourceId") Integer sourceId);

        /**
         * TOP RELATIONS: Finds the strongest connections in the database.
         * SELECT s.entity_name, r.relation_type, t.entity_name, r.edge_weight
         * FROM relations r
         * JOIN entities s ON r.source_id = s.id
         * JOIN entities t ON r.target_id = t.id
         * ORDER BY r.edge_weight DESC LIMIT 10;
         */
        @Query(value = """
                        SELECT s.entity_name AS source_name, r.relation_type, t.entity_name AS target_name, r.edge_weight
                        FROM relations r
                        JOIN entities s ON r.source_id = s.id
                        JOIN entities t ON r.target_id = t.id
                        ORDER BY r.edge_weight DESC
                        LIMIT :limit
                        """, nativeQuery = true)
        List<Object[]> findTopRelations(@Param("limit") int limit);

        // Additional utility methods

        /**
         * Find all relations for a specific source entity.
         */
        List<Relation> findBySourceId(Integer sourceId);

        /**
         * Find all relations for a specific target entity.
         */
        List<Relation> findByTargetId(Integer targetId);

        /**
         * Find relations by type.
         */
        List<Relation> findByRelationType(String relationType);
}
