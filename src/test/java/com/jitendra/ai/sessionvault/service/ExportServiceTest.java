package com.jitendra.ai.sessionvault.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.domain.MessageRole;
import com.jitendra.ai.sessionvault.domain.Tag;
import com.jitendra.ai.sessionvault.dto.ExportFormat;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.service.impl.ExportServiceImpl;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ExportServiceImpl exportService;

    private UUID sessionId;
    private ChatSession session;
    private ChatMessage message1;
    private ChatMessage message2;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        session = ChatSession.builder()
                .id(sessionId)
                .externalSessionId("ext-exp-1")
                .title("Export Demo Session")
                .provider("google-antigravity")
                .modelName("gemini-2.5-pro")
                .projectName("DemoProject")
                .tags(Set.of(new Tag("demo"), new Tag("export")))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        message1 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sequenceNumber(1)
                .role(MessageRole.USER)
                .content("Show me an export")
                .createdAt(OffsetDateTime.now())
                .build();

        message2 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sequenceNumber(2)
                .role(MessageRole.MODEL)
                .content("Here is your formatted export.")
                .thinking("Preparing markdown output format.")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("exportSession generates clean formatted Markdown with headers, roles, and thinking process")
    void exportSession_Markdown_Success() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId))
                .thenReturn(List.of(message1, message2));

        String markdown = exportService.exportSession(sessionId, ExportFormat.MARKDOWN);

        assertThat(markdown).isNotBlank();
        assertThat(markdown).contains("# Export Demo Session");
        assertThat(markdown).contains("**External ID:** `ext-exp-1`");
        assertThat(markdown).contains("👤 **User**");
        assertThat(markdown).contains("🤖 **Assistant**");
        assertThat(markdown).contains("<summary>🧠 Thinking Process</summary>");
        assertThat(markdown).contains("Preparing markdown output format.");
    }

    @Test
    @DisplayName("exportSession generates valid JSON with full session and message details")
    void exportSession_Json_Success() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId))
                .thenReturn(List.of(message1, message2));

        String json = exportService.exportSession(sessionId, ExportFormat.JSON);

        assertThat(json).isNotBlank();
        assertThat(json).contains("\"title\" : \"Export Demo Session\"");
        assertThat(json).contains("\"externalSessionId\" : \"ext-exp-1\"");
        assertThat(json).contains("\"messageCount\" : 2");
        assertThat(json).contains("\"content\" : \"Show me an export\"");
    }
}
