package com.vectornode.memory.repository;

import com.vectornode.memory.entity.Relation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RelationRepository extends JpaRepository<Relation, UUID> {
    List<Relation> findBySourceEntityId(UUID sourceEntityId);

    List<Relation> findByTargetEntityId(UUID targetEntityId);
}
