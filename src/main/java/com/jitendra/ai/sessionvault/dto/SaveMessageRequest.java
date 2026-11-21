package com.jitendra.ai.sessionvault.dto;

import com.jitendra.ai.sessionvault.domain.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveMessageRequest {

    private Integer sequenceNumber;

    @NotNull(message = "Message role is required")
    private MessageRole role;

    @NotBlank(message = "Message content cannot be blank")
    private String content;

    private String thinking;
    private Object toolCalls;
    private String toolCallId;
    private Integer tokensUsed;
}
