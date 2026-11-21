package com.jitendra.ai.sessionvault.controller;

import com.jitendra.ai.sessionvault.dto.SearchQueryRequest;
import com.jitendra.ai.sessionvault.dto.SearchResultDto;
import com.jitendra.ai.sessionvault.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping("/search")
    public ResponseEntity<Page<SearchResultDto>> searchPost(@Valid @RequestBody SearchQueryRequest request) {
        return ResponseEntity.ok(searchService.search(request));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SearchResultDto>> searchGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String tagName) {

        SearchQueryRequest request = SearchQueryRequest.builder()
                .query(query)
                .page(page)
                .size(size)
                .projectName(projectName)
                .provider(provider)
                .tagName(tagName)
                .build();

        return ResponseEntity.ok(searchService.search(request));
    }

    @GetMapping("/sessions/{sessionId}/search")
    public ResponseEntity<Page<SearchResultDto>> searchInSession(
            @PathVariable UUID sessionId,
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(searchService.searchInSession(sessionId, query, pageable));
    }
}
