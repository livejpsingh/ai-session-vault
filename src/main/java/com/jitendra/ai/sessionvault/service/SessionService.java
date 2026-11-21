package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.dto.ChatSessionDto;
import com.jitendra.ai.sessionvault.dto.CreateSessionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SessionService {
    ChatSessionDto createSession(CreateSessionRequest request);
    ChatSessionDto getSessionById(UUID id, boolean includeMessages);
    ChatSessionDto getSessionByExternalId(String externalId, boolean includeMessages);
    Page<ChatSessionDto> listSessions(Pageable pageable, String projectName, String provider, String tagName);
    void deleteSession(UUID id);
}
