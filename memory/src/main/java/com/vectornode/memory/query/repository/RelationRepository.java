package com.vectornode.memory.query.repository;

import com.vectornode.memory.entity.Relation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for relations table (Query Pipeline - Read Operations).
 * Relation now extends BaseEntity and has a UUID primary key.
 */
@Repository
public interface RelationRepository extends JpaRepository<Relation, UUID> {

    // NOTE: Relation insertion is handled by IngestionWorker using
    // entityManager.persist()
    // This repository is READ-ONLY for the Query Pipeline

    /**
     * FROM (Outgoing): Finds what an entity connects to.
     */
    @Query(value = """
            SELECT r.relation_type, t.entity_name, r.edge_weight
            FROM relations r
            JOIN entities t ON r.target_entity_id = t.id
            WHERE r.source_entity_id = :sourceId
            ORDER BY r.edge_weight DESC
            """, nativeQuery = true)
    List<Object[]> findOutgoingRelations(@Param("sourceId") UUID sourceId);

    /**
     * TO (Incoming): Finds what connects to an entity.
     */
    @Query(value = """
            SELECT s.entity_name, r.relation_type, r.edge_weight
            FROM relations r
            JOIN entities s ON r.source_entity_id = s.id
            WHERE r.target_entity_id = :targetId
            """, nativeQuery = true)
    List<Object[]> findIncomingRelations(@Param("targetId") UUID targetId);

    /**
     * TRAVERSAL (Graph RAG): Finds "2-hop" connections (Friends of Friends).
     */
    @Query(value = """
            SELECT t.entity_name FROM relations r1
            JOIN relations r2 ON r1.target_entity_id = r2.source_entity_id
            JOIN entities t ON r2.target_entity_id = t.id
            WHERE r1.source_entity_id = :sourceId
            """, nativeQuery = true)
    List<String> findTwoHopConnections(@Param("sourceId") UUID sourceId);

    /**
     * TOP RELATIONS: Finds the strongest connections in the database.
     */
    @Query(value = """
            SELECT s.entity_name AS source_name, r.relation_type, t.entity_name AS target_name, r.edge_weight
            FROM relations r
            JOIN entities s ON r.source_entity_id = s.id
            JOIN entities t ON r.target_entity_id = t.id
            ORDER BY r.edge_weight DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopRelations(@Param("limit") int limit);

    /**
     * Find all relations for a specific source entity.
     */
    List<Relation> findBySourceEntityId(UUID sourceId);

    /**
     * Find all relations for a specific target entity.
     */
    List<Relation> findByTargetEntityId(UUID targetId);

    /**
     * Find relations by type.
     */
    List<Relation> findByRelationType(String relationType);
}