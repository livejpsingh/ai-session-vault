package com.jitendra.ai.sessionvault.repository;

import com.jitendra.ai.sessionvault.domain.TokenUsageMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TokenUsageMetricRepository extends JpaRepository<TokenUsageMetric, UUID> {

    List<TokenUsageMetric> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    List<TokenUsageMetric> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    @Query("SELECT coalesce(sum(m.originalTokens), 0) FROM TokenUsageMetric m WHERE m.tenantId = :tenantId")
    long sumOriginalTokens(@Param("tenantId") String tenantId);

    @Query("SELECT coalesce(sum(m.optimizedTokens), 0) FROM TokenUsageMetric m WHERE m.tenantId = :tenantId")
    long sumOptimizedTokens(@Param("tenantId") String tenantId);

    @Query("SELECT coalesce(sum(m.tokensSaved), 0) FROM TokenUsageMetric m WHERE m.tenantId = :tenantId")
    long sumTokensSaved(@Param("tenantId") String tenantId);

    @Query("SELECT coalesce(avg(m.reductionPercentage), 0.0) FROM TokenUsageMetric m WHERE m.tenantId = :tenantId")
    double avgReductionPercentage(@Param("tenantId") String tenantId);
}
