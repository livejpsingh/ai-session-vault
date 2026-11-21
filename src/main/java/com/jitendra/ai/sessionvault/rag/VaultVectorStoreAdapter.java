package com.jitendra.ai.sessionvault.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.config.VaultFeatureFlags;
import com.jitendra.ai.sessionvault.rag.repository.HybridCandidate;
import com.jitendra.ai.sessionvault.rag.repository.HybridSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private final ObjectMapper objectMapper;

    public List<HybridCandidate> query(
            float[] queryVector,
            String queryText,
            String embeddingModel,
            String project,
            String agent,
            List<String> tags,
            Map<String, Object> metadataFilter,
            int candidateLimit,
            double minScore,
            double hybridWeight,
            int rrfK,
            double recencyWeight,
            Duration recencyHalfLife) {

        log.debug("VaultVectorStoreAdapter query executed (featureFlag.vectorStoreAdapter={})",
                featureFlags.isVectorStoreAdapter());

        String tagsJson = json(tags == null ? Collections.emptyList() : tags);
        String metadataJson = json(metadataFilter == null ? Collections.emptyMap() : metadataFilter);

        return hybridSearchRepository.search(
                queryVector,
                queryText,
                embeddingModel,
                project,
                agent,
                tagsJson,
                metadataJson,
                candidateLimit,
                minScore,
                hybridWeight,
                rrfK,
                recencyWeight,
                recencyHalfLife
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize filter to JSON", e);
        }
    }
}
