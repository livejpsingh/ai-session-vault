package com.jitendra.ai.sessionvault.rag.dto;

import java.util.UUID;

public record ReindexResponse(UUID sessionId, String status, String message) {
}
