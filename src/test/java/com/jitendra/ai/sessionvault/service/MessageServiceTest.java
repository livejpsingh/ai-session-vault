package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.domain.MessageRole;
import com.jitendra.ai.sessionvault.dto.ChatMessageDto;
import com.jitendra.ai.sessionvault.dto.SaveMessageRequest;
import com.jitendra.ai.sessionvault.exception.ResourceNotFoundException;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @InjectMocks
    private MessageServiceImpl messageService;

    private UUID sessionId;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        session = ChatSession.builder().id(sessionId).title("Test Session").build();
    }

    @Test
    @DisplayName("addMessage successfully saves a message with auto sequence number")
    void addMessage_Success() {
        SaveMessageRequest request = SaveMessageRequest.builder()
                .role(MessageRole.USER)
                .content("Hello assistant")
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(0);
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(i -> {
            ChatMessage m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(OffsetDateTime.now());
            return m;
        });

        ChatMessageDto result = messageService.addMessage(sessionId, request);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hello assistant");
        assertThat(result.getRole()).isEqualTo(MessageRole.USER);
        assertThat(result.getSequenceNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("addMessage throws ResourceNotFoundException if session does not exist")
    void addMessage_SessionNotFound_ThrowsException() {
        SaveMessageRequest request = SaveMessageRequest.builder()
                .role(MessageRole.USER)
                .content("Hello")
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.addMessage(sessionId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("addMessagesBatch saves multiple messages in sequence")
    void addMessagesBatch_Success() {
        List<SaveMessageRequest> requests = List.of(
                SaveMessageRequest.builder().role(MessageRole.USER).content("Query 1").build(),
                SaveMessageRequest.builder().role(MessageRole.MODEL).content("Response 1").thinking("Reasoning 1").build()
        );

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(5);
        when(messageRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<ChatMessageDto> dtos = messageService.addMessagesBatch(sessionId, requests);

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getSequenceNumber()).isEqualTo(6);
        assertThat(dtos.get(1).getSequenceNumber()).isEqualTo(7);
        assertThat(dtos.get(1).getThinking()).isEqualTo("Reasoning 1");
    }

    @Test
    @DisplayName("getMessagesBySessionId returns ordered messages")
    void getMessagesBySessionId_Success() {
        ChatMessage msg1 = ChatMessage.builder().id(UUID.randomUUID()).session(session).sequenceNumber(1).role(MessageRole.USER).content("Q").build();
        ChatMessage msg2 = ChatMessage.builder().id(UUID.randomUUID()).session(session).sequenceNumber(2).role(MessageRole.MODEL).content("A").build();

        when(sessionRepository.existsById(sessionId)).thenReturn(true);
        when(messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId)).thenReturn(List.of(msg1, msg2));

        List<ChatMessageDto> messages = messageService.getMessagesBySessionId(sessionId);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("Q");
        assertThat(messages.get(1).getContent()).isEqualTo("A");
    }
}
