package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.dto.ChatMessageDto;
import com.jitendra.ai.sessionvault.dto.SaveMessageRequest;

import java.util.List;
import java.util.UUID;

public interface MessageService {
    ChatMessageDto addMessage(UUID sessionId, SaveMessageRequest request);
    List<ChatMessageDto> addMessagesBatch(UUID sessionId, List<SaveMessageRequest> requests);
    List<ChatMessageDto> getMessagesBySessionId(UUID sessionId);
}
