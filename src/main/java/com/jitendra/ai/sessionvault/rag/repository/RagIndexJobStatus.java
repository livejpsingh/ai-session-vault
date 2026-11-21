package com.jitendra.ai.sessionvault.rag.repository;

import java.time.OffsetDateTime;

public record RagIndexJobStatus(
        String status,
        int attempts,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime updatedAt) {
}
