package com.aditya.hello.repository;

import com.aditya.hello.entity.RagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface RagEntityRepository extends JpaRepository<RagEntity, UUID> {
    Optional<RagEntity> findByEntityName(String entityName);
}
