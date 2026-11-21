package com.jitendra.ai.sessionvault.rag.repository;

import java.util.UUID;

public record RagIndexJob(
        UUID id,
        UUID sessionId,
        String embeddingModel,
        String status,
        int attempts,
        long generation) {
}
