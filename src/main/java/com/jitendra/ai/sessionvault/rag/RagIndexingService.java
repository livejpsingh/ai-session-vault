package com.jitendra.ai.sessionvault.rag;

import com.jitendra.ai.sessionvault.exception.ResourceNotFoundException;
import com.jitendra.ai.sessionvault.rag.dto.ReindexAllResponse;
import com.jitendra.ai.sessionvault.rag.dto.ReindexResponse;
import com.jitendra.ai.sessionvault.rag.dto.ReindexStatusResponse;
import com.jitendra.ai.sessionvault.rag.repository.RagIndexJobRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagIndexingService implements RagIndexingTrigger {

    private final RagIndexJobRepository jobRepository;
    private final ChatSessionRepository sessionRepository;
    private final RagProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requestIndex(UUID sessionId) {
        jobRepository.enqueue(sessionId, activeEmbeddingModelId());
        eventPublisher.publishEvent(new RagIndexRequestedEvent(sessionId));
    }

    @Transactional
    public ReindexResponse reindex(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Chat session not found with ID: " + sessionId);
        }
        requestIndex(sessionId);
        return new ReindexResponse(sessionId, "PENDING", "Session queued for reindexing.");
    }

    @Transactional
    public ReindexAllResponse reindexAll() {
        int queued = jobRepository.enqueueAll(activeEmbeddingModelId());
        return new ReindexAllResponse(queued, "PENDING");
    }

    @Transactional(readOnly = true)
    public ReindexStatusResponse status(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Chat session not found with ID: " + sessionId);
        }
        return jobRepository.findStatus(sessionId)
                .map(status -> new ReindexStatusResponse(
                        sessionId, status.status(), status.attempts(), status.nextAttemptAt(), status.updatedAt()))
                .orElseGet(() -> new ReindexStatusResponse(sessionId, "NOT_REQUESTED", 0, null, null));
    }

    public String activeEmbeddingModelId() {
        return properties.getProvider() + ":" + properties.getEmbeddingModel();
    }
}
