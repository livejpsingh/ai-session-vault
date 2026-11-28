package com.jitendra.ai.sessionvault.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.domain.*;
import com.jitendra.ai.sessionvault.dto.ContextOptimizationRequest;
import com.jitendra.ai.sessionvault.dto.OptimizedContextResponse;
import com.jitendra.ai.sessionvault.rag.chunking.JTokkitTokenCounter;
import com.jitendra.ai.sessionvault.repository.*;
import com.jitendra.ai.sessionvault.service.impl.ContextOptimizationEngineImpl;
import com.jitendra.ai.sessionvault.service.impl.MemoryExtractionEngineImpl;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Reproducible benchmark suite verifying the core performance and token-optimization
 * metrics claimed in README.md:
 * 1. 70%–90%+ token reduction on realistic multi-turn session memories
 * 2. Zero silent budget overruns (used <= tokenBudget in 100% of runs)
 * 3. Sub-30ms latency on SHA-256 context cache hits
 * 4. $0-LLM-cost deterministic memory extraction throughput
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerformanceAndReductionBenchmarkTest {

    @Mock
    private MemoryRepository memoryRepository;
    @Mock
    private TokenUsageMetricRepository metricRepository;
    @Mock
    private ContextCacheRepository cacheRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private MemoryVersionRepository versionRepository;
    @Mock
    private ChatSessionRepository sessionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JTokkitTokenCounter tokenCounter = new JTokkitTokenCounter(EncodingType.CL100K_BASE);

    private ContextOptimizationEngineImpl optimizer;
    private MemoryExtractionEngineImpl extractor;

    @BeforeEach
    void setUp() {
        optimizer = new ContextOptimizationEngineImpl(
                memoryRepository,
                metricRepository,
                cacheRepository,
                messageRepository,
                objectMapper,
                tokenCounter
        );
        extractor = new MemoryExtractionEngineImpl(
                memoryRepository,
                versionRepository,
                messageRepository,
                sessionRepository
        );
        lenient().when(cacheRepository.findByCacheKeyAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Benchmark: Achieves 70%-90%+ token reduction across bloated session memories")
    void benchmarkTokenReduction70To90Percent() {
        List<Memory> bloatedMemories = buildRealisticMemoryCorpus(40);

        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), eq(MemoryCategory.DECISION)))
                .thenReturn(bloatedMemories.subList(0, 10));
        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), eq(MemoryCategory.PROJECT)))
                .thenReturn(bloatedMemories.subList(10, 20));
        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), eq(MemoryCategory.PREFERENCE)))
                .thenReturn(bloatedMemories.subList(20, 25));
        when(memoryRepository.searchByFullText(anyString(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(bloatedMemories));

        OptimizedContextResponse balanced = optimizer.optimizeContext(ContextOptimizationRequest.builder()
                .query("How is PostgreSQL, Flyway, and MCP configured?")
                .projectId("ai-session-vault")
                .tokenBudget(500)
                .optimizationMode(OptimizationMode.BALANCED)
                .build());

        OptimizedContextResponse aggressive = optimizer.optimizeContext(ContextOptimizationRequest.builder()
                .query("How is PostgreSQL, Flyway, and MCP configured?")
                .projectId("ai-session-vault")
                .tokenBudget(300)
                .optimizationMode(OptimizationMode.AGGRESSIVE)
                .build());

        System.out.printf("[BENCHMARK] BALANCED   original=%d, used=%d, reduction=%.2f%%, latency=%dms%n",
                balanced.getTokenUsage().getOriginalTokens(),
                balanced.getTokenUsage().getUsed(),
                balanced.getTokenUsage().getReductionPercentage(),
                balanced.getLatencyMs());

        System.out.printf("[BENCHMARK] AGGRESSIVE original=%d, used=%d, reduction=%.2f%%, latency=%dms%n",
                aggressive.getTokenUsage().getOriginalTokens(),
                aggressive.getTokenUsage().getUsed(),
                aggressive.getTokenUsage().getReductionPercentage(),
                aggressive.getLatencyMs());

        assertThat(balanced.getTokenUsage().getUsed()).isLessThanOrEqualTo(500);
        assertThat(balanced.getTokenUsage().getReductionPercentage()).isGreaterThanOrEqualTo(70.0);

        assertThat(aggressive.getTokenUsage().getUsed()).isLessThanOrEqualTo(300);
        assertThat(aggressive.getTokenUsage().getReductionPercentage()).isGreaterThanOrEqualTo(80.0);
    }

    @Test
    @DisplayName("Benchmark: Zero silent budget overruns across budgets 50..2000")
    void benchmarkZeroSilentBudgetOverruns() {
        List<Memory> corpus = buildRealisticMemoryCorpus(30);
        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), any()))
                .thenReturn(corpus.subList(0, 10));
        when(memoryRepository.searchByFullText(anyString(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(corpus));

        int[] budgets = {50, 100, 250, 500, 1000, 2000};
        for (int budget : budgets) {
            for (OptimizationMode mode : OptimizationMode.values()) {
                OptimizedContextResponse response = optimizer.optimizeContext(ContextOptimizationRequest.builder()
                        .query("architecture constraints and database setup")
                        .tokenBudget(budget)
                        .optimizationMode(mode)
                        .build());

                assertThat(response.getTokenUsage().getUsed())
                        .as("Budget overrun for budget=%d mode=%s", budget, mode)
                        .isLessThanOrEqualTo(budget);
            }
        }
    }

    @Test
    @DisplayName("Benchmark: SHA-256 context cache hits serve in < 30 ms")
    void benchmarkCacheHitSub30Ms() {
        OptimizedContextResponse samplePayload = OptimizedContextResponse.builder()
                .query("database setup")
                .renderedContext("### PROJECT CONTEXT\n- PostgreSQL 16 + pgvector HNSW")
                .optimizedContext(List.of())
                .sources(List.of("memory:decision"))
                .warnings(List.of())
                .fromCache(false)
                .latencyMs(12L)
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> cachedMap = objectMapper.convertValue(samplePayload, Map.class);

        ContextCacheEntry cacheEntry = ContextCacheEntry.builder()
                .id(UUID.randomUUID())
                .cacheKey("mock-sha256-key")
                .cachedContext(cachedMap)
                .tokenCount(14)
                .expiresAt(OffsetDateTime.now().plusMinutes(30))
                .build();

        when(cacheRepository.findByCacheKeyAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(cacheEntry));

        // Warmup JVM / Jackson reflection before measuring steady-state cache latency
        for (int w = 0; w < 5; w++) {
            optimizer.optimizeContext(ContextOptimizationRequest.builder()
                    .query("database setup")
                    .projectId("ai-session-vault")
                    .tokenBudget(500)
                    .optimizationMode(OptimizationMode.BALANCED)
                    .build());
        }

        int iterations = 50;
        long totalMs = 0;
        long maxMs = 0;

        for (int i = 0; i < iterations; i++) {
            OptimizedContextResponse res = optimizer.optimizeContext(ContextOptimizationRequest.builder()
                    .query("database setup")
                    .projectId("ai-session-vault")
                    .tokenBudget(500)
                    .optimizationMode(OptimizationMode.BALANCED)
                    .build());

            assertThat(res.isFromCache()).isTrue();
            totalMs += res.getLatencyMs();
            maxMs = Math.max(maxMs, res.getLatencyMs());
        }

        double avgMs = (double) totalMs / iterations;
        System.out.printf("[BENCHMARK] Cache Hit Latency over %d runs: avg=%.2fms, max=%dms%n",
                iterations, avgMs, maxMs);

        assertThat(avgMs).isLessThan(30.0);
        assertThat(maxMs).isLessThan(30L);
    }

    @Test
    @DisplayName("Benchmark: $0-cost deterministic memory extraction extracts multi-category facts locally")
    void benchmarkZeroCostDeterministicExtraction() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.builder()
                .id(sessionId)
                .projectName("ai-session-vault")
                .build();

        List<ChatMessage> messages = List.of(
                ChatMessage.builder().id(UUID.randomUUID()).sequenceNumber(1).role(MessageRole.USER)
                        .content("We decided to use PostgreSQL with pgvector and Flyway for schema migrations. Always use PowerShell on Windows.").build(),
                ChatMessage.builder().id(UUID.randomUUID()).sequenceNumber(2).role(MessageRole.MODEL)
                        .content("Sure! Here is the setup using Java 25, Spring Boot, and JTokkit.\n```bash\ndocker compose up -d\n```\nTODO: Add PostgreSQL HA documentation.").build()
        );

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId)).thenReturn(messages);
        when(memoryRepository.findBySessionIdAndActiveTrueOrderByImportanceScoreDesc(sessionId)).thenReturn(List.of());
        when(memoryRepository.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));

        long startNs = System.nanoTime();
        List<Memory> extracted = extractor.extractMemoriesFromSession(sessionId);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        System.out.printf("[BENCHMARK] $0-Cost Extraction: extracted=%d facts in %dms%n",
                extracted.size(), elapsedMs);

        assertThat(extracted).isNotEmpty();
        assertThat(extracted).extracting(Memory::getCategory)
                .contains(MemoryCategory.PROJECT, MemoryCategory.DECISION);
        assertThat(elapsedMs).isLessThan(500L);
    }

    private List<Memory> buildRealisticMemoryCorpus(int count) {
        List<Memory> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MemoryCategory cat = switch (i % 4) {
                case 0 -> MemoryCategory.DECISION;
                case 1 -> MemoryCategory.PROJECT;
                case 2 -> MemoryCategory.PREFERENCE;
                default -> MemoryCategory.PROCEDURAL;
            };
            String content = "Sure! Here is context item #" + i + ": We decided to configure PostgreSQL 16 with pgvector HNSW "
                    + "indexes (m=16, ef_construction=64), Flyway migrations V1-V5, Spring Boot 4.2.0, and JTokkit CL100K_BASE "
                    + "token counting for deterministic budget packing across autonomous coding agents. "
                    + "Hope this helps! Let me know if you need more details on item " + i + ".";
            list.add(Memory.builder()
                    .id(UUID.randomUUID())
                    .category(cat)
                    .content(content)
                    .shortSummary("Item #" + i + ": PostgreSQL pgvector HNSW + Flyway V1-V5 + JTokkit.")
                    .importanceScore(0.95 - (i * 0.01))
                    .active(true)
                    .build());
        }
        return list;
    }
}
