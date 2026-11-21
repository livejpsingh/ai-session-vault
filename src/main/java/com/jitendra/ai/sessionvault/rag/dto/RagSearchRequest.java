package com.jitendra.ai.sessionvault.rag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RagSearchRequest(
        @NotBlank String query,
        @Min(1) @Max(100) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double minScore,
        @DecimalMin("0.0") @DecimalMax("1.0") Double hybridWeight,
        @Valid RagSearchFilters filters) {

    public int resolvedTopK() {
        return topK == null ? 10 : topK;
    }

    public double resolvedMinScore() {
        return minScore == null ? 0.0 : minScore;
    }

    public double resolvedHybridWeight() {
        return hybridWeight == null ? 0.5 : hybridWeight;
    }
}
