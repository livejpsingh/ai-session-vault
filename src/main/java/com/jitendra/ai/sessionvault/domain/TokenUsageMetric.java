package com.jitendra.ai.sessionvault.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "token_usage_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUsageMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession session;

    @Column(name = "tenant_id", nullable = false, length = 100)
    @Builder.Default
    private String tenantId = "default";

    @Column(name = "project_id", length = 100)
    private String projectId;

    @Column(name = "agent_id", length = 100)
    private String agentId;

    @Column(name = "original_tokens", nullable = false)
    private int originalTokens;

    @Column(name = "optimized_tokens", nullable = false)
    private int optimizedTokens;

    @Column(name = "tokens_saved", nullable = false)
    private int tokensSaved;

    @Column(name = "reduction_percentage", nullable = false)
    private double reductionPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "optimization_mode", nullable = false, length = 50)
    private OptimizationMode optimizationMode;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
