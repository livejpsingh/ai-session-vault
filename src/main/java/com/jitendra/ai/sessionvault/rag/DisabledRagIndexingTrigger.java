package com.jitendra.ai.sessionvault.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "false")
public class DisabledRagIndexingTrigger implements RagIndexingTrigger {

    @Override
    public void requestIndex(UUID sessionId) {
    }
}
