package com.jitendra.ai.sessionvault.repository;

import com.jitendra.ai.sessionvault.domain.MemoryVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MemoryVersionRepository extends JpaRepository<MemoryVersion, UUID> {

    List<MemoryVersion> findByMemoryIdOrderByVersionNumberDesc(UUID memoryId);

    int countByMemoryId(UUID memoryId);
}
