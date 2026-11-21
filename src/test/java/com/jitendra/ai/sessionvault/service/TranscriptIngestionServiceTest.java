package com.jitendra.ai.sessionvault.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.domain.MessageRole;
import com.jitendra.ai.sessionvault.dto.TranscriptIngestRequest;
import com.jitendra.ai.sessionvault.dto.TranscriptIngestResult;
import com.jitendra.ai.sessionvault.exception.InvalidTranscriptException;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.repository.TagRepository;
import com.jitendra.ai.sessionvault.rag.RagIndexingTrigger;
import com.jitendra.ai.sessionvault.service.impl.TranscriptIngestionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranscriptIngestionServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private RagIndexingTrigger ragIndexingTrigger;

    @Mock
    private MemoryExtractionEngine memoryExtractionEngine;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TranscriptIngestionServiceImpl ingestionService;

    private UUID sessionId;
    private ChatSession existingSession;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        existingSession = ChatSession.builder()
                .id(sessionId)
                .externalSessionId("conv-trans-100")
                .title("Transcript Test Session")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("ingestTranscript successfully ingests Antigravity JSONL transcript")
    void ingestTranscript_JsonlContent_Success() {
        String jsonl = """
            {"step_index": 1, "source": "USER_EXPLICIT", "type": "USER_INPUT", "content": "How do I secure the vault?"}
            {"step_index": 2, "source": "MODEL", "type": "PLANNER_RESPONSE", "thinking": "We should use an API Key filter.", "content": "Implement ApiKeyAuthenticationFilter."}
            """;

        TranscriptIngestRequest request = TranscriptIngestRequest.builder()
                .externalSessionId("conv-trans-100")
                .jsonlContent(jsonl)
                .title("Security Consultation")
                .build();

        when(sessionRepository.findByExternalSessionId("conv-trans-100")).thenReturn(Optional.of(existingSession));
        when(sessionRepository.save(any(ChatSession.class))).thenReturn(existingSession);
        when(messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId)).thenReturn(Collections.emptyList());
        when(messageRepository.countBySessionId(sessionId)).thenReturn(2);

        TranscriptIngestResult result = ingestionService.ingestTranscript(request);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessagesIngested()).isEqualTo(2);
        assertThat(result.getDuplicatesSkipped()).isEqualTo(0);
        verify(messageRepository).saveAll(any());
        verify(ragIndexingTrigger).requestIndex(sessionId);
    }

    @Test
    @DisplayName("ingestTranscript handles DUPLICATE ingestion idempotently without duplicate rows")
    void ingestTranscript_DuplicateIngestion_SkipsDuplicates() {
        String jsonl = """
            {"step_index": 1, "source": "USER_EXPLICIT", "type": "USER_INPUT", "content": "First Question"}
            {"step_index": 2, "source": "MODEL", "type": "PLANNER_RESPONSE", "content": "First Answer"}
            """;

        TranscriptIngestRequest request = TranscriptIngestRequest.builder()
                .externalSessionId("conv-trans-100")
                .jsonlContent(jsonl)
                .build();

        ChatMessage existingMsg1 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(existingSession)
                .sequenceNumber(1)
                .role(MessageRole.USER)
                .content("First Question")
                .build();

        ChatMessage existingMsg2 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(existingSession)
                .sequenceNumber(2)
                .role(MessageRole.MODEL)
                .content("First Answer")
                .build();

        when(sessionRepository.findByExternalSessionId("conv-trans-100")).thenReturn(Optional.of(existingSession));
        when(sessionRepository.save(any(ChatSession.class))).thenReturn(existingSession);
        when(messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId))
                .thenReturn(List.of(existingMsg1, existingMsg2));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(2);

        TranscriptIngestResult result = ingestionService.ingestTranscript(request);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessagesIngested()).isEqualTo(0);
        assertThat(result.getDuplicatesSkipped()).isEqualTo(2);
        assertThat(result.getTotalMessages()).isEqualTo(2);

        // Verify saveAll was NOT called since all messages were duplicates
        verify(messageRepository, never()).saveAll(any());
        verifyNoInteractions(ragIndexingTrigger);
    }

    @Test
    @DisplayName("ingestTranscript throws InvalidTranscriptException when no content is provided")
    void ingestTranscript_NoContent_ThrowsException() {
        TranscriptIngestRequest request = TranscriptIngestRequest.builder().build();

        assertThatThrownBy(() -> ingestionService.ingestTranscript(request))
                .isInstanceOf(InvalidTranscriptException.class)
                .hasMessageContaining("Either 'filePath' or 'jsonlContent' must be provided");
    }

    @Test
    @DisplayName("ingestTranscript rejects path traversal with illegal null byte characters")
    void ingestTranscript_NullBytePath_ThrowsException() {
        TranscriptIngestRequest request = TranscriptIngestRequest.builder()
                .filePath("safe/path\0/etc/passwd")
                .build();

        assertThatThrownBy(() -> ingestionService.ingestTranscript(request))
                .isInstanceOf(InvalidTranscriptException.class)
                .hasMessageContaining("Illegal character");
    }
}
