package com.jitendra.ai.sessionvault.rag;

import com.jitendra.ai.sessionvault.rag.dto.RagSearchRequest;
import com.jitendra.ai.sessionvault.rag.dto.RagSearchResponse;
import com.jitendra.ai.sessionvault.rag.dto.RelatedSessionsResponse;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagMcpTools {

    private final RagSearchService searchService;
    private final RagRelatedSessionService relatedSessionService;

    public RagMcpTools(RagSearchService searchService, RagRelatedSessionService relatedSessionService) {
        this.searchService = searchService;
        this.relatedSessionService = relatedSessionService;
    }

    @McpTool(
            name = "semantic_recall",
            description = "Recall relevant grounded excerpts from prior AI sessions using semantic and keyword search.")
    public RagSearchResponse semanticRecall(
            @McpToolParam(description = "Natural-language question about historical sessions", required = true)
            String query,
            @McpToolParam(description = "Maximum excerpts to return (1-20)", required = false)
            Integer topK) {
        int boundedTopK = topK == null ? 5 : Math.max(1, Math.min(topK, 20));
        return searchService.search(new RagSearchRequest(query, boundedTopK, 0.0, 0.5, null));
    }

    @McpTool(
            name = "find_related_sessions",
            description = "Find up to five relevant historical sessions and summarize their grounded excerpts.")
    public RelatedSessionsResponse findRelatedSessions(
            @McpToolParam(description = "Topic or current problem description", required = true)
            String query) {
        return relatedSessionService.findRelatedSessions(query);
    }
}
