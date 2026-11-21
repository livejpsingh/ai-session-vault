package com.jitendra.ai.sessionvault.rag.dto;

import java.util.List;

public record RagSearchResponse(String query, List<RagChunkResult> results) {
}
