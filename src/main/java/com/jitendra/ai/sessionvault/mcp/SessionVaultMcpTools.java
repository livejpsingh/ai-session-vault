package com.jitendra.ai.sessionvault.mcp;

import com.jitendra.ai.sessionvault.domain.MessageRole;
import com.jitendra.ai.sessionvault.dto.*;
import com.jitendra.ai.sessionvault.service.ExportService;
import com.jitendra.ai.sessionvault.service.MessageService;
import com.jitendra.ai.sessionvault.service.SearchService;
import com.jitendra.ai.sessionvault.service.SessionService;
import com.jitendra.ai.sessionvault.service.TranscriptIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionVaultMcpTools {

    private final SearchService searchService;
    private final SessionService sessionService;
    private final MessageService messageService;
    private final TranscriptIngestionService ingestionService;
    private final ExportService exportService;

    @McpTool(
            name = "vault_search_messages",
            description = "Search across all AI conversation transcripts, assistant reasoning traces, and user messages using PostgreSQL full-text search."
    )
    public Page<SearchResultDto> searchMessages(
            @McpToolParam(description = "Keywords or search phrase to find in conversation history", required = true) String query,
            @McpToolParam(description = "Optional project name filter", required = false) String projectName,
            @McpToolParam(description = "Page number (0-indexed)", required = false) Integer page,
            @McpToolParam(description = "Page size (max 100)", required = false) Integer size) {

        log.info("MCP Tool [vault_search_messages] invoked for query='{}'", query);
        SearchQueryRequest request = SearchQueryRequest.builder()
                .query(query)
                .projectName(projectName)
                .page(page != null ? page : 0)
                .size(size != null ? size : 20)
                .build();
        return searchService.search(request);
    }

    @McpTool(
            name = "vault_get_session",
            description = "Retrieve session metadata and conversation messages by external conversation ID or UUID."
    )
    public ChatSessionDto getSession(
            @McpToolParam(description = "The session ID or external conversation ID", required = true) String sessionIdOrExternalId,
            @McpToolParam(description = "Whether to include full conversation messages", required = false) Boolean includeMessages) {

        log.info("MCP Tool [vault_get_session] invoked for id='{}'", sessionIdOrExternalId);
        boolean include = includeMessages != null && includeMessages;
        try {
            UUID uuid = UUID.fromString(sessionIdOrExternalId);
            return sessionService.getSessionById(uuid, include);
        } catch (IllegalArgumentException e) {
            return sessionService.getSessionByExternalId(sessionIdOrExternalId, include);
        }
    }

    @McpTool(
            name = "vault_list_sessions",
            description = "List stored AI sessions with optional project or tag filters and pagination."
    )
    public Page<ChatSessionDto> listSessions(
            @McpToolParam(description = "Filter by project name", required = false) String projectName,
            @McpToolParam(description = "Filter by provider", required = false) String provider,
            @McpToolParam(description = "Filter by tag name", required = false) String tagName,
            @McpToolParam(description = "Page number (0-indexed)", required = false) Integer page,
            @McpToolParam(description = "Page size (max 100)", required = false) Integer size) {

        log.info("MCP Tool [vault_list_sessions] invoked with project='{}', tag='{}'", projectName, tagName);
        int p = page != null ? page : 0;
        int s = size != null ? Math.min(size, 100) : 20;
        return sessionService.listSessions(PageRequest.of(p, s), projectName, provider, tagName);
    }

    @McpTool(
            name = "vault_ingest_transcript",
            description = "Ingest an Antigravity or AI conversation transcript from a local JSONL file path or raw JSONL string."
    )
    public TranscriptIngestResult ingestTranscript(
            @McpToolParam(description = "Absolute or relative file path to the transcript (e.g. transcript.jsonl)", required = false) String filePath,
            @McpToolParam(description = "Raw JSON or JSONL transcript content string", required = false) String jsonlContent,
            @McpToolParam(description = "External conversation ID", required = false) String externalSessionId,
            @McpToolParam(description = "Custom title for the session", required = false) String title,
            @McpToolParam(description = "Project name", required = false) String projectName) {

        log.info("MCP Tool [vault_ingest_transcript] invoked for path='{}', externalId='{}'", filePath, externalSessionId);
        TranscriptIngestRequest request = TranscriptIngestRequest.builder()
                .filePath(filePath)
                .jsonlContent(jsonlContent)
                .externalSessionId(externalSessionId)
                .title(title)
                .projectName(projectName)
                .build();
        return ingestionService.ingestTranscript(request);
    }

    @McpTool(
            name = "vault_export_session",
            description = "Export a session and its conversation transcript as formatted Markdown or JSON."
    )
    public String exportSession(
            @McpToolParam(description = "The session ID or external conversation ID", required = true) String sessionIdOrExternalId,
            @McpToolParam(description = "Export format: MARKDOWN or JSON", required = false) String format) {

        log.info("MCP Tool [vault_export_session] invoked for id='{}', format='{}'", sessionIdOrExternalId, format);
        ExportFormat exportFormat = "JSON".equalsIgnoreCase(format) ? ExportFormat.JSON : ExportFormat.MARKDOWN;
        try {
            UUID uuid = UUID.fromString(sessionIdOrExternalId);
            return exportService.exportSession(uuid, exportFormat);
        } catch (IllegalArgumentException e) {
            return exportService.exportSessionByExternalId(sessionIdOrExternalId, exportFormat);
        }
    }

    @McpTool(
            name = "vault_save_message",
            description = "Append a new message, tool result, or user note to an existing session."
    )
    public ChatMessageDto saveMessage(
            @McpToolParam(description = "The session UUID", required = true) String sessionId,
            @McpToolParam(description = "Role: USER, MODEL, SYSTEM, TOOL", required = true) String role,
            @McpToolParam(description = "Message content text", required = true) String content,
            @McpToolParam(description = "Optional thinking process text", required = false) String thinking) {

        log.info("MCP Tool [vault_save_message] invoked for session={}", sessionId);
        UUID uuid = UUID.fromString(sessionId);
        SaveMessageRequest request = SaveMessageRequest.builder()
                .role(MessageRole.fromString(role))
                .content(content)
                .thinking(thinking)
                .build();
        return messageService.addMessage(uuid, request);
    }
}
