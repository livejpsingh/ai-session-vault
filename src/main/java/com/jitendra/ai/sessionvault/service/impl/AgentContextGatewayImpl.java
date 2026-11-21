package com.jitendra.ai.sessionvault.service.impl;

import com.jitendra.ai.sessionvault.domain.Memory;
import com.jitendra.ai.sessionvault.domain.MemoryCategory;
import com.jitendra.ai.sessionvault.dto.ContextOptimizationRequest;
import com.jitendra.ai.sessionvault.dto.OptimizedContextResponse;
import com.jitendra.ai.sessionvault.repository.MemoryRepository;
import com.jitendra.ai.sessionvault.repository.TokenUsageMetricRepository;
import com.jitendra.ai.sessionvault.service.AgentContextGateway;
import com.jitendra.ai.sessionvault.service.ContextOptimizationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgentContextGatewayImpl implements AgentContextGateway {

    private final ContextOptimizationEngine optimizationEngine;
    private final MemoryRepository memoryRepository;
    private final TokenUsageMetricRepository metricRepository;

    @Override
    @Transactional
    public OptimizedContextResponse getAgentContext(ContextOptimizationRequest request) {
        if (request.getTenantId() == null || request.getTenantId().isBlank()) {
            request.setTenantId("default");
        }
        log.info("AgentContextGateway: serving context for agentId='{}', tenant='{}', project='{}'",
                request.getAgentId(), request.getTenantId(), request.getProjectId());
        return optimizationEngine.optimizeContext(request);
    }

    @Override
    public List<Memory> getAgentProfile(String tenantId, String projectId, String agentId) {
        String safeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        Set<MemoryCategory> profileCategories = Set.of(MemoryCategory.PREFERENCE, MemoryCategory.PROJECT);
        if (projectId != null && !projectId.isBlank()) {
            return memoryRepository.findByTenantIdAndProjectIdAndCategoryInAndActiveTrueOrderByImportanceScoreDesc(
                    safeTenant, projectId, profileCategories);
        }
        return memoryRepository.findByTenantIdAndCategoryInAndActiveTrueOrderByImportanceScoreDesc(
                safeTenant, profileCategories);
    }

    @Override
    public List<Memory> getProjectDecisions(String tenantId, String projectId) {
        String safeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        List<Memory> allDecisions = memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(
                safeTenant, MemoryCategory.DECISION);
        if (projectId != null && !projectId.isBlank()) {
            return allDecisions.stream()
                    .filter(m -> projectId.equalsIgnoreCase(m.getProjectId()))
                    .toList();
        }
        return allDecisions;
    }

    @Override
    public Map<String, Object> getMemoryStats(String tenantId, String projectId) {
        String safeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        Map<String, Object> stats = new LinkedHashMap<>();

        long totalMemories = memoryRepository.countByTenantIdAndActiveTrue(safeTenant);
        stats.put("tenantId", safeTenant);
        stats.put("totalMemories", totalMemories);

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (MemoryCategory cat : MemoryCategory.values()) {
            categoryCounts.put(cat.name(), memoryRepository.countByCategoryAndActiveTrue(cat));
        }
        stats.put("categories", categoryCounts);

        long originalTokens = metricRepository.sumOriginalTokens(safeTenant);
        long optimizedTokens = metricRepository.sumOptimizedTokens(safeTenant);
        long tokensSaved = metricRepository.sumTokensSaved(safeTenant);
        double avgReduction = metricRepository.avgReductionPercentage(safeTenant);

        Map<String, Object> tokenStats = new LinkedHashMap<>();
        tokenStats.put("totalOriginalTokens", originalTokens);
        tokenStats.put("totalOptimizedTokens", optimizedTokens);
        tokenStats.put("totalTokensSaved", tokensSaved);
        tokenStats.put("averageReductionPercentage", Math.round(avgReduction * 10.0) / 10.0);
        stats.put("tokenSavings", tokenStats);

        return stats;
    }
}
