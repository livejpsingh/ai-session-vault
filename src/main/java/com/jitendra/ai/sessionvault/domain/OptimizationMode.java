package com.jitendra.ai.sessionvault.domain;

public enum OptimizationMode {
    BALANCED,
    AGGRESSIVE,
    QUALITY_FIRST;

    public static OptimizationMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        try {
            return OptimizationMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BALANCED;
        }
    }
}
