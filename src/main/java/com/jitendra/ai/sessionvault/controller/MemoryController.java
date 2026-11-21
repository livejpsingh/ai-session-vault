package com.jitendra.ai.sessionvault.controller;

import com.jitendra.ai.sessionvault.domain.Memory;
import com.jitendra.ai.sessionvault.domain.MemoryCategory;
import com.jitendra.ai.sessionvault.dto.ContextOptimizationRequest;
import com.jitendra.ai.sessionvault.dto.OptimizedContextResponse;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.MemoryRepository;
import com.jitendra.ai.sessionvault.service.AgentContextGateway;
import com.jitendra.ai.sessionvault.service.ContextOptimizationEngine;
import com.jitendra.ai.sessionvault.service.MemoryExtractionEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Intelligent Memory & Token Optimization", description = "Endpoints for context compression, hierarchical memory retrieval, and LLM token optimization")
public class MemoryController {

    private final ContextOptimizationEngine optimizationEngine;
    private final MemoryExtractionEngine extractionEngine;
    private final AgentContextGateway contextGateway;
    private final MemoryRepository memoryRepository;
    private final ChatMessageRepository messageRepository;

    @PostMapping("/optimize-context")
    @Operation(summary = "Optimize context to fit strictly within a token budget (70%-90% reduction)")
    public ResponseEntity<OptimizedContextResponse> optimizeContext(
            @Valid @RequestBody ContextOptimizationRequest request) {
        log.info("REST: optimizeContext requested for query='{}', budget={}", request.getQuery(), request.getTokenBudget());
        OptimizedContextResponse response = optimizationEngine.optimizeContext(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/store")
    @Operation(summary = "Manually store a structured memory fact, architectural decision, or runbook")
    public ResponseEntity<Memory> storeMemory(@RequestBody com.jitendra.ai.sessionvault.dto.StoreMemoryRequest request) {
        Memory memory = Memory.builder()
                .tenantId(request.getTenantId() != null ? request.getTenantId() : "default")
                .projectId(request.getProjectId())
                .category(request.getCategory() != null ? request.getCategory() : MemoryCategory.SEMANTIC)
                .content(request.getContent())
                .shortSummary(request.getShortSummary())
                .importanceScore(request.getImportanceScore() != null ? request.getImportanceScore() : 0.5)
                .confidenceScore(request.getConfidenceScore() != null ? request.getConfidenceScore() : 1.0)
                .sourceMessageIds(request.getSourceMessageIds() != null ? request.getSourceMessageIds() : new ArrayList<>())
                .metadata(request.getMetadata())
                .active(true)
                .build();
        Memory stored = extractionEngine.storeMemory(memory);
        return ResponseEntity.status(HttpStatus.CREATED).body(stored);
    }

    @PostMapping("/search")
    @Operation(summary = "Full-text search across memories with importance ranking")
    public ResponseEntity<Page<Memory>> searchMemories(
            @RequestParam String query,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Memory> results = memoryRepository.searchByFullText(tenantId, projectId, query, PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }

    @PostMapping("/recall")
    @Operation(summary = "Recall structured memories for an active session")
    public ResponseEntity<List<Memory>> recallSessionMemories(@RequestParam UUID sessionId) {
        List<Memory> memories = memoryRepository.findBySessionIdAndActiveTrueOrderByImportanceScoreDesc(sessionId);
        return ResponseEntity.ok(memories);
    }

    @PostMapping("/consolidate")
    @Operation(summary = "Consolidate and deduplicate memories for a project")
    public ResponseEntity<Map<String, Object>> consolidateMemories(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) String projectId) {
        int merged = extractionEngine.consolidateMemories(tenantId, projectId);
        return ResponseEntity.ok(Map.of(
                "status", "CONSOLIDATED",
                "duplicatesMerged", merged,
                "tenantId", tenantId,
                "projectId", projectId != null ? projectId : "ALL"
        ));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing memory with historical version audit logging")
    public ResponseEntity<Memory> updateMemory(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        String reason = body.getOrDefault("reason", "API update");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Memory updated = extractionEngine.updateMemory(id, content, reason);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a memory")
    public ResponseEntity<Void> forgetMemory(@PathVariable UUID id) {
        extractionEngine.forgetMemory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    @Operation(summary = "Retrieve user preferences and project architecture facts")
    public ResponseEntity<List<Memory>> getProfile(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String agentId) {
        return ResponseEntity.ok(contextGateway.getAgentProfile(tenantId, projectId, agentId));
    }

    @GetMapping("/decisions")
    @Operation(summary = "Retrieve chronological list of architectural decisions")
    public ResponseEntity<List<Memory>> getDecisions(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) String projectId) {
        return ResponseEntity.ok(contextGateway.getProjectDecisions(tenantId, projectId));
    }

    @GetMapping("/timeline")
    @Operation(summary = "Retrieve conversation timeline for a session")
    public ResponseEntity<List<Map<String, Object>>> getTimeline(@RequestParam UUID sessionId) {
        var messages = messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (var msg : messages) {
            timeline.add(Map.of(
                    "sequence", msg.getSequenceNumber(),
                    "role", msg.getRole().name(),
                    "timestamp", msg.getCreatedAt().toString(),
                    "excerpt", msg.getContent().length() > 100 ? msg.getContent().substring(0, 100) + "..." : msg.getContent()
            ));
        }
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/stats")
    @Operation(summary = "Retrieve token savings analytics and memory counts")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) String projectId) {
        return ResponseEntity.ok(contextGateway.getMemoryStats(tenantId, projectId));
    }
}
