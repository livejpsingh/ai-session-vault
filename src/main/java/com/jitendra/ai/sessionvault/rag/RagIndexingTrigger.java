package com.jitendra.ai.sessionvault.rag;

import java.util.UUID;

public interface RagIndexingTrigger {

    void requestIndex(UUID sessionId);
}
