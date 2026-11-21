package com.jitendra.ai.sessionvault.rag.dto;

import java.util.List;

public record RelatedSessionsResponse(String query, List<RelatedSessionResult> sessions) {
}
