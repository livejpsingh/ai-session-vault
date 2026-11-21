package com.jitendra.ai.sessionvault.dto;

import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.domain.SessionStatus;
import com.jitendra.ai.sessionvault.domain.Tag;
import lombok.*;
import org.hibernate.Hibernate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionDto {
    private UUID id;
    private String externalSessionId;
    private String title;
    private String description;
    private String provider;
    private String modelName;
    private String projectName;
    private String workspacePath;
    private SessionStatus status;
    private Map<String, Object> metadata;
    private Set<String> tags;
    private int messageCount;
    private List<ChatMessageDto> messages;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ChatSessionDto fromEntity(ChatSession session, boolean includeMessages) {
        if (session == null) return null;

        Set<String> tagNames = Collections.emptySet();
        if (session.getTags() != null && Hibernate.isInitialized(session.getTags())) {
            tagNames = session.getTags().stream().map(Tag::getName).collect(Collectors.toSet());
        }

        List<ChatMessageDto> messageDtos = Collections.emptyList();
        int count = 0;

        if (includeMessages) {
            if (session.getMessages() != null) {
                count = session.getMessages().size();
                messageDtos = session.getMessages().stream()
                        .map(ChatMessageDto::fromEntity)
                        .toList();
            }
        } else if (session.getMessages() != null && Hibernate.isInitialized(session.getMessages())) {
            count = session.getMessages().size();
        }

        return ChatSessionDto.builder()
                .id(session.getId())
                .externalSessionId(session.getExternalSessionId())
                .title(session.getTitle())
                .description(session.getDescription())
                .provider(session.getProvider())
                .modelName(session.getModelName())
                .projectName(session.getProjectName())
                .workspacePath(session.getWorkspacePath())
                .status(session.getStatus())
                .metadata(session.getMetadata())
                .tags(tagNames)
                .messageCount(count)
                .messages(includeMessages ? messageDtos : null)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
