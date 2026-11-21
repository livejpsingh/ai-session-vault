package com.jitendra.ai.sessionvault.service.impl;

import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.dto.SearchQueryRequest;
import com.jitendra.ai.sessionvault.dto.SearchResultDto;
import com.jitendra.ai.sessionvault.exception.ResourceNotFoundException;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;

    @Override
    public Page<SearchResultDto> search(SearchQueryRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Search query cannot be null or blank");
        }

        int pageNum = Math.max(0, request.getPage());
        int pageSize = request.getSize() > 0 ? Math.min(request.getSize(), 100) : 20;

        log.info("Executing full-text search queryLength={}, page={}, size={}",
                request.getQuery().trim().length(), pageNum, pageSize);

        Pageable pageable = PageRequest.of(pageNum, pageSize);
        Page<ChatMessage> messages = messageRepository.searchByFullText(request.getQuery().trim(), pageable);

        return messages.map(this::mapToSearchResult);
    }

    @Override
    public Page<SearchResultDto> searchInSession(UUID sessionId, String query, Pageable pageable) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID cannot be null");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query cannot be null or blank");
        }
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Chat session not found with ID: " + sessionId);
        }

        log.info("Executing in-session full-text search in session={}", sessionId);
        Page<ChatMessage> messages = messageRepository.searchInSessionByFullText(sessionId, query.trim(), pageable);
        return messages.map(this::mapToSearchResult);
    }

    private SearchResultDto mapToSearchResult(ChatMessage msg) {
        ChatSession session = msg.getSession();
        return SearchResultDto.builder()
                .sessionId(session != null ? session.getId() : null)
                .externalSessionId(session != null ? session.getExternalSessionId() : null)
                .sessionTitle(session != null ? session.getTitle() : "Unknown Session")
                .projectName(session != null ? session.getProjectName() : null)
                .messageId(msg.getId())
                .sequenceNumber(msg.getSequenceNumber())
                .role(msg.getRole())
                .matchedSnippet(truncateSnippet(msg.getContent(), 300))
                .thinkingSnippet(truncateSnippet(msg.getThinking(), 150))
                .messageCreatedAt(msg.getCreatedAt())
                .build();
    }

    private String truncateSnippet(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
