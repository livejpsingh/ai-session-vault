package com.jitendra.ai.sessionvault.rag;

import com.jitendra.ai.sessionvault.rag.dto.RagChunkResult;
import com.jitendra.ai.sessionvault.rag.dto.RagSearchResponse;
import com.jitendra.ai.sessionvault.rag.dto.RelatedSessionsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagMcpToolsTest {

    @Mock
    private RagSearchService searchService;

    @Mock
    private RagRelatedSessionService relatedSessionService;

    @InjectMocks
    private RagMcpTools tools;

    @Test
    void semanticRecallCapsResultsAndDelegatesSearch() {
        UUID sessionId = UUID.randomUUID();
        RagChunkResult chunk = new RagChunkResult(
                UUID.randomUUID(), sessionId, "external-1", "Session", "project",
                "grounded answer", "USER>MODEL", 0.8, 0.9, 0.2, null);
        when(searchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RagSearchResponse("how was this fixed?", List.of(chunk)));

        RagSearchResponse response = tools.semanticRecall("how was this fixed?", 50);

        assertThat(response.results()).containsExactly(chunk);
        verify(searchService).search(argThat(request ->
                request.topK() == 20
                        && request.query().equals("how was this fixed?")
                        && request.hybridWeight() == 0.5));
    }

    @Test
    void findRelatedSessionsDelegatesToSummaryService() {
        RelatedSessionsResponse expected = new RelatedSessionsResponse("database migration", List.of());
        when(relatedSessionService.findRelatedSessions("database migration")).thenReturn(expected);

        assertThat(tools.findRelatedSessions("database migration")).isSameAs(expected);
    }
}
