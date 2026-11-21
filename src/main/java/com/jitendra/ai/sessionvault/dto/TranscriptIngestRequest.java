package com.jitendra.ai.sessionvault.dto;

import lombok.*;

import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranscriptIngestRequest {

    /**
     * Absolute or relative path to a local transcript file (e.g. transcript.jsonl).
     */
    private String filePath;

    /**
     * Optional raw JSON or JSONL content string if uploading directly.
     */
    private String jsonlContent;

    /**
     * External session identifier (e.g., conversation ID).
     */
    private String externalSessionId;

    /**
     * Optional custom title for the session.
     */
    private String title;

    private String projectName;
    private String workspacePath;
    private String provider;
    private String modelName;
    private Set<String> tags;
    private Map<String, Object> metadata;
}
