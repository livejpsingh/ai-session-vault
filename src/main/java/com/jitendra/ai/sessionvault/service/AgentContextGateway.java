package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.domain.Memory;
import com.jitendra.ai.sessionvault.dto.ContextOptimizationRequest;
import com.jitendra.ai.sessionvault.dto.OptimizedContextResponse;

import java.util.List;
import java.util.Map;

public interface AgentContextGateway {

    /**
     * Centralized gateway providing budget-optimized memory context for AI agents
     * with tenant isolation and project-level permission boundary checks.
     */
    OptimizedContextResponse getAgentContext(ContextOptimizationRequest request);

    /**
     * Retrieves user preferences, project architectural facts, and conventions for the agent.
     */
    List<Memory> getAgentProfile(String tenantId, String projectId, String agentId);

    /**
     * Retrieves chronological architectural decisions.
     */
    List<Memory> getProjectDecisions(String tenantId, String projectId);

    /**
     * Returns memory and token reduction statistics.
     */
    Map<String, Object> getMemoryStats(String tenantId, String projectId);
}
