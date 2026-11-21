package com.jitendra.ai.sessionvault.rag;

import com.jitendra.ai.sessionvault.config.VaultFeatureFlags;
import com.jitendra.ai.sessionvault.rag.dto.RagSearchCandidate;
import com.jitendra.ai.sessionvault.rag.repository.HybridSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Thin VectorStore Adapter over existing PostgreSQL pgvector and GIN tables (Mitigates Risk R5).
 * Acts as a non-breaking adapter ensuring direct SQL and Spring AI native access produce identical top-20 rankings.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VaultVectorStoreAdapter {

    private final HybridSearchRepository hybridSearchRepository;
    private final VaultFeatureFlags featureFlags;

    public List<RagSearchCandidate> query(
            String embeddingModel,
            String queryVector,
            String query,
            String project,
            String agent,
            String metadataFilter,
            List<String> tags,
            int candidateLimit,
            double hybridWeight,
            int rrfK,
            double recencyWeight,
            Duration recencyHalfLife,
            double minScore) {

        log.debug("VaultVectorStoreAdapter query executed (featureFlag.vectorStoreAdapter={})",
                featureFlags.isVectorStoreAdapter());

        return hybridSearchRepository.search(
                embeddingModel,
                queryVector,
                query,
                project,
                agent,
                metadataFilter,
                tags != null ? tags : Collections.emptyList(),
                candidateLimit,
                hybridWeight,
                rrfK,
                recencyWeight,
                recencyHalfLife,
                minScore
        );
    }
}
