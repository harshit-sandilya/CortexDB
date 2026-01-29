package com.vectornode.memory.repository;

import com.vectornode.memory.entity.RagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntityRepository extends JpaRepository<RagEntity, UUID> {
    Optional<RagEntity> findByName(String name);
}
