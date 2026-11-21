package com.jitendra.ai.sessionvault.service.impl;

import com.jitendra.ai.sessionvault.config.VaultFeatureFlags;
import com.jitendra.ai.sessionvault.domain.OutboxEvent;
import com.jitendra.ai.sessionvault.repository.OutboxEventRepository;
import com.jitendra.ai.sessionvault.service.MemoryExtractionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transactional Outbox Background Worker (Mitigates Risks R3 and R7).
 * Recovers and processes pending transcript ingestion events to guarantee zero memory loss on app crashes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final MemoryExtractionEngine memoryExtractionEngine;
    private final VaultFeatureFlags featureFlags;

    @Scheduled(fixedDelayString = "${vault.outbox.poll-interval:10000}")
    @Transactional
    public void processPendingEvents() {
        if (!featureFlags.isAsyncExtraction()) {
            return;
        }

        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING", PageRequest.of(0, 20));
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                if ("TRANSCRIPT_INGESTED".equals(event.getEventType())) {
                    String sessionIdStr = (String) event.getPayload().get("sessionId");
                    if (sessionIdStr != null) {
                        UUID sessionId = UUID.fromString(sessionIdStr);
                        memoryExtractionEngine.extractMemoriesFromSession(sessionId);
                    }
                }
                event.setStatus("PROCESSED");
                event.setProcessedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event {}: {}", event.getId(), e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());
                if (event.getRetryCount() >= 5) {
                    event.setStatus("FAILED");
                }
                outboxEventRepository.save(event);
            }
        }
    }
}
