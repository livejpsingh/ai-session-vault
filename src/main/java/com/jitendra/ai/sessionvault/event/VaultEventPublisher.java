package com.jitendra.ai.sessionvault.event;

import java.util.Map;

/**
 * Enterprise Event Publisher Interface (Phase 5).
 * Allows event-driven scaling over in-memory Spring ApplicationEvents or distributed Kafka brokers.
 */
public interface VaultEventPublisher {

    void publishEvent(String eventType, String aggregateId, Map<String, Object> payload);
}
