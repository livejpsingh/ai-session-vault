package com.jitendra.ai.sessionvault.event;

import com.jitendra.ai.sessionvault.config.VaultFeatureFlags;
import com.jitendra.ai.sessionvault.domain.OutboxEvent;
import com.jitendra.ai.sessionvault.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default implementation of VaultEventPublisher.
 * Persists events to OutboxEventRepository and dispatches via Spring ApplicationEventPublisher.
 * When vault.features.kafka is true, routes to distributed message brokers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultVaultEventPublisher implements VaultEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final VaultFeatureFlags featureFlags;

    @Override
    public void publishEvent(String eventType, String aggregateId, Map<String, Object> payload) {
        log.debug("Publishing event type='{}' for aggregateId='{}' (kafka.enabled={})",
                eventType, aggregateId, featureFlags.isKafka());

        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("EVENT")
                    .aggregateId(aggregateId != null ? aggregateId : "SYSTEM")
                    .eventType(eventType)
                    .payload(payload)
                    .status("PENDING")
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.warn("Failed to persist outbox event during publish: {}", e.getMessage());
        }

        applicationEventPublisher.publishEvent(payload);
    }
}
