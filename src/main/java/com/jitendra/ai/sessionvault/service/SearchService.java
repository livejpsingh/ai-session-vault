package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.dto.SearchQueryRequest;
import com.jitendra.ai.sessionvault.dto.SearchResultDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SearchService {
    Page<SearchResultDto> search(SearchQueryRequest request);
    Page<SearchResultDto> searchInSession(UUID sessionId, String query, Pageable pageable);
}
