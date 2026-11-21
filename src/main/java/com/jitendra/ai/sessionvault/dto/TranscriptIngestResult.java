package com.jitendra.ai.sessionvault.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranscriptIngestResult {
    private UUID sessionId;
    private String externalSessionId;
    private String title;
    private int messagesIngested;
    private int duplicatesSkipped;
    private int totalMessages;
    private boolean success;
    private String message;
}
