package com.aditya.hello.repository;

import com.aditya.hello.entity.Context;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

@Repository
public interface ContextRepository extends JpaRepository<Context, UUID> {

    @Query(nativeQuery = true, value = "SELECT * FROM contexts ORDER BY vector_embedding <-> cast(:vector as vector) LIMIT :limit")
    List<Context> findSimilarContexts(@Param("vector") String vectorString, @Param("limit") int limit);
}
