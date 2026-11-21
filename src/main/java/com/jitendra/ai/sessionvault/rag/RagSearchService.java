package com.jitendra.ai.sessionvault.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.rag.dto.RagChunkResult;
import com.jitendra.ai.sessionvault.rag.dto.RagSearchFilters;
import com.jitendra.ai.sessionvault.rag.dto.RagSearchRequest;
import com.jitendra.ai.sessionvault.rag.dto.RagSearchResponse;
import com.jitendra.ai.sessionvault.rag.repository.HybridCandidate;
import com.jitendra.ai.sessionvault.rag.repository.HybridSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagSearchService {

    private final EmbeddingModel embeddingModel;
    private final HybridSearchRepository searchRepository;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;
    private final RagIndexingService indexingService;

    public RagSearchResponse search(RagSearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("Search query cannot be blank");
        }

        float[] queryVector = embeddingModel.embed(properties.getQueryPrefix() + request.query().trim());
        EmbeddingVectorValidator.validate(queryVector, properties.getEmbeddingDimension(), "Query");

        RagSearchFilters filters = request.filters();
        int topK = request.resolvedTopK();
        int candidateLimit = Math.min(500, Math.max(topK, topK * properties.getCandidateMultiplier()));
        List<HybridCandidate> candidates = searchRepository.search(
                queryVector,
                request.query().trim(),
                indexingService.activeEmbeddingModelId(),
                filters == null ? null : filters.project(),
                filters == null ? null : filters.agent(),
                json(filters == null || filters.tags() == null ? List.of() : filters.tags()),
                json(filters == null || filters.metadata() == null ? Map.of() : filters.metadata()),
                candidateLimit,
                request.resolvedMinScore(),
                request.resolvedHybridWeight(),
                properties.getRrfK(),
                properties.getRecencyWeight(),
                properties.getRecencyHalfLife());

        List<HybridCandidate> selected = applyMmr(candidates, topK);
        List<RagChunkResult> results = selected.stream()
                .map(candidate -> new RagChunkResult(
                        candidate.chunkId(),
                        candidate.sessionId(),
                        candidate.externalSessionId(),
                        candidate.sessionTitle(),
                        candidate.projectName(),
                        candidate.content(),
                        candidate.roleSequence(),
                        candidate.score(),
                        candidate.semanticSimilarity(),
                        candidate.keywordRank(),
                        candidate.sessionCreatedAt()))
                .toList();
        return new RagSearchResponse(request.query().trim(), results);
    }

    private List<HybridCandidate> applyMmr(List<HybridCandidate> candidates, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        double min = candidates.stream().mapToDouble(HybridCandidate::score).min().orElse(0.0);
        double max = candidates.stream().mapToDouble(HybridCandidate::score).max().orElse(min);
        List<HybridCandidate> remaining = new ArrayList<>(candidates);
        List<HybridCandidate> selected = new ArrayList<>();

        while (!remaining.isEmpty() && selected.size() < topK) {
            HybridCandidate best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (HybridCandidate candidate : remaining) {
                double relevance = max == min ? 1.0 : (candidate.score() - min) / (max - min);
                double redundancy = selected.stream()
                        .filter(chosen -> chosen.sessionId().equals(candidate.sessionId()))
                        .mapToDouble(chosen -> cosineSimilarity(candidate.embedding(), chosen.embedding()))
                        .max()
                        .orElse(0.0);
                if (redundancy >= properties.getDuplicateSimilarityThreshold()) {
                    continue;
                }
                double mmr = properties.getMmrLambda() * relevance
                        - (1.0 - properties.getMmrLambda()) * redundancy;
                if (mmr > bestScore) {
                    best = candidate;
                    bestScore = mmr;
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
            remaining.remove(best);
        }
        return selected;
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize RAG search filters", e);
        }
    }
}
