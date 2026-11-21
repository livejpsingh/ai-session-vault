package com.jitendra.ai.sessionvault.rag.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RelatedSessionResult(
        UUID sessionId,
        String externalSessionId,
        String title,
        String projectName,
        String summary,
        double score,
        OffsetDateTime createdAt) {
}
