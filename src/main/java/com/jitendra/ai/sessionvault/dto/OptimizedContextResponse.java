package com.jitendra.ai.sessionvault.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizedContextResponse {

    private String query;

    @Builder.Default
    private List<OptimizedContextItem> optimizedContext = new ArrayList<>();

    private String renderedContext;

    private TokenUsageSummary tokenUsage;

    @Builder.Default
    private List<String> sources = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    private boolean fromCache;

    private long latencyMs;
}
