package com.jitendra.ai.sessionvault.dto;

import com.jitendra.ai.sessionvault.domain.MessageRole;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResultDto {
    private UUID sessionId;
    private String externalSessionId;
    private String sessionTitle;
    private String projectName;
    private UUID messageId;
    private int sequenceNumber;
    private MessageRole role;
    private String matchedSnippet;
    private String thinkingSnippet;
    private OffsetDateTime messageCreatedAt;
}
