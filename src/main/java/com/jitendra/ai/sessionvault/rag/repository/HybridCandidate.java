package com.jitendra.ai.sessionvault.rag.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HybridCandidate(
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
        OffsetDateTime sessionCreatedAt,
        float[] embedding) {
}
