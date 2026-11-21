package com.jitendra.ai.sessionvault.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.domain.Tag;
import com.jitendra.ai.sessionvault.dto.ChatMessageDto;
import com.jitendra.ai.sessionvault.dto.ChatSessionDto;
import com.jitendra.ai.sessionvault.dto.ExportFormat;
import com.jitendra.ai.sessionvault.exception.ResourceNotFoundException;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExportServiceImpl implements ExportService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String exportSession(UUID sessionId, ExportFormat format) {
        log.info("Exporting session id={} format={}", sessionId, format);
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
        return formatSession(session, messages, format);
    }

    @Override
    public String exportSessionByExternalId(String externalId, ExportFormat format) {
        log.info("Exporting session externalId={} format={}", externalId, format);
        ChatSession session = sessionRepository.findByExternalSessionId(externalId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with externalSessionId: " + externalId));
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderBySequenceNumberAsc(session.getId());
        return formatSession(session, messages, format);
    }

    private String formatSession(ChatSession session, List<ChatMessage> messages, ExportFormat format) {
        if (format == ExportFormat.JSON) {
            return exportToJson(session, messages);
        } else {
            return exportToMarkdown(session, messages);
        }
    }

    private String exportToJson(ChatSession session, List<ChatMessage> messages) {
        ChatSessionDto dto = ChatSessionDto.fromEntity(session, false);
        dto.setMessageCount(messages.size());
        dto.setMessages(messages.stream().map(ChatMessageDto::fromEntity).toList());

        try {
            ObjectMapper mapper = objectMapper.copy()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize session to JSON: " + e.getMessage(), e);
        }
    }

    private String exportToMarkdown(ChatSession session, List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dtf = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        sb.append("# ").append(session.getTitle()).append("\n\n");
        sb.append("**Session ID:** `").append(session.getId()).append("`  \n");
        if (session.getExternalSessionId() != null) {
            sb.append("**External ID:** `").append(session.getExternalSessionId()).append("`  \n");
        }
        sb.append("**Status:** `").append(session.getStatus()).append("`  \n");
        if (session.getProvider() != null) {
            sb.append("**Provider:** `").append(session.getProvider()).append("`  \n");
        }
        if (session.getModelName() != null) {
            sb.append("**Model:** `").append(session.getModelName()).append("`  \n");
        }
        if (session.getProjectName() != null) {
            sb.append("**Project:** ").append(session.getProjectName()).append("  \n");
        }
        if (session.getWorkspacePath() != null) {
            sb.append("**Workspace:** `").append(session.getWorkspacePath()).append("`  \n");
        }
        if (session.getCreatedAt() != null) {
            sb.append("**Created:** ").append(session.getCreatedAt().format(dtf)).append("  \n");
        }
        if (session.getTags() != null && !session.getTags().isEmpty()) {
            String tagList = session.getTags().stream().map(Tag::getName).sorted().collect(Collectors.joining(", "));
            sb.append("**Tags:** ").append(tagList).append("  \n");
        }
        if (session.getDescription() != null && !session.getDescription().isBlank()) {
            sb.append("\n## Description\n\n").append(session.getDescription()).append("\n");
        }

        sb.append("\n---\n\n## Conversation History (").append(messages.size()).append(" messages)\n\n");

        for (ChatMessage msg : messages) {
            String roleBadge = switch (msg.getRole()) {
                case USER -> "👤 **User**";
                case MODEL -> "🤖 **Assistant**";
                case SYSTEM -> "⚙️ **System**";
                case TOOL -> "🛠️ **Tool Result**";
            };

            sb.append("### #").append(msg.getSequenceNumber()).append(" ").append(roleBadge);
            if (msg.getCreatedAt() != null) {
                sb.append(" *(at ").append(msg.getCreatedAt().format(dtf)).append(")*");
            }
            sb.append("\n\n");

            if (msg.getThinking() != null && !msg.getThinking().isBlank()) {
                sb.append("<details>\n<summary>🧠 Thinking Process</summary>\n\n");
                sb.append(msg.getThinking().trim());
                sb.append("\n\n</details>\n\n");
            }

            if (msg.getContent() != null && !msg.getContent().isBlank()) {
                sb.append(msg.getContent().trim()).append("\n\n");
            }

            if (msg.getToolCalls() != null) {
                sb.append("```json\n// Tool Calls\n");
                try {
                    sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(msg.getToolCalls()));
                } catch (Exception e) {
                    sb.append(msg.getToolCalls());
                }
                sb.append("\n```\n\n");
            }

            if (msg.getToolCallId() != null) {
                sb.append("**Tool Call ID:** `").append(msg.getToolCallId()).append("`  \n");
            }
            if (msg.getTokensUsed() != null) {
                sb.append("**Tokens:** `").append(msg.getTokensUsed()).append("`  \n");
            }

            sb.append("\n---\n\n");
        }

        return sb.toString();
    }
}
