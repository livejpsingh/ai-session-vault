package com.jitendra.ai.sessionvault.rag.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReindexStatusResponse(
        UUID sessionId,
        String status,
        int attempts,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime updatedAt) {
}
