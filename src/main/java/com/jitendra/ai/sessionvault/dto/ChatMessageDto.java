package com.jitendra.ai.sessionvault.dto;

import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.MessageRole;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {
    private UUID id;
    private UUID sessionId;
    private int sequenceNumber;
    private MessageRole role;
    private String content;
    private String thinking;
    private Object toolCalls;
    private String toolCallId;
    private Integer tokensUsed;
    private OffsetDateTime createdAt;

    public static ChatMessageDto fromEntity(ChatMessage message) {
        if (message == null) return null;
        return ChatMessageDto.builder()
                .id(message.getId())
                .sessionId(message.getSession() != null ? message.getSession().getId() : null)
                .sequenceNumber(message.getSequenceNumber())
                .role(message.getRole())
                .content(message.getContent())
                .thinking(message.getThinking())
                .toolCalls(message.getToolCalls())
                .toolCallId(message.getToolCallId())
                .tokensUsed(message.getTokensUsed())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
