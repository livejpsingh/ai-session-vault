package com.jitendra.ai.sessionvault.rag.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record RagSearchFilters(
        String project,
        String agent,
        List<@NotBlank String> tags,
        Map<String, Object> metadata) {
}
