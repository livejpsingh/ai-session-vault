package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.domain.Memory;

import java.util.List;
import java.util.UUID;

public interface MemoryExtractionEngine {

    /**
     * Deterministically extracts structured memories from a session's chat transcript.
     * Operates with $0 LLM cost using heuristic pattern extraction.
     *
     * @param sessionId the UUID of the session
     * @return list of newly created or updated memories
     */
    List<Memory> extractMemoriesFromSession(UUID sessionId);

    /**
     * Stores or updates a memory.
     */
    Memory storeMemory(Memory memory);

    /**
     * Updates an existing memory with full historical audit versioning.
     */
    Memory updateMemory(UUID memoryId, String newContent, String reason);

    /**
     * Soft-deletes a memory by setting active = false.
     */
    void forgetMemory(UUID memoryId);

    /**
     * Consolidates duplicate and related memories for a given tenant and project.
     */
    int consolidateMemories(String tenantId, String projectId);
}
