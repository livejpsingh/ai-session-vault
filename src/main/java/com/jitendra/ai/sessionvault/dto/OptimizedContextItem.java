package com.jitendra.ai.sessionvault.dto;

import com.jitendra.ai.sessionvault.domain.MemoryCategory;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizedContextItem {

    private UUID memoryId;

    private String type;

    private MemoryCategory category;

    private String content;

    private double importance;

    private int tokenCount;

    private String source;
}
