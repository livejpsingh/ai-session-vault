package com.jitendra.ai.sessionvault.domain;

public enum MessageRole {
    USER,
    MODEL,
    SYSTEM,
    TOOL;

    public static MessageRole fromString(String role) {
        if (role == null) {
            return USER;
        }
        return switch (role.toUpperCase().trim()) {
            case "USER", "USER_INPUT", "USER_EXPLICIT" -> USER;
            case "MODEL", "ASSISTANT", "PLANNER_RESPONSE" -> MODEL;
            case "SYSTEM" -> SYSTEM;
            case "TOOL", "TOOL_RESULT" -> TOOL;
            default -> USER;
        };
    }
}
