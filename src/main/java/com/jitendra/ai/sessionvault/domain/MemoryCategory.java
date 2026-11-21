package com.jitendra.ai.sessionvault.domain;

public enum MemoryCategory {
    WORKING,
    EPISODIC,
    SEMANTIC,
    PROCEDURAL,
    DECISION,
    PROJECT,
    PREFERENCE,
    ACTION;

    public static MemoryCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return SEMANTIC;
        }
        try {
            return MemoryCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SEMANTIC;
        }
    }
}
