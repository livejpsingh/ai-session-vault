package com.jitendra.ai.sessionvault.rag.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RagChunkResult(
        UUID chunkId,
        UUID sessionId,
        String externalSessionId,
        String sessionTitle,
        String projectName,
        String content,
        String roleSequence,
        double score,
        double semanticSimilarity,
        double keywordRank,
        OffsetDateTime sessionCreatedAt) {
}
