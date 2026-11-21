package com.jitendra.ai.sessionvault.service.impl;

import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.dto.ChatMessageDto;
import com.jitendra.ai.sessionvault.dto.SaveMessageRequest;
import com.jitendra.ai.sessionvault.exception.ResourceNotFoundException;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.rag.RagIndexingTrigger;
import com.jitendra.ai.sessionvault.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessageServiceImpl implements MessageService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final RagIndexingTrigger ragIndexingTrigger;

    @Override
    public ChatMessageDto addMessage(UUID sessionId, SaveMessageRequest request) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));

        int nextSeq = request.getSequenceNumber() != null
                ? request.getSequenceNumber()
                : messageRepository.countBySessionId(sessionId) + 1;

        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sequenceNumber(nextSeq)
                .role(request.getRole())
                .content(request.getContent())
                .thinking(request.getThinking())
                .toolCalls(request.getToolCalls())
                .toolCallId(request.getToolCallId())
                .tokensUsed(request.getTokensUsed())
                .build();

        ChatMessage saved = messageRepository.save(message);
        ragIndexingTrigger.requestIndex(sessionId);
        return ChatMessageDto.fromEntity(saved);
    }

    @Override
    public List<ChatMessageDto> addMessagesBatch(UUID sessionId, List<SaveMessageRequest> requests) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found with ID: " + sessionId));

        int currentCount = messageRepository.countBySessionId(sessionId);
        List<ChatMessage> entities = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            SaveMessageRequest req = requests.get(i);
            int seq = req.getSequenceNumber() != null ? req.getSequenceNumber() : currentCount + i + 1;

            ChatMessage msg = ChatMessage.builder()
                    .session(session)
                    .sequenceNumber(seq)
                    .role(req.getRole())
                    .content(req.getContent())
                    .thinking(req.getThinking())
                    .toolCalls(req.getToolCalls())
                    .toolCallId(req.getToolCallId())
                    .tokensUsed(req.getTokensUsed())
                    .build();
            entities.add(msg);
        }

        List<ChatMessage> saved = messageRepository.saveAll(entities);
        if (!saved.isEmpty()) {
            ragIndexingTrigger.requestIndex(sessionId);
        }
        return saved.stream().map(ChatMessageDto::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessagesBySessionId(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Chat session not found with ID: " + sessionId);
        }
        return messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId)
                .stream()
                .map(ChatMessageDto::fromEntity)
                .toList();
    }
}
