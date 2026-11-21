package com.jitendra.ai.sessionvault.repository;

import com.jitendra.ai.sessionvault.domain.ContextCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContextCacheRepository extends JpaRepository<ContextCacheEntry, UUID> {

    Optional<ContextCacheEntry> findByCacheKeyAndExpiresAtAfter(String cacheKey, OffsetDateTime now);

    void deleteByExpiresAtBefore(OffsetDateTime now);
}
