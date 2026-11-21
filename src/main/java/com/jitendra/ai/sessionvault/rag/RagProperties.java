package com.jitendra.ai.sessionvault.rag;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "vault.rag")
public class RagProperties {

    @NotBlank
    private String provider;

    @NotBlank
    private String embeddingModel;

    private String queryPrefix = "";
    private String documentPrefix = "";

    @Min(1)
    @Max(2000)
    private int embeddingDimension = 768;

    @Min(1)
    private int maxTokens = 800;

    @Min(0)
    private int overlapTokens = 100;

    @Min(1)
    private int embeddingBatchSize = 32;

    @Min(1)
    private int maxAttempts = 5;

    @NotNull
    private Duration retryInitialDelay = Duration.ofSeconds(2);

    @NotNull
    private Duration pollInterval = Duration.ofSeconds(5);

    @NotNull
    private Duration jobLease = Duration.ofMinutes(10);

    @Min(1)
    private int maxConcurrentJobs = 4;

    @Min(1)
    private int rrfK = 60;

    @NotNull
    private Duration recencyHalfLife = Duration.ofDays(90);

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double recencyWeight = 0.1;

    @NotBlank
    private String tokenizer;

    private String tokenizerVocabPath;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double mmrLambda = 0.7;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double duplicateSimilarityThreshold = 0.95;

    @Min(1)
    private int candidateMultiplier = 5;

    @AssertTrue(message = "RAG retry, polling, lease, and recency durations must be positive")
    public boolean isDurationConfigurationValid() {
        return isPositive(retryInitialDelay)
                && isPositive(pollInterval)
                && isPositive(jobLease)
                && isPositive(recencyHalfLife);
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }
}
