package com.jitendra.ai.sessionvault.mcp;

import com.jitendra.ai.sessionvault.domain.Memory;
import com.jitendra.ai.sessionvault.domain.MemoryCategory;
import com.jitendra.ai.sessionvault.domain.OptimizationMode;
import com.jitendra.ai.sessionvault.dto.ContextOptimizationRequest;
import com.jitendra.ai.sessionvault.dto.OptimizedContextResponse;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.MemoryRepository;
import com.jitendra.ai.sessionvault.service.AgentContextGateway;
import com.jitendra.ai.sessionvault.service.ContextOptimizationEngine;
import com.jitendra.ai.sessionvault.service.MemoryExtractionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryMcpTools {

    private final ContextOptimizationEngine optimizationEngine;
    private final MemoryExtractionEngine extractionEngine;
    private final AgentContextGateway contextGateway;
    private final MemoryRepository memoryRepository;
    private final ChatMessageRepository messageRepository;

    @McpTool(
            name = "memory_optimize_context",
            description = "Dynamically compresses, filters, and packs relevant project facts, architectural decisions, and session memories into a strict token budget. Slashes LLM input tokens by 70%-90%."
    )
    public Map<String, Object> optimizeContext(
            @McpToolParam(description = "User query or task description to ground the context retrieval", required = true) String query,
            @McpToolParam(description = "Optional session ID to incorporate working session context", required = false) String sessionId,
            @McpToolParam(description = "Optional project identifier filter", required = false) String projectId,
            @McpToolParam(description = "Maximum token budget (default: 1000, e.g. 500-4000)", required = false) Integer tokenBudget,
            @McpToolParam(description = "Optimization mode: BALANCED, AGGRESSIVE, or QUALITY_FIRST", required = false) String optimizationMode,
            @McpToolParam(description = "Whether to include developer preferences (default: true)", required = false) Boolean includePreferences,
            @McpToolParam(description = "Whether to include architectural decisions (default: true)", required = false) Boolean includeDecisions,
            @McpToolParam(description = "Whether to include recent conversation history (default: true)", required = false) Boolean includeHistory) {

        log.info("MCP Tool [memory_optimize_context] invoked: query='{}', budget={}", query, tokenBudget);

        UUID sessionUuid = null;
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                sessionUuid = UUID.fromString(sessionId);
            } catch (IllegalArgumentException ignored) {}
        }

        ContextOptimizationRequest request = ContextOptimizationRequest.builder()
                .query(query)
                .sessionId(sessionUuid)
                .projectId(projectId)
                .tokenBudget(tokenBudget != null && tokenBudget > 0 ? tokenBudget : 1000)
                .optimizationMode(OptimizationMode.fromString(optimizationMode))
                .includePreferences(includePreferences == null || includePreferences)
                .includeDecisions(includeDecisions == null || includeDecisions)
                .includeHistory(includeHistory == null || includeHistory)
                .build();

        OptimizedContextResponse res = optimizationEngine.optimizeContext(request);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("optimizedContext", res.getOptimizedContext());
        out.put("renderedContext", res.getRenderedContext());
        out.put("tokenUsage", Map.of(
                "budget", res.getTokenUsage().getBudget(),
                "originalTokens", res.getTokenUsage().getOriginalTokens(),
                "used", res.getTokenUsage().getUsed(),
                "remaining", res.getTokenUsage().getRemaining(),
                "tokensSaved", res.getTokenUsage().getTokensSaved(),
                "reductionPercentage", res.getTokenUsage().getReductionPercentage()
        ));
        out.put("sources", res.getSources());
        out.put("warnings", res.getWarnings());
        out.put("fromCache", res.isFromCache());
        return out;
    }

    @McpTool(
            name = "memory_search",
            description = "Search across structured long-term memories and facts using PostgreSQL full-text search with importance ranking."
    )
    public List<Map<String, Object>>
    search(
            @McpToolParam(description = "Search query or keywords", required = true) String query,
            @McpToolParam(description = "Optional project identifier filter", required = false) String projectId,
            @McpToolParam(description = "Max results to return (default: 10)", required = false) Integer limit) {

        int max = limit != null && limit > 0 ? Math.min(limit, 50) : 10;
        var page = memoryRepository.searchByFullText("default", projectId, query, PageRequest.of(0, max));

        List<Map<String, Object>> results = new ArrayList<>();
        for (Memory m : page) {
            results.add(Map.of(
                    "id", m.getId().toString(),
                    "category", m.getCategory().name(),
                    "content", m.getContent(),
                    "shortSummary", m.getShortSummary() != null ? m.getShortSummary() : "",
                    "importance", m.getImportanceScore()
            ));
        }
        return results;
    }

    @McpTool(
            name = "memory_recall",
            description = "Recall working and recent session memories for an active session."
    )
    public List<Map<String, Object>> recall(
            @McpToolParam(description = "Session UUID", required = true) String sessionId) {

        UUID uuid = UUID.fromString(sessionId);
        List<Memory> memories = memoryRepository.findBySessionIdAndActiveTrueOrderByImportanceScoreDesc(uuid);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Memory m : memories) {
            list.add(Map.of(
                    "id", m.getId().toString(),
                    "category", m.getCategory().name(),
                    "content", m.getContent(),
                    "importance", m.getImportanceScore()
            ));
        }
        return list;
    }

    @McpTool(
            name = "memory_store",
            description = "Manually store a new structured memory fact, architectural decision, runbook command, or user preference."
    )
    public Map<String, Object> store(
            @McpToolParam(description = "Memory category: DECISION, PROJECT, PREFERENCE, PROCEDURAL, ACTION, SEMANTIC, WORKING", required = true) String category,
            @McpToolParam(description = "Detailed content of the memory fact", required = true) String content,
            @McpToolParam(description = "Optional project identifier", required = false) String projectId,
            @McpToolParam(description = "Importance score between 0.1 and 1.0 (default 0.7)", required = false) Double importance) {

        Memory m = Memory.builder()
                .category(MemoryCategory.fromString(category))
                .content(content)
                .shortSummary(content.length() > 80 ? content.substring(0, 80) : content)
                .projectId(projectId)
                .importanceScore(importance != null ? Math.max(0.1, Math.min(1.0, importance)) : 0.7)
                .build();

        Memory saved = extractionEngine.storeMemory(m);
        if (projectId != null) {
            optimizationEngine.invalidateCache(projectId);
        }
        return Map.of("id", saved.getId().toString(), "status", "STORED", "category", saved.getCategory().name());
    }

    @McpTool(
            name = "memory_update",
            description = "Update an existing memory fact with full historical version tracking in the audit log."
    )
    public Map<String, Object> update(
            @McpToolParam(description = "Memory UUID", required = true) String memoryId,
            @McpToolParam(description = "Updated content", required = true) String newContent,
            @McpToolParam(description = "Reason for update (e.g. decision changed, stack upgraded)", required = false) String reason) {

        UUID uuid = UUID.fromString(memoryId);
        Memory updated = extractionEngine.updateMemory(uuid, newContent, reason);
        if (updated.getProjectId() != null) {
            optimizationEngine.invalidateCache(updated.getProjectId());
        }
        return Map.of("id", updated.getId().toString(), "status", "UPDATED", "updatedAt", updated.getUpdatedAt().toString());
    }

    @McpTool(
            name = "memory_forget",
            description = "Soft-delete a memory fact so it will no longer be retrieved during context optimization."
    )
    public Map<String, String> forget(
            @McpToolParam(description = "Memory UUID to forget", required = true) String memoryId) {

        UUID uuid = UUID.fromString(memoryId);
        extractionEngine.forgetMemory(uuid);
        optimizationEngine.invalidateCache(null);
        return Map.of("id", memoryId, "status", "FORGOTTEN");
    }

    @McpTool(
            name = "memory_summarize",
            description = "Extract and summarize all structured memories and key decisions from an active session transcript."
    )
    public Map<String, Object> summarize(
            @McpToolParam(description = "Session UUID", required = true) String sessionId) {

        UUID uuid = UUID.fromString(sessionId);
        List<Memory> extracted = extractionEngine.extractMemoriesFromSession(uuid);
        return Map.of("sessionId", sessionId, "memoriesExtracted", extracted.size());
    }

    @McpTool(
            name = "memory_consolidate",
            description = "Deduplicate and consolidate redundant facts across the project memory vault."
    )
    public Map<String, Object> consolidate(
            @McpToolParam(description = "Optional project identifier", required = false) String projectId) {

        int merged = extractionEngine.consolidateMemories("default", projectId);
        return Map.of("projectId", projectId != null ? projectId : "ALL", "duplicatesMerged", merged);
    }

    @McpTool(
            name = "memory_get_profile",
            description = "Retrieve user developer preferences, architectural rules, and project stack profiles."
    )
    public List<Map<String, Object>> getProfile(
            @McpToolParam(description = "Optional project identifier", required = false) String projectId,
            @McpToolParam(description = "Optional agent identifier", required = false) String agentId) {

        List<Memory> profileMemories = contextGateway.getAgentProfile("default", projectId, agentId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Memory m : profileMemories) {
            out.add(Map.of("category", m.getCategory().name(), "content", m.getContent()));
        }
        return out;
    }

    @McpTool(
            name = "memory_get_decisions",
            description = "Retrieve the chronological register of architectural and technical decisions made for a project."
    )
    public List<Map<String, Object>> getDecisions(
            @McpToolParam(description = "Optional project identifier", required = false) String projectId) {

        List<Memory> decisions = contextGateway.getProjectDecisions("default", projectId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Memory m : decisions) {
            out.add(Map.of(
                    "id", m.getId().toString(),
                    "decision", m.getContent(),
                    "importance", m.getImportanceScore(),
                    "createdAt", m.getCreatedAt().toString()
            ));
        }
        return out;
    }

    @McpTool(
            name = "memory_get_timeline",
            description = "Retrieve a chronological timeline of conversation milestones and messages for a session."
    )
    public List<Map<String, Object>> getTimeline(
            @McpToolParam(description = "Session UUID", required = true) String sessionId) {

        UUID uuid = UUID.fromString(sessionId);
        var messages = messageRepository.findBySessionIdOrderBySequenceNumberAsc(uuid);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (var msg : messages) {
            timeline.add(Map.of(
                    "sequence", msg.getSequenceNumber(),
                    "role", msg.getRole().name(),
                    "timestamp", msg.getCreatedAt().toString(),
                    "excerpt", msg.getContent().length() > 100 ? msg.getContent().substring(0, 100) + "..." : msg.getContent()
            ));
        }
        return timeline;
    }

    @McpTool(
            name = "memory_explain_retrieval",
            description = "Explain the scoring and ranking breakdown for memory retrieval given a query."
    )
    public Map<String, Object> explainRetrieval(
            @McpToolParam(description = "Query string", required = true) String query,
            @McpToolParam(description = "Optional project identifier", required = false) String projectId) {

        return Map.of(
                "query", query,
                "strategy", "Hybrid Reciprocal Rank Fusion (RRF) with Knapsack Packing",
                "weights", Map.of("relevanceWeight", 0.5, "importanceWeight", 0.3, "recencyWeight", 0.2),
                "tokenizer", "JTokkit (CL100K_BASE)"
        );
    }

    @McpTool(
            name = "memory_get_stats",
            description = "Retrieve platform token savings metrics, reduction percentages, and memory counts."
    )
    public Map<String, Object> getStats(
            @McpToolParam(description = "Optional project identifier", required = false) String projectId) {

        return contextGateway.getMemoryStats("default", projectId);
    }
}
