package com.jitendra.ai.sessionvault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSessionRequest {

    private String externalSessionId;

    @NotBlank(message = "Session title cannot be blank")
    @Size(max = 500, message = "Session title cannot exceed 500 characters")
    private String title;

    private String description;
    private String provider;
    private String modelName;
    private String projectName;
    private String workspacePath;
    private Set<String> tags;
    private Map<String, Object> metadata;
}
