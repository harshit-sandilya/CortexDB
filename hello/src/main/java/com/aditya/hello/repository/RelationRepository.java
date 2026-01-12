package com.aditya.hello.repository;

import com.aditya.hello.entity.Relation;
import com.aditya.hello.entity.RagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface RelationRepository extends JpaRepository<Relation, UUID> {

    Optional<Relation> findBySourceEntityAndTargetEntityAndRelationType(RagEntity source, RagEntity target,
            String type);

    // Recursive CTE to find related entities up to a depth
    @Query(nativeQuery = true, value = "WITH RECURSIVE graph_path AS ( " +
            "    SELECT source_entity_id, target_entity_id, 1 as depth " +
            "    FROM relations " +
            "    WHERE source_entity_id = :startId " +
            "    UNION ALL " +
            "    SELECT r.source_entity_id, r.target_entity_id, gp.depth + 1 " +
            "    FROM relations r " +
            "    JOIN graph_path gp ON r.source_entity_id = gp.target_entity_id " +
            "    WHERE gp.depth < :maxDepth " +
            ") " +
            "SELECT cast(target_entity_id as uuid) FROM graph_path")
    List<UUID> findConnectedEntityIds(@Param("startId") UUID startId, @Param("maxDepth") int maxDepth);
}
