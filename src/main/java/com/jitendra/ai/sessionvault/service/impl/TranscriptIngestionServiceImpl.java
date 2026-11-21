package com.jitendra.ai.sessionvault.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.domain.MessageRole;
import com.jitendra.ai.sessionvault.domain.SessionStatus;
import com.jitendra.ai.sessionvault.domain.Tag;
import com.jitendra.ai.sessionvault.dto.TranscriptIngestRequest;
import com.jitendra.ai.sessionvault.dto.TranscriptIngestResult;
import com.jitendra.ai.sessionvault.exception.InvalidTranscriptException;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.repository.TagRepository;
import com.jitendra.ai.sessionvault.service.TranscriptIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TranscriptIngestionServiceImpl implements TranscriptIngestionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final TagRepository tagRepository;
    private final ObjectMapper objectMapper;

    @Override
    public TranscriptIngestResult ingestTranscript(TranscriptIngestRequest request) {
        log.info("Starting transcript ingestion for externalSessionId='{}', filePath='{}'",
                request.getExternalSessionId(), request.getFilePath());

        List<String> lines = extractLines(request);
        if (lines.isEmpty()) {
            throw new InvalidTranscriptException("No content found in transcript source");
        }

        ChatSession session = resolveOrCreateSession(request, lines);

        // Deduplication handling against already existing messages in this session
        List<ChatMessage> existingMessages = messageRepository.findBySessionIdOrderBySequenceNumberAsc(session.getId());
        Set<String> existingSignatures = new HashSet<>();
        Set<Integer> existingSequences = new HashSet<>();

        for (ChatMessage em : existingMessages) {
            existingSequences.add(em.getSequenceNumber());
            existingSignatures.add(computeSignature(em.getRole(), em.getContent(), em.getToolCallId()));
        }

        List<ChatMessage> toSave = new ArrayList<>();
        int duplicatesSkipped = 0;
        int nextSeq = existingSequences.isEmpty() ? 1 : Collections.max(existingSequences) + 1;

        for (String line : lines) {
            try {
                JsonNode root = objectMapper.readTree(line);
                List<JsonNode> items = new ArrayList<>();
                if (root.isArray()) {
                    for (JsonNode item : root) items.add(item);
                } else if (root.isObject()) {
                    items.add(root);
                }

                for (JsonNode item : items) {
                    ParsedMessageData parsed = parseJsonNode(item);
                    if (parsed == null) {
                        continue;
                    }

                    String signature = computeSignature(parsed.role(), parsed.content(), parsed.toolCallId());

                    // Check if identical message already ingested
                    if (existingSignatures.contains(signature)) {
                        duplicatesSkipped++;
                        continue;
                    }

                    int assignedSeq;
                    if (parsed.stepIndex() != null && !existingSequences.contains(parsed.stepIndex())) {
                        assignedSeq = parsed.stepIndex();
                    } else {
                        assignedSeq = nextSeq++;
                    }

                    ChatMessage msg = ChatMessage.builder()
                            .session(session)
                            .sequenceNumber(assignedSeq)
                            .role(parsed.role())
                            .content(parsed.content())
                            .thinking(parsed.thinking())
                            .toolCalls(parsed.toolCalls())
                            .toolCallId(parsed.toolCallId())
                            .tokensUsed(parsed.tokensUsed())
                            .createdAt(parsed.createdAt() != null ? parsed.createdAt() : OffsetDateTime.now())
                            .build();

                    toSave.add(msg);
                    existingSignatures.add(signature);
                    existingSequences.add(assignedSeq);
                }
            } catch (Exception e) {
                log.warn("Skipping unparseable transcript line: {} (Error: {})", line, e.getMessage());
            }
        }

        if (!toSave.isEmpty()) {
            messageRepository.saveAll(toSave);
        }

        int totalCount = messageRepository.countBySessionId(session.getId());
        log.info("Ingested {} new messages (skipped {} duplicates) for session {}",
                toSave.size(), duplicatesSkipped, session.getId());

        return TranscriptIngestResult.builder()
                .sessionId(session.getId())
                .externalSessionId(session.getExternalSessionId())
                .title(session.getTitle())
                .messagesIngested(toSave.size())
                .duplicatesSkipped(duplicatesSkipped)
                .totalMessages(totalCount)
                .success(true)
                .message(String.format("Ingestion complete: %d new messages saved, %d duplicates skipped.",
                        toSave.size(), duplicatesSkipped))
                .build();
    }

    private String computeSignature(MessageRole role, String content, String toolCallId) {
        String safeRole = role != null ? role.name() : "";
        String safeContent = content != null ? content.trim() : "";
        String safeTool = toolCallId != null ? toolCallId.trim() : "";
        return safeRole + "::" + safeTool + "::" + (safeContent.length() > 500 ? safeContent.substring(0, 500) : safeContent);
    }

    private List<String> extractLines(TranscriptIngestRequest request) {
        try {
            if (request.getJsonlContent() != null && !request.getJsonlContent().isBlank()) {
                List<String> list = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new StringReader(request.getJsonlContent()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            list.add(trimmed);
                        }
                    }
                }
                return list;
            }

            if (request.getFilePath() != null && !request.getFilePath().isBlank()) {
                String rawPath = request.getFilePath().trim();

                // Path validation against null byte injection
                if (rawPath.contains("\0")) {
                    throw new InvalidTranscriptException("Illegal character in file path");
                }

                Path path = Path.of(rawPath).normalize().toAbsolutePath();

                if (!Files.exists(path)) {
                    throw new InvalidTranscriptException("Transcript file does not exist at path: " + rawPath);
                }
                if (!Files.isRegularFile(path)) {
                    throw new InvalidTranscriptException("Specified path is not a regular file: " + rawPath);
                }
                if (!Files.isReadable(path)) {
                    throw new InvalidTranscriptException("Cannot read transcript file: " + rawPath);
                }

                return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }

            throw new InvalidTranscriptException("Either 'filePath' or 'jsonlContent' must be provided for ingestion");
        } catch (InvalidTranscriptException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTranscriptException("Failed to read transcript input: " + e.getMessage(), e);
        }
    }

    private ChatSession resolveOrCreateSession(TranscriptIngestRequest request, List<String> lines) {
        String externalId = request.getExternalSessionId();
        if (externalId == null || externalId.isBlank()) {
            externalId = extractExternalIdFromLines(lines);
            if (externalId == null || externalId.isBlank()) {
                externalId = UUID.randomUUID().toString();
            }
        }

        final String finalExternalId = externalId;
        Optional<ChatSession> existing = sessionRepository.findByExternalSessionId(finalExternalId);
        if (existing.isPresent()) {
            log.info("Found existing session for externalSessionId='{}', updating metadata", finalExternalId);
            ChatSession s = existing.get();
            if (request.getTitle() != null && !request.getTitle().isBlank()) {
                s.setTitle(request.getTitle());
            }
            if (request.getProjectName() != null) {
                s.setProjectName(request.getProjectName());
            }
            if (request.getWorkspacePath() != null) {
                s.setWorkspacePath(request.getWorkspacePath());
            }
            if (request.getTags() != null) {
                s.getTags().addAll(resolveTags(request.getTags()));
            }
            return sessionRepository.save(s);
        }

        String sessionTitle = request.getTitle();
        if (sessionTitle == null || sessionTitle.isBlank()) {
            sessionTitle = "Session " + finalExternalId;
        }

        ChatSession newSession = ChatSession.builder()
                .externalSessionId(finalExternalId)
                .title(sessionTitle)
                .provider(request.getProvider() != null ? request.getProvider() : "google-antigravity")
                .modelName(request.getModelName() != null ? request.getModelName() : "gemini")
                .projectName(request.getProjectName())
                .workspacePath(request.getWorkspacePath())
                .status(SessionStatus.ACTIVE)
                .metadata(request.getMetadata())
                .tags(resolveTags(request.getTags()))
                .build();

        return sessionRepository.save(newSession);
    }

    private String extractExternalIdFromLines(List<String> lines) {
        for (String line : lines) {
            try {
                JsonNode node = objectMapper.readTree(line);
                if (node.hasNonNull("conversation_id")) {
                    return node.get("conversation_id").asText();
                }
                if (node.hasNonNull("conversationId")) {
                    return node.get("conversationId").asText();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private record ParsedMessageData(
            Integer stepIndex,
            MessageRole role,
            String content,
            String thinking,
            Object toolCalls,
            String toolCallId,
            Integer tokensUsed,
            OffsetDateTime createdAt
    ) {}

    private ParsedMessageData parseJsonNode(JsonNode node) {
        String type = node.hasNonNull("type") ? node.get("type").asText() : "";
        String roleStr = node.hasNonNull("role") ? node.get("role").asText() : type;

        MessageRole role = MessageRole.fromString(roleStr);

        String content = "";
        if (node.hasNonNull("content")) {
            content = node.get("content").asText();
        } else if (node.hasNonNull("text")) {
            content = node.get("text").asText();
        } else if (node.hasNonNull("message")) {
            content = node.get("message").asText();
        }

        if (content.isBlank() && !node.hasNonNull("tool_calls") && !node.hasNonNull("thinking")) {
            return null;
        }

        String thinking = node.hasNonNull("thinking") ? node.get("thinking").asText() : null;

        Object toolCalls = null;
        if (node.hasNonNull("tool_calls")) {
            toolCalls = objectMapper.convertValue(node.get("tool_calls"), Object.class);
        }

        String toolCallId = node.hasNonNull("tool_call_id") ? node.get("tool_call_id").asText() : null;
        Integer tokens = node.hasNonNull("tokens_used") ? node.get("tokens_used").asInt() : null;

        Integer stepIndex = null;
        if (node.hasNonNull("step_index")) {
            stepIndex = node.get("step_index").asInt();
        }

        OffsetDateTime createdAt = null;
        if (node.hasNonNull("created_at")) {
            try {
                createdAt = OffsetDateTime.parse(node.get("created_at").asText(), DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception ignored) {
            }
        }

        return new ParsedMessageData(
                stepIndex,
                role,
                content.isBlank() ? "[Tool Invocation or Reasoning Trace]" : content,
                thinking,
                toolCalls,
                toolCallId,
                tokens,
                createdAt
        );
    }

    private Set<Tag> resolveTags(Set<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return new HashSet<>();
        }
        Set<Tag> tags = new HashSet<>();
        for (String rawName : tagNames) {
            String name = rawName.trim().toLowerCase();
            if (!name.isEmpty()) {
                Tag tag = tagRepository.findByNameIgnoreCase(name)
                        .orElseGet(() -> tagRepository.save(new Tag(name)));
                tags.add(tag);
            }
        }
        return tags;
    }
}
