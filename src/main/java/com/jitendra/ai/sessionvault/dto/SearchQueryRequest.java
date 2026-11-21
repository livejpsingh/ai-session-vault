package com.jitendra.ai.sessionvault.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchQueryRequest {

    @NotBlank(message = "Search query cannot be blank")
    private String query;

    private String projectName;
    private String provider;
    private String modelName;
    private String tagName;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;
}
