package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.domain.MessageRole;
import com.jitendra.ai.sessionvault.dto.SearchQueryRequest;
import com.jitendra.ai.sessionvault.dto.SearchResultDto;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ChatSessionRepository sessionRepository;

    @InjectMocks
    private SearchServiceImpl searchService;

    private ChatSession session;
    private ChatMessage message;

    @BeforeEach
    void setUp() {
        session = ChatSession.builder()
                .id(UUID.randomUUID())
                .externalSessionId("conv-abc")
                .title("Database Optimization")
                .projectName("SessionVault")
                .build();

        message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sequenceNumber(1)
                .role(MessageRole.MODEL)
                .content("We should use PostgreSQL GIN indexes for TSVECTOR full text search.")
                .thinking("Evaluating index types between BTREE and GIN.")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("search returns formatted search results with snippets")
    void search_ReturnsResults() {
        SearchQueryRequest request = SearchQueryRequest.builder()
                .query("PostgreSQL")
                .page(0)
                .size(10)
                .build();

        Page<ChatMessage> page = new PageImpl<>(List.of(message));
        when(messageRepository.searchByFullText(eq("PostgreSQL"), any(Pageable.class))).thenReturn(page);

        Page<SearchResultDto> results = searchService.search(request);

        assertThat(results.getTotalElements()).isEqualTo(1);
        SearchResultDto dto = results.getContent().get(0);
        assertThat(dto.getSessionTitle()).isEqualTo("Database Optimization");
        assertThat(dto.getMatchedSnippet()).contains("PostgreSQL GIN");
        assertThat(dto.getThinkingSnippet()).contains("Evaluating index types");
    }

    @Test
    @DisplayName("search throws IllegalArgumentException when query is null or blank")
    void search_BlankQuery_ThrowsException() {
        SearchQueryRequest request = SearchQueryRequest.builder().query("   ").build();

        assertThatThrownBy(() -> searchService.search(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    @Test
    @DisplayName("searchInSession returns results for specific session")
    void searchInSession_Success() {
        UUID sessionId = session.getId();
        when(sessionRepository.existsById(sessionId)).thenReturn(true);
        when(messageRepository.searchInSessionByFullText(eq(sessionId), eq("GIN"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message)));

        Page<SearchResultDto> results = searchService.searchInSession(sessionId, "GIN", Pageable.unpaged());

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getSessionId()).isEqualTo(sessionId);
    }
}
