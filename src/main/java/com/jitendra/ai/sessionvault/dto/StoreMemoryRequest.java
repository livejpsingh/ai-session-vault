package com.jitendra.ai.sessionvault.dto;

import com.jitendra.ai.sessionvault.domain.MemoryCategory;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreMemoryRequest {
    private String tenantId;
    private String projectId;
    private UUID sessionId;
    private MemoryCategory category;
    private String content;
    private String shortSummary;
    private Double importanceScore;
    private Double confidenceScore;
    private List<String> sourceMessageIds;
    private Map<String, Object> metadata;
}
