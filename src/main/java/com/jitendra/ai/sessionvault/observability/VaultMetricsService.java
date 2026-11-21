package com.jitendra.ai.sessionvault.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Enterprise Observability & Micrometer Metrics Service.
 * Tracks live ingestion throughput, token reduction performance, and context caching hit ratios.
 */
@Service
public class VaultMetricsService {

    private final MeterRegistry meterRegistry;
    private final Counter totalTranscriptsIngested;
    private final Counter totalMessagesPersisted;
    private final Counter totalTokensSaved;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    public VaultMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.totalTranscriptsIngested = Counter.builder("vault.transcripts.ingested.total")
                .description("Total number of transcripts successfully ingested")
                .register(meterRegistry);

        this.totalMessagesPersisted = Counter.builder("vault.messages.persisted.total")
                .description("Total number of individual chat messages persisted")
                .register(meterRegistry);

        this.totalTokensSaved = Counter.builder("vault.tokens.saved.total")
                .description("Cumulative count of prompt tokens saved by the Knapsack Optimizer")
                .register(meterRegistry);

        this.cacheHits = Counter.builder("vault.cache.hits.total")
                .description("Context optimization cache hits (SHA-256)")
                .register(meterRegistry);

        this.cacheMisses = Counter.builder("vault.cache.misses.total")
                .description("Context optimization cache misses")
                .register(meterRegistry);
    }

    public void recordTranscriptIngestion(String provider, int messageCount) {
        totalTranscriptsIngested.increment();
        totalMessagesPersisted.increment(messageCount);
        Counter.builder("vault.transcripts.by.provider")
                .tag("provider", provider != null ? provider : "unknown")
                .register(meterRegistry)
                .increment();
    }

    public void recordTokenOptimization(String model, int savedTokens, long durationMs) {
        if (savedTokens > 0) {
            totalTokensSaved.increment(savedTokens);
            Counter.builder("vault.tokens.saved.by.model")
                    .tag("model", model != null ? model : "unknown")
                    .register(meterRegistry)
                    .increment(savedTokens);
        }
        Timer.builder("vault.optimization.duration")
                .tag("model", model != null ? model : "unknown")
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }
}
