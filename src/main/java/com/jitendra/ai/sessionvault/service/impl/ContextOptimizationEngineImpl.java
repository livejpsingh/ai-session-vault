package com.jitendra.ai.sessionvault.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.domain.*;
import com.jitendra.ai.sessionvault.dto.*;
import com.jitendra.ai.sessionvault.rag.chunking.JTokkitTokenCounter;
import com.jitendra.ai.sessionvault.rag.chunking.TokenCounter;
import com.jitendra.ai.sessionvault.repository.*;
import com.jitendra.ai.sessionvault.service.ContextOptimizationEngine;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContextOptimizationEngineImpl implements ContextOptimizationEngine {

    private final MemoryRepository memoryRepository;
    private final TokenUsageMetricRepository metricRepository;
    private final ContextCacheRepository cacheRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final TokenCounter tokenCounter;

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern FLUFF_PATTERN = Pattern.compile("(?i)^(Sure|Here is|Hope this helps|Let me know|Certainly|Glad to help|As an AI)[^\\n]*\\n?");

    @Autowired
    public ContextOptimizationEngineImpl(
            MemoryRepository memoryRepository,
            TokenUsageMetricRepository metricRepository,
            ContextCacheRepository cacheRepository,
            ChatMessageRepository messageRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) TokenCounter tokenCounter) {
        this.memoryRepository = memoryRepository;
        this.metricRepository = metricRepository;
        this.cacheRepository = cacheRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.tokenCounter = (tokenCounter != null) ? tokenCounter : new JTokkitTokenCounter(EncodingType.CL100K_BASE);
    }

    @Override
    @Transactional
    public OptimizedContextResponse optimizeContext(ContextOptimizationRequest request) {
        long startTime = System.currentTimeMillis();
        String tenantId = (request.getTenantId() != null && !request.getTenantId().isBlank()) ? request.getTenantId() : "default";
        String projectId = request.getProjectId();
        int budget = request.getTokenBudget() > 0 ? request.getTokenBudget() : 1000;
        OptimizationMode mode = request.getOptimizationMode() != null ? request.getOptimizationMode() : OptimizationMode.BALANCED;

        // 1. Check Context Cache
        String cacheKey = computeCacheKey(tenantId, projectId, request.getQuery(), budget, mode);
        try {
            Optional<ContextCacheEntry> cached = cacheRepository.findByCacheKeyAndExpiresAtAfter(cacheKey, OffsetDateTime.now());
            if (cached.isPresent()) {
                OptimizedContextResponse cachedResponse = objectMapper.convertValue(
                        cached.get().getCachedContext(), OptimizedContextResponse.class);
                cachedResponse.setFromCache(true);
                cachedResponse.setLatencyMs(System.currentTimeMillis() - startTime);
                log.info("Context cache hit for key={}", cacheKey);
                return cachedResponse;
            }
        } catch (Exception e) {
            log.warn("Cache lookup failed: {}", e.getMessage());
        }

        // 2. Gather Candidate Memories
        List<MemoryCandidate> candidates = gatherCandidates(request, tenantId, projectId);

        // 3. Calculate baseline original tokens
        int originalTokens = 0;
        for (MemoryCandidate c : candidates) {
            originalTokens += countTokens(c.rawContent);
        }

        // 4. Compress & Deduplicate Candidate Items
        List<OptimizedCandidate> processedCandidates = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();

        for (MemoryCandidate c : candidates) {
            String compressed = compressContent(c.rawContent, mode);
            String sig = generateContentSignature(compressed);
            if (seenSignatures.contains(sig)) {
                continue; // Deduplicate
            }
            seenSignatures.add(sig);

            int tokenCost = countTokens(compressed);
            processedCandidates.add(new OptimizedCandidate(c.memory, c.category, compressed, tokenCost, c.score, c.source));
        }

        // 5. Knapsack Budget Packing (Greedy by rank score)
        // Sort descending by score
        processedCandidates.sort((a, b) -> Double.compare(b.score, a.score));

        List<OptimizedContextItem> selectedItems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        int tokensUsed = 0;

        for (OptimizedCandidate candidate : processedCandidates) {
            if (tokensUsed + candidate.tokenCost <= budget) {
                tokensUsed += candidate.tokenCost;
                selectedItems.add(OptimizedContextItem.builder()
                        .memoryId(candidate.memory != null ? candidate.memory.getId() : null)
                        .type(candidate.category.name().toLowerCase())
                        .category(candidate.category)
                        .content(candidate.content)
                        .importance(candidate.memory != null && candidate.memory.getImportanceScore() != null ? candidate.memory.getImportanceScore() : 0.5)
                        .tokenCount(candidate.tokenCost)
                        .source(candidate.source)
                        .build());
                if (candidate.source != null && !sources.contains(candidate.source)) {
                    sources.add(candidate.source);
                }
            } else {
                // If high importance item cannot fit, try using short summary
                if (candidate.memory != null && candidate.memory.getShortSummary() != null) {
                    String summary = candidate.memory.getShortSummary();
                    int summaryTokens = countTokens(summary);
                    if (tokensUsed + summaryTokens <= budget) {
                        tokensUsed += summaryTokens;
                        selectedItems.add(OptimizedContextItem.builder()
                                .memoryId(candidate.memory.getId())
                                .type(candidate.category.name().toLowerCase() + "_summary")
                                .category(candidate.category)
                                .content(summary)
                                .importance(candidate.memory.getImportanceScore() != null ? candidate.memory.getImportanceScore() : 0.5)
                                .tokenCount(summaryTokens)
                                .source(candidate.source)
                                .build());
                        continue;
                    }
                }

                if (candidate.score >= 0.75) {
                    warnings.add("Budget (" + budget + " tokens) too small to include high-importance " +
                            candidate.category + " item: " + truncate(candidate.content, 60));
                }
            }
        }

        // Render Markdown Prompt Context
        String renderedMarkdown = renderContextMarkdown(selectedItems);
        int finalTokens = countTokens(renderedMarkdown);

        // Safety invariant: Ensure rendered string doesn't breach budget
        if (finalTokens > budget && !selectedItems.isEmpty()) {
            // Trim from lowest priority until within budget
            while (finalTokens > budget && selectedItems.size() > 1) {
                selectedItems.remove(selectedItems.size() - 1);
                renderedMarkdown = renderContextMarkdown(selectedItems);
                finalTokens = countTokens(renderedMarkdown);
            }
        }

        int baselineSafe = Math.max(originalTokens, finalTokens);
        int tokensSaved = Math.max(0, baselineSafe - finalTokens);
        double reductionPct = baselineSafe > 0 ? ((double) tokensSaved / baselineSafe) * 100.0 : 0.0;

        TokenUsageSummary usageSummary = TokenUsageSummary.builder()
                .budget(budget)
                .originalTokens(baselineSafe)
                .used(finalTokens)
                .remaining(Math.max(0, budget - finalTokens))
                .tokensSaved(tokensSaved)
                .reductionPercentage(Math.round(reductionPct * 10.0) / 10.0)
                .build();

        long latency = System.currentTimeMillis() - startTime;

        OptimizedContextResponse response = OptimizedContextResponse.builder()
                .query(request.getQuery())
                .optimizedContext(selectedItems)
                .renderedContext(renderedMarkdown)
                .tokenUsage(usageSummary)
                .sources(sources)
                .warnings(warnings)
                .fromCache(false)
                .latencyMs(latency)
                .build();

        // 6. Record Metrics
        try {
            TokenUsageMetric metric = TokenUsageMetric.builder()
                    .tenantId(tenantId)
                    .projectId(projectId)
                    .agentId(request.getAgentId())
                    .originalTokens(baselineSafe)
                    .optimizedTokens(finalTokens)
                    .tokensSaved(tokensSaved)
                    .reductionPercentage(usageSummary.getReductionPercentage())
                    .optimizationMode(mode)
                    .latencyMs(latency)
                    .build();
            metricRepository.save(metric);
        } catch (Exception e) {
            log.warn("Failed to persist token usage metric: {}", e.getMessage());
        }

        // 7. Save to Cache
        try {
            ContextCacheEntry cacheEntry = ContextCacheEntry.builder()
                    .cacheKey(cacheKey)
                    .tenantId(tenantId)
                    .projectId(projectId)
                    .cachedContext(objectMapper.convertValue(response, Map.class))
                    .tokenCount(finalTokens)
                    .expiresAt(OffsetDateTime.now().plusMinutes(30))
                    .build();
            cacheRepository.save(cacheEntry);
        } catch (Exception e) {
            log.warn("Failed to save context to cache: {}", e.getMessage());
        }

        log.info("Context optimized: budget={}, original={}, final={}, saved={} ({}%), latency={}ms",
                budget, baselineSafe, finalTokens, tokensSaved, usageSummary.getReductionPercentage(), latency);

        return response;
    }

    private List<MemoryCandidate> gatherCandidates(ContextOptimizationRequest request, String tenantId, String projectId) {
        List<MemoryCandidate> list = new ArrayList<>();
        Set<MemoryCategory> excluded = request.getExcludedCategories() != null ? request.getExcludedCategories() : Collections.emptySet();

        // High-importance constraints & decisions (always candidates)
        if (request.isIncludeDecisions() && !excluded.contains(MemoryCategory.DECISION)) {
            List<Memory> decisions = memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(
                    tenantId, MemoryCategory.DECISION);
            for (Memory m : decisions) {
                list.add(new MemoryCandidate(m, MemoryCategory.DECISION, m.getContent(), m.getImportanceScore() * 1.2, "Decision Vault"));
            }
        }

        // Project Architecture & Tech Stack facts
        if (!excluded.contains(MemoryCategory.PROJECT)) {
            List<Memory> projectFacts = memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(
                    tenantId, MemoryCategory.PROJECT);
            for (Memory m : projectFacts) {
                list.add(new MemoryCandidate(m, MemoryCategory.PROJECT, m.getContent(), m.getImportanceScore() * 1.1, "Project Schema"));
            }
        }

        // Developer Preferences
        if (request.isIncludePreferences() && !excluded.contains(MemoryCategory.PREFERENCE)) {
            List<Memory> prefs = memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(
                    tenantId, MemoryCategory.PREFERENCE);
            for (Memory m : prefs) {
                list.add(new MemoryCandidate(m, MemoryCategory.PREFERENCE, m.getContent(), m.getImportanceScore(), "User Preference"));
            }
        }

        // Query-specific hybrid search
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            try {
                var searchResults = memoryRepository.searchByFullText(
                        tenantId, projectId, request.getQuery(), PageRequest.of(0, 15));
                for (Memory m : searchResults) {
                    if (!excluded.contains(m.getCategory())) {
                        list.add(new MemoryCandidate(m, m.getCategory(), m.getContent(), m.getImportanceScore() * 1.3, "Hybrid Search Match"));
                    }
                }
            } catch (Exception e) {
                log.warn("FTS search encountered issue, falling back to recent memories: {}", e.getMessage());
            }
        }

        // Recent session working memory
        if (request.getSessionId() != null && request.isIncludeHistory()) {
            List<Memory> sessionMemories = memoryRepository.findBySessionIdAndActiveTrueOrderByImportanceScoreDesc(request.getSessionId());
            for (Memory m : sessionMemories) {
                if (!excluded.contains(m.getCategory())) {
                    list.add(new MemoryCandidate(m, m.getCategory(), m.getContent(), m.getImportanceScore() * 0.9, "Session Memory"));
                }
            }
            // If few structured memories, pull last 5 raw chat messages as fallback
            if (sessionMemories.isEmpty()) {
                var messages = messageRepository.findBySessionIdOrderBySequenceNumberAsc(request.getSessionId());
                int start = Math.max(0, messages.size() - 5);
                for (int i = start; i < messages.size(); i++) {
                    ChatMessage msg = messages.get(i);
                    String excerpt = msg.getRole() + ": " + msg.getContent();
                    list.add(new MemoryCandidate(null, MemoryCategory.WORKING, excerpt, 0.6, "Recent Chat History"));
                }
            }
        }

        return list;
    }

    private String compressContent(String text, OptimizationMode mode) {
        if (text == null || text.isBlank()) {
            return "";
        }

        if (mode == OptimizationMode.QUALITY_FIRST) {
            return text.trim();
        }

        // Remove conversational pleasantries
        String cleaned = FLUFF_PATTERN.matcher(text).replaceAll("").trim();

        if (mode == OptimizationMode.AGGRESSIVE) {
            // Check if code block exists - preserve code blocks verbatim
            if (CODE_BLOCK_PATTERN.matcher(cleaned).find()) {
                return cleaned;
            }
            // Strip multiple blank lines and simplify
            cleaned = cleaned.replaceAll("\\n{2,}", "\n").trim();
            // Convert verbose sentences to compact bullets if long
            if (cleaned.length() > 200 && !cleaned.contains("\n- ")) {
                String[] sentences = cleaned.split("\\.\\s+");
                if (sentences.length > 2) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : sentences) {
                        String trimmed = s.trim();
                        if (!trimmed.isEmpty()) {
                            sb.append("- ").append(trimmed.replaceAll("\\.$", "")).append("\n");
                        }
                    }
                    return sb.toString().trim();
                }
            }
        }

        return cleaned;
    }

    private String renderContextMarkdown(List<OptimizedContextItem> items) {
        if (items.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### Relevant Project & Memory Context\n\n");

        Map<MemoryCategory, List<OptimizedContextItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(OptimizedContextItem::getCategory, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<MemoryCategory, List<OptimizedContextItem>> entry : grouped.entrySet()) {
            sb.append("#### ").append(formatCategoryTitle(entry.getKey())).append("\n");
            for (OptimizedContextItem item : entry.getValue()) {
                if (item.getContent().startsWith("- ") || item.getContent().startsWith("```")) {
                    sb.append(item.getContent()).append("\n\n");
                } else {
                    sb.append("- ").append(item.getContent()).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private String formatCategoryTitle(MemoryCategory category) {
        return switch (category) {
            case DECISION -> "Architectural & Technical Decisions";
            case PROJECT -> "Project Architecture & Tech Stack";
            case PREFERENCE -> "Developer Preferences & Conventions";
            case PROCEDURAL -> "Procedures & Runbooks";
            case ACTION -> "Action Items & Open Tasks";
            case WORKING -> "Working Memory & Recent Context";
            case EPISODIC -> "Session History & Key Milestones";
            case SEMANTIC -> "Knowledge & Domain Facts";
        };
    }

    private int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, tokenCounter.count(text));
    }

    private String generateContentSignature(String text) {
        String normalized = text.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }

    private String computeCacheKey(String tenantId, String projectId, String query, int budget, OptimizationMode mode) {
        String raw = tenantId + ":" + (projectId != null ? projectId : "") + ":" + (query != null ? query : "") + ":" + budget + ":" + mode.name();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(raw.hashCode());
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private record MemoryCandidate(Memory memory, MemoryCategory category, String rawContent, double score, String source) {}
    private record OptimizedCandidate(Memory memory, MemoryCategory category, String content, int tokenCost, double score, String source) {}
}
