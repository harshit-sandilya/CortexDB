package com.vectornode.memory.repository;

import com.vectornode.memory.entity.Context;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContextRepository extends JpaRepository<Context, UUID> {
    List<Context> findByKnowledgeBaseId(UUID kbId);
}
