package com.jitendra.ai.sessionvault.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI Session Vault 3.0 Master Feature Flag Matrix.
 * All advanced/experimental capabilities are decoupled behind feature flags,
 * defaulting to false in production to strictly preserve the 5 core product contracts.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vault.features")
public class VaultFeatureFlags {

    // Phase 1: Foundational Hardening & Observability
    private boolean testcontainers = true;
    private boolean dockerCompose = true;
    private boolean securityHardening = false;
    private boolean observability = true;

    // Phase 2: Spring AI Native Alignment
    private boolean springAiNative = false;
    private boolean vectorStoreAdapter = false;

    // Phase 3: Resilience, Streaming & Multi-Layer Performance
    private boolean resilience4j = false;
    private boolean streaming = false;
    private boolean multiLayerCache = false;
    private boolean asyncExtraction = false;

    // Phase 4: Enterprise Safety, Guardrails & Multi-Tenancy
    private boolean guardrails = false;
    private boolean multitenancy = false;

    // Phase 5: Event-Driven Scaling
    private boolean kafka = false;
}
