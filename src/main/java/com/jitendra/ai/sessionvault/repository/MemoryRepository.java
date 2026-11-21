package com.jitendra.ai.sessionvault.repository;

import com.jitendra.ai.sessionvault.domain.Memory;
import com.jitendra.ai.sessionvault.domain.MemoryCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    List<Memory> findByTenantIdAndActiveTrueOrderByImportanceScoreDesc(String tenantId);

    List<Memory> findByTenantIdAndProjectIdAndActiveTrueOrderByImportanceScoreDesc(String tenantId, String projectId);

    List<Memory> findBySessionIdAndActiveTrueOrderByImportanceScoreDesc(UUID sessionId);

    List<Memory> findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(String tenantId, MemoryCategory category);

    List<Memory> findByTenantIdAndCategoryInAndActiveTrueOrderByImportanceScoreDesc(
            String tenantId, Collection<MemoryCategory> categories);

    List<Memory> findByTenantIdAndProjectIdAndCategoryInAndActiveTrueOrderByImportanceScoreDesc(
            String tenantId, String projectId, Collection<MemoryCategory> categories);

    long countByTenantIdAndActiveTrue(String tenantId);

    long countByCategoryAndActiveTrue(MemoryCategory category);

    @Query(
        value = """
            SELECT m.* FROM memories m
            WHERE m.tenant_id = :tenantId
              AND (:projectId IS NULL OR m.project_id = :projectId)
              AND m.is_active = true
              AND m.search_vector @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank_cd(m.search_vector, plainto_tsquery('english', :query)) * m.importance_score DESC
            """,
        countQuery = """
            SELECT count(*) FROM memories m
            WHERE m.tenant_id = :tenantId
              AND (:projectId IS NULL OR m.project_id = :projectId)
              AND m.is_active = true
              AND m.search_vector @@ plainto_tsquery('english', :query)
            """,
        nativeQuery = true
    )
    Page<Memory> searchByFullText(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("query") String query,
            Pageable pageable);

    void deleteByTenantIdAndProjectId(String tenantId, String projectId);
}
