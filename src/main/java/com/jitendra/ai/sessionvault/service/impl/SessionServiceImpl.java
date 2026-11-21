package com.jitendra.ai.sessionvault.service.impl;

import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.domain.SessionStatus;
import com.jitendra.ai.sessionvault.domain.Tag;
import com.jitendra.ai.sessionvault.dto.ChatSessionDto;
import com.jitendra.ai.sessionvault.dto.CreateSessionRequest;
import com.jitendra.ai.sessionvault.exception.DuplicateResourceException;
import com.jitendra.ai.sessionvault.exception.ResourceNotFoundException;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.repository.TagRepository;
import com.jitendra.ai.sessionvault.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SessionServiceImpl implements SessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final TagRepository tagRepository;

    @Override
    public ChatSessionDto createSession(CreateSessionRequest request) {
        log.info("Creating chat session with title='{}', externalSessionId='{}'", request.getTitle(), request.getExternalSessionId());

        if (request.getExternalSessionId() != null && !request.getExternalSessionId().isBlank()) {
            sessionRepository.findByExternalSessionId(request.getExternalSessionId().trim())
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("Session with externalSessionId '" + request.getExternalSessionId() + "' already exists");
                    });
        }

        ChatSession session = ChatSession.builder()
                .externalSessionId(request.getExternalSessionId() != null ? request.getExternalSessionId().trim() : null)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .provider(request.getProvider())
                .modelName(request.getModelName())
                .projectName(request.getProjectName())
                .workspacePath(request.getWorkspacePath())
                .status(SessionStatus.ACTIVE)
                .metadata(request.getMetadata())
                .tags(resolveTags(request.getTags()))
                .build();

        ChatSession saved = sessionRepository.save(session);
        return ChatSessionDto.fromEntity(saved, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionDto getSessionById(UUID id, boolean includeMessages) {
        ChatSession session;
        if (includeMessages) {
            session = sessionRepository.findByIdWithMessages(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + id));
        } else {
            session = sessionRepository.findByIdWithTags(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + id));
        }
        ChatSessionDto dto = ChatSessionDto.fromEntity(session, includeMessages);
        if (!includeMessages) {
            dto.setMessageCount(messageRepository.countBySessionId(id));
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionDto getSessionByExternalId(String externalId, boolean includeMessages) {
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("External session ID cannot be blank");
        }
        ChatSession session = sessionRepository.findByExternalSessionId(externalId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with externalSessionId: " + externalId));
        ChatSessionDto dto = ChatSessionDto.fromEntity(session, includeMessages);
        if (!includeMessages) {
            dto.setMessageCount(messageRepository.countBySessionId(session.getId()));
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatSessionDto> listSessions(Pageable pageable, String projectName, String provider, String tagName) {
        Page<ChatSession> page;

        if (tagName != null && !tagName.isBlank()) {
            page = sessionRepository.findByTagName(tagName.trim(), pageable);
        } else if (projectName != null && !projectName.isBlank()) {
            page = sessionRepository.findByProjectNameIgnoreCase(projectName.trim(), pageable);
        } else if (provider != null && !provider.isBlank()) {
            page = sessionRepository.findByProviderIgnoreCase(provider.trim(), pageable);
        } else {
            page = sessionRepository.findAll(pageable);
        }

        return page.map(s -> ChatSessionDto.fromEntity(s, false));
    }

    @Override
    public void deleteSession(UUID id) {
        log.info("Deleting chat session: {}", id);
        if (!sessionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Chat session not found with ID: " + id);
        }
        sessionRepository.deleteById(id);
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
