package com.vectornode.memory.setup.repository;

import com.vectornode.memory.entity.SetupConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SetupRepository extends JpaRepository<SetupConfiguration, UUID> {
    Optional<SetupConfiguration> findByIsActiveTrue();
}
