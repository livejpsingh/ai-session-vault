package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.dto.ContextOptimizationRequest;
import com.jitendra.ai.sessionvault.dto.OptimizedContextResponse;

public interface ContextOptimizationEngine {

    /**
     * Optimizes context for a given query, session, and project to fit strictly
     * within the specified token budget using model-specific tokenizers.
     *
     * @param request the optimization parameters and constraints
     * @return budget-enforced optimized context response with usage statistics
     */
    OptimizedContextResponse optimizeContext(ContextOptimizationRequest request);

    /**
     * Invalidates all cached contexts for a specific project upon memory mutation (Mitigates Risk R8).
     *
     * @param projectId target project to invalidate
     */
    void invalidateCache(String projectId);
}
