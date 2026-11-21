package com.jitendra.ai.sessionvault.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUsageSummary {

    private int budget;

    private int originalTokens;

    private int used;

    private int remaining;

    private int tokensSaved;

    private double reductionPercentage;
}
