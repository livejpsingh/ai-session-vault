package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.domain.*;
import com.jitendra.ai.sessionvault.repository.*;
import com.jitendra.ai.sessionvault.service.impl.MemoryExtractionEngineImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryExtractionEngineTest {

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryVersionRepository versionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ChatSessionRepository sessionRepository;

    private MemoryExtractionEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new MemoryExtractionEngineImpl(
                memoryRepository,
                versionRepository,
                messageRepository,
                sessionRepository
        );
    }

    @Test
    @DisplayName("Should deterministically extract decisions and tech stack facts from messages")
    void shouldExtractDecisionsAndTechStack() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.builder()
                .id(sessionId)
                .projectName("ai-session-vault")
                .build();

        ChatMessage msg1 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sequenceNumber(1)
                .role(MessageRole.USER)
                .content("We decided to migrate our search engine to PostgreSQL 18 with pgvector.")
                .build();

        ChatMessage msg2 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sequenceNumber(2)
                .role(MessageRole.MODEL)
                .content("I will configure Spring Boot 4.1.2 and Flyway migrations for pgvector.")
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId)).thenReturn(List.of(msg1, msg2));
        when(memoryRepository.findBySessionIdAndActiveTrueOrderByImportanceScoreDesc(sessionId)).thenReturn(List.of());
        when(memoryRepository.save(any(Memory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Memory> extracted = engine.extractMemoriesFromSession(sessionId);

        assertThat(extracted).isNotEmpty();
        boolean hasDecision = extracted.stream().anyMatch(m -> m.getCategory() == MemoryCategory.DECISION);
        boolean hasProject = extracted.stream().anyMatch(m -> m.getCategory() == MemoryCategory.PROJECT);
        assertThat(hasDecision).isTrue();
        assertThat(hasProject).isTrue();
    }

    @Test
    @DisplayName("Should archive historical version when updating a memory")
    void shouldArchiveVersionOnUpdate() {
        UUID memoryId = UUID.randomUUID();
        Memory existing = Memory.builder()
                .id(memoryId)
                .category(MemoryCategory.DECISION)
                .content("Initial decision: Use H2 for tests.")
                .importanceScore(0.8)
                .build();

        when(memoryRepository.findById(memoryId)).thenReturn(Optional.of(existing));
        when(versionRepository.countByMemoryId(memoryId)).thenReturn(1);
        when(memoryRepository.save(any(Memory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Memory updated = engine.updateMemory(memoryId, "Updated decision: Use PostgreSQL 18 for tests.", "Switched test database");

        assertThat(updated.getContent()).isEqualTo("Updated decision: Use PostgreSQL 18 for tests.");
        verify(versionRepository, times(1)).save(any(MemoryVersion.class));
    }
}
