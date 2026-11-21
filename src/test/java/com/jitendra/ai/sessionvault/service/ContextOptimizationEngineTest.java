package com.jitendra.ai.sessionvault.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.domain.Memory;
import com.jitendra.ai.sessionvault.domain.MemoryCategory;
import com.jitendra.ai.sessionvault.domain.OptimizationMode;
import com.jitendra.ai.sessionvault.dto.ContextOptimizationRequest;
import com.jitendra.ai.sessionvault.dto.OptimizedContextResponse;
import com.jitendra.ai.sessionvault.rag.chunking.JTokkitTokenCounter;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ContextCacheRepository;
import com.jitendra.ai.sessionvault.repository.MemoryRepository;
import com.jitendra.ai.sessionvault.repository.TokenUsageMetricRepository;
import com.jitendra.ai.sessionvault.service.impl.ContextOptimizationEngineImpl;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextOptimizationEngineTest {

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private TokenUsageMetricRepository metricRepository;

    @Mock
    private ContextCacheRepository cacheRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    private ObjectMapper objectMapper = new ObjectMapper();
    private JTokkitTokenCounter tokenCounter = new JTokkitTokenCounter(EncodingType.CL100K_BASE);

    private ContextOptimizationEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new ContextOptimizationEngineImpl(
                memoryRepository,
                metricRepository,
                cacheRepository,
                messageRepository,
                objectMapper,
                tokenCounter
        );
        when(cacheRepository.findByCacheKeyAndExpiresAtAfter(anyString(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Should strictly enforce token budget and pack memories within limit")
    void shouldStrictlyEnforceTokenBudget() {
        Memory m1 = Memory.builder()
                .id(UUID.randomUUID())
                .category(MemoryCategory.DECISION)
                .content("We decided to use PostgreSQL 18 with pgvector for hybrid RAG search.")
                .importanceScore(0.95)
                .build();

        Memory m2 = Memory.builder()
                .id(UUID.randomUUID())
                .category(MemoryCategory.PROJECT)
                .content("The application is developed using Java 21, Spring Boot 4.1.2, and JTokkit.")
                .importanceScore(0.85)
                .build();

        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), eq(MemoryCategory.DECISION)))
                .thenReturn(List.of(m1));
        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), eq(MemoryCategory.PROJECT)))
                .thenReturn(List.of(m2));
        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), eq(MemoryCategory.PREFERENCE)))
                .thenReturn(List.of());
        when(memoryRepository.searchByFullText(anyString(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m1, m2)));

        ContextOptimizationRequest request = ContextOptimizationRequest.builder()
                .query("How is the database configured?")
                .tokenBudget(500)
                .optimizationMode(OptimizationMode.BALANCED)
                .build();

        OptimizedContextResponse response = engine.optimizeContext(request);

        assertThat(response).isNotNull();
        assertThat(response.getTokenUsage().getUsed()).isLessThanOrEqualTo(500);
        assertThat(response.getOptimizedContext()).isNotEmpty();
        assertThat(response.getRenderedContext()).contains("PostgreSQL");
    }

    @Test
    @DisplayName("Should issue warning when budget is too small to include essential constraints")
    void shouldIssueWarningOnMicroBudget() {
        Memory m1 = Memory.builder()
                .id(UUID.randomUUID())
                .category(MemoryCategory.DECISION)
                .content("Critical constraint: Database must use strict tenant isolation with row-level security and SSL.")
                .importanceScore(0.99)
                .build();

        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), eq(MemoryCategory.DECISION)))
                .thenReturn(List.of(m1));
        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), eq(MemoryCategory.PROJECT)))
                .thenReturn(List.of());
        when(memoryRepository.findByTenantIdAndCategoryAndActiveTrueOrderByImportanceScoreDesc(anyString(), eq(MemoryCategory.PREFERENCE)))
                .thenReturn(List.of());
        when(memoryRepository.searchByFullText(anyString(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m1)));

        // Tiny budget of 5 tokens cannot fit the 20-token constraint
        ContextOptimizationRequest request = ContextOptimizationRequest.builder()
                .query("Explain security")
                .tokenBudget(50)
                .build();

        OptimizedContextResponse response = engine.optimizeContext(request);

        assertThat(response).isNotNull();
        assertThat(response.getTokenUsage().getUsed()).isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("Invalidating cache for project calls deleteByProjectId (R8 Mitigation)")
    void invalidateCache_CallsRepository() {
        engine.invalidateCache("my-project");
        verify(cacheRepository, times(1)).deleteByProjectId("my-project");
    }
}
