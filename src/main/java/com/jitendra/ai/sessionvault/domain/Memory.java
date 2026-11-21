package com.jitendra.ai.sessionvault.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "memories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Memory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    @Builder.Default
    private String tenantId = "default";

    @Column(name = "project_id", length = 100)
    private String projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MemoryCategory category;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "short_summary", length = 500)
    private String shortSummary;

    @Column(name = "importance_score", nullable = false)
    @Builder.Default
    private Double importanceScore = 0.5;

    @Column(name = "confidence_score", nullable = false)
    @Builder.Default
    private Double confidenceScore = 1.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_message_ids", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> sourceMessageIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "valid_from")
    @Builder.Default
    private OffsetDateTime validFrom = OffsetDateTime.now();

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
