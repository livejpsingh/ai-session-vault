package com.jitendra.ai.sessionvault.service.impl;

import com.jitendra.ai.sessionvault.domain.*;
import com.jitendra.ai.sessionvault.repository.*;
import com.jitendra.ai.sessionvault.service.MemoryExtractionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MemoryExtractionEngineImpl implements MemoryExtractionEngine {

    private final MemoryRepository memoryRepository;
    private final MemoryVersionRepository versionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;

    private static final Pattern DECISION_PATTERN = Pattern.compile(
            "(?i)(?:we decided to|decided to|agreed on|agreed that|selected|switched to|will use|chose)\\s+([^.\\n]{10,150})");

    private static final Pattern PREFERENCE_PATTERN = Pattern.compile(
            "(?i)(?:prefer|preference for|always use|never use|convention is to)\\s+([^.\\n]{10,120})");

    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "(?i)(?:TODO|FIXME|NEED TO|ACTION ITEM)[:\\s]+([^.\\n]{10,150})");

    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "(?m)^\\s*(`{1,3})?\\s*(?:\\.\\/mvnw|mvn|docker compose|git|curl|psql|powershell)[^`\\n]{5,150}(`{1,3})?\\s*$");

    private static final String[] TECH_STACK_KEYWORDS = {
            "Java 21", "Java 25", "Spring Boot", "PostgreSQL", "pgvector", "Docker", "Flyway",
            "JTokkit", "Model Context Protocol", "MCP", "Maven", "Gradle", "PowerShell", "REST API"
    };

    @Override
    public List<Memory> extractMemoriesFromSession(UUID sessionId) {
        Optional<ChatSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Session {} not found for memory extraction", sessionId);
            return Collections.emptyList();
        }
        ChatSession session = sessionOpt.get();
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        String tenantId = "default";
        String projectId = session.getProjectName();

        List<Memory> existingMemories = memoryRepository.findBySessionIdAndActiveTrueOrderByImportanceScoreDesc(sessionId);
        Set<String> existingSignatures = new HashSet<>();
        for (Memory m : existingMemories) {
            existingSignatures.add(normalizeSignature(m.getContent()));
        }

        List<Memory> extracted = new ArrayList<>();

        // 1. Scan for Tech Stack & Project Architecture Facts
        Set<String> detectedTech = new HashSet<>();
        for (ChatMessage msg : messages) {
            String content = msg.getContent();
            if (content == null) continue;

            for (String tech : TECH_STACK_KEYWORDS) {
                if (content.toLowerCase().contains(tech.toLowerCase())) {
                    detectedTech.add(tech);
                }
            }
        }

        if (!detectedTech.isEmpty()) {
            String techFact = "Project utilizes " + String.join(", ", detectedTech) + ".";
            String sig = normalizeSignature(techFact);
            if (!existingSignatures.contains(sig)) {
                Memory m = Memory.builder()
                        .tenantId(tenantId)
                        .projectId(projectId)
                        .session(session)
                        .category(MemoryCategory.PROJECT)
                        .content(techFact)
                        .shortSummary("Project Tech Stack: " + String.join(", ", detectedTech))
                        .importanceScore(0.85)
                        .confidenceScore(0.95)
                        .active(true)
                        .build();
                extracted.add(memoryRepository.save(m));
                existingSignatures.add(sig);
            }
        }

        // 2. Scan for Decisions, Preferences, Actions, and Procedural Commands
        for (ChatMessage msg : messages) {
            String content = msg.getContent();
            if (content == null || content.isBlank()) continue;

            // Extract Decisions
            Matcher decMatcher = DECISION_PATTERN.matcher(content);
            while (decMatcher.find()) {
                String fact = "Decision: " + decMatcher.group(1).trim();
                String sig = normalizeSignature(fact);
                if (!existingSignatures.contains(sig)) {
                    Memory m = Memory.builder()
                            .tenantId(tenantId)
                            .projectId(projectId)
                            .session(session)
                            .category(MemoryCategory.DECISION)
                            .content(fact)
                            .shortSummary(truncate(fact, 80))
                            .importanceScore(0.9)
                            .confidenceScore(0.9)
                            .sourceMessageIds(List.of(msg.getId().toString()))
                            .active(true)
                            .build();
                    extracted.add(memoryRepository.save(m));
                    existingSignatures.add(sig);
                }
            }

            // Extract Developer Preferences
            Matcher prefMatcher = PREFERENCE_PATTERN.matcher(content);
            while (prefMatcher.find()) {
                String fact = "Preference: " + prefMatcher.group(1).trim();
                String sig = normalizeSignature(fact);
                if (!existingSignatures.contains(sig)) {
                    Memory m = Memory.builder()
                            .tenantId(tenantId)
                            .projectId(projectId)
                            .session(session)
                            .category(MemoryCategory.PREFERENCE)
                            .content(fact)
                            .shortSummary(truncate(fact, 80))
                            .importanceScore(0.8)
                            .confidenceScore(0.85)
                            .sourceMessageIds(List.of(msg.getId().toString()))
                            .active(true)
                            .build();
                    extracted.add(memoryRepository.save(m));
                    existingSignatures.add(sig);
                }
            }

            // Extract Action Items (TODO / FIXME)
            Matcher actMatcher = ACTION_PATTERN.matcher(content);
            while (actMatcher.find()) {
                String fact = "Action Item: " + actMatcher.group(1).trim();
                String sig = normalizeSignature(fact);
                if (!existingSignatures.contains(sig)) {
                    Memory m = Memory.builder()
                            .tenantId(tenantId)
                            .projectId(projectId)
                            .session(session)
                            .category(MemoryCategory.ACTION)
                            .content(fact)
                            .shortSummary(truncate(fact, 80))
                            .importanceScore(0.7)
                            .confidenceScore(0.8)
                            .sourceMessageIds(List.of(msg.getId().toString()))
                            .active(true)
                            .build();
                    extracted.add(memoryRepository.save(m));
                    existingSignatures.add(sig);
                }
            }

            // Extract Procedural Commands
            Matcher cmdMatcher = COMMAND_PATTERN.matcher(content);
            while (cmdMatcher.find()) {
                String cmd = cmdMatcher.group().trim().replace("`", "");
                String sig = normalizeSignature(cmd);
                if (!existingSignatures.contains(sig) && cmd.length() > 5) {
                    Memory m = Memory.builder()
                            .tenantId(tenantId)
                            .projectId(projectId)
                            .session(session)
                            .category(MemoryCategory.PROCEDURAL)
                            .content("Runbook command: `" + cmd + "`")
                            .shortSummary("Command: " + truncate(cmd, 60))
                            .importanceScore(0.75)
                            .confidenceScore(0.9)
                            .sourceMessageIds(List.of(msg.getId().toString()))
                            .active(true)
                            .build();
                    extracted.add(memoryRepository.save(m));
                    existingSignatures.add(sig);
                }
            }
        }

        log.info("Extracted {} new deterministic memories from session {}", extracted.size(), sessionId);
        return extracted;
    }

    @Override
    public Memory storeMemory(Memory memory) {
        if (memory.getTenantId() == null || memory.getTenantId().isBlank()) {
            memory.setTenantId("default");
        }
        if (memory.getImportanceScore() == null || memory.getImportanceScore() <= 0) {
            memory.setImportanceScore(0.5);
        }
        if (memory.getConfidenceScore() == null || memory.getConfidenceScore() <= 0) {
            memory.setConfidenceScore(1.0);
        }
        memory.setActive(true);
        return memoryRepository.save(memory);
    }

    @Override
    public Memory updateMemory(UUID memoryId, String newContent, String reason) {
        Memory existing = memoryRepository.findById(memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found with ID: " + memoryId));

        int currentVersion = versionRepository.countByMemoryId(memoryId) + 1;

        // Archive previous state to memory_versions table
        MemoryVersion version = MemoryVersion.builder()
                .memory(existing)
                .versionNumber(currentVersion)
                .content(existing.getContent())
                .reasonForUpdate(reason != null ? reason : "Direct update")
                .createdAt(OffsetDateTime.now())
                .build();
        versionRepository.save(version);

        // Update memory
        existing.setContent(newContent);
        if (existing.getShortSummary() == null || existing.getShortSummary().isBlank()) {
            existing.setShortSummary(truncate(newContent, 80));
        }
        existing.setUpdatedAt(OffsetDateTime.now());

        log.info("Updated memory {} to version {}", memoryId, currentVersion + 1);
        return memoryRepository.save(existing);
    }

    @Override
    public void forgetMemory(UUID memoryId) {
        memoryRepository.findById(memoryId).ifPresent(m -> {
            m.setActive(false);
            memoryRepository.save(m);
            log.info("Soft-deleted memory {}", memoryId);
        });
    }

    @Override
    public int consolidateMemories(String tenantId, String projectId) {
        List<Memory> all = (projectId != null && !projectId.isBlank())
                ? memoryRepository.findByTenantIdAndProjectIdAndActiveTrueOrderByImportanceScoreDesc(tenantId, projectId)
                : memoryRepository.findByTenantIdAndActiveTrueOrderByImportanceScoreDesc(tenantId);

        Map<String, List<Memory>> groups = new HashMap<>();
        for (Memory m : all) {
            String sig = m.getCategory() + ":" + normalizeSignature(m.getContent());
            groups.computeIfAbsent(sig, k -> new ArrayList<>()).add(m);
        }

        int mergedCount = 0;
        for (Map.Entry<String, List<Memory>> entry : groups.entrySet()) {
            List<Memory> duplicates = entry.getValue();
            if (duplicates.size() > 1) {
                // Keep the one with highest importance or latest update
                Memory primary = duplicates.get(0);
                for (int i = 1; i < duplicates.size(); i++) {
                    Memory dup = duplicates.get(i);
                    dup.setActive(false);
                    memoryRepository.save(dup);
                    mergedCount++;
                }
            }
        }

        log.info("Consolidated memories for tenant='{}', project='{}': merged {} duplicates",
                tenantId, projectId, mergedCount);
        return mergedCount;
    }

    private String normalizeSignature(String text) {
        if (text == null) return "";
        return text.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
