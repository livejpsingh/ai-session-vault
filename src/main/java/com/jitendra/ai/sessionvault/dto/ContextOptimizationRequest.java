package com.jitendra.ai.sessionvault.dto;

import com.jitendra.ai.sessionvault.domain.MemoryCategory;
import com.jitendra.ai.sessionvault.domain.OptimizationMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextOptimizationRequest {

    private String query;

    private UUID sessionId;

    private String projectId;

    @Builder.Default
    private String tenantId = "default";

    private String agentId;

    @Min(value = 50, message = "Token budget must be at least 50")
    @Max(value = 128000, message = "Token budget must not exceed 128000")
    @Builder.Default
    private int tokenBudget = 1000;

    @Builder.Default
    private OptimizationMode optimizationMode = OptimizationMode.BALANCED;

    @Builder.Default
    private boolean includePreferences = true;

    @Builder.Default
    private boolean includeDecisions = true;

    @Builder.Default
    private boolean includeHistory = true;

    private Set<MemoryCategory> categories;

    private Set<MemoryCategory> excludedCategories;

    private String targetModel;
}
