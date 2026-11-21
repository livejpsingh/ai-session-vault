package com.jitendra.ai.sessionvault.mcp;

import com.jitendra.ai.sessionvault.domain.MessageRole;
import com.jitendra.ai.sessionvault.dto.*;
import com.jitendra.ai.sessionvault.service.ExportService;
import com.jitendra.ai.sessionvault.service.MessageService;
import com.jitendra.ai.sessionvault.service.SearchService;
import com.jitendra.ai.sessionvault.service.SessionService;
import com.jitendra.ai.sessionvault.service.TranscriptIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionVaultMcpToolsTest {

    @Mock
    private SearchService searchService;

    @Mock
    private SessionService sessionService;

    @Mock
    private MessageService messageService;

    @Mock
    private TranscriptIngestionService ingestionService;

    @Mock
    private ExportService exportService;

    @InjectMocks
    private SessionVaultMcpTools mcpTools;

    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
    }

    @Test
    @DisplayName("searchMessages MCP tool delegates to SearchService")
    void searchMessages_DelegatesCorrectly() {
        Page<SearchResultDto> page = new PageImpl<>(Collections.emptyList());
        when(searchService.search(any())).thenReturn(page);

        Page<SearchResultDto> result = mcpTools.searchMessages("vector", "project-x", 0, 10);

        assertThat(result).isNotNull();
        verify(searchService).search(any(SearchQueryRequest.class));
    }

    @Test
    @DisplayName("getSession MCP tool retrieves by UUID")
    void getSession_ByUuid() {
        ChatSessionDto dto = ChatSessionDto.builder().id(testId).title("Session").build();
        when(sessionService.getSessionById(testId, true)).thenReturn(dto);

        ChatSessionDto result = mcpTools.getSession(testId.toString(), true);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testId);
    }

    @Test
    @DisplayName("getSession MCP tool retrieves by externalId when not UUID")
    void getSession_ByExternalId() {
        ChatSessionDto dto = ChatSessionDto.builder().externalSessionId("conv-ext-9").title("Session").build();
        when(sessionService.getSessionByExternalId("conv-ext-9", false)).thenReturn(dto);

        ChatSessionDto result = mcpTools.getSession("conv-ext-9", false);

        assertThat(result).isNotNull();
        assertThat(result.getExternalSessionId()).isEqualTo("conv-ext-9");
    }

    @Test
    @DisplayName("ingestTranscript MCP tool delegates to TranscriptIngestionService")
    void ingestTranscript_DelegatesCorrectly() {
        TranscriptIngestResult res = TranscriptIngestResult.builder().success(true).messagesIngested(5).build();
        when(ingestionService.ingestTranscript(any())).thenReturn(res);

        TranscriptIngestResult result = mcpTools.ingestTranscript("transcript.jsonl", null, "conv-1", "Title", "Proj");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessagesIngested()).isEqualTo(5);
        verify(ingestionService).ingestTranscript(any(TranscriptIngestRequest.class));
    }

    @Test
    @DisplayName("exportSession MCP tool delegates to ExportService")
    void exportSession_DelegatesCorrectly() {
        when(exportService.exportSession(testId, ExportFormat.MARKDOWN)).thenReturn("# Markdown Export");

        String exported = mcpTools.exportSession(testId.toString(), "MARKDOWN");

        assertThat(exported).isEqualTo("# Markdown Export");
    }

    @Test
    @DisplayName("saveMessage MCP tool delegates to MessageService")
    void saveMessage_DelegatesCorrectly() {
        ChatMessageDto dto = ChatMessageDto.builder().id(UUID.randomUUID()).content("Note").build();
        when(messageService.addMessage(eq(testId), any())).thenReturn(dto);

        ChatMessageDto result = mcpTools.saveMessage(testId.toString(), "USER", "Note", null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Note");
    }
}
