package com.aditya.hello.repository;

import com.aditya.hello.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {
    List<KnowledgeBase> findByUserId(String userId);

    // fetch_bytime equivalent
    List<KnowledgeBase> findByTimestampBetween(Instant startTime, Instant endTime);
}
