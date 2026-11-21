package com.jitendra.ai.sessionvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.dto.SearchQueryRequest;
import com.jitendra.ai.sessionvault.dto.SearchResultDto;
import com.jitendra.ai.sessionvault.exception.GlobalExceptionHandler;
import com.jitendra.ai.sessionvault.service.SearchService;
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController searchController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(searchController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/search with query returns 200 OK")
    void searchGet_Returns200() throws Exception {
        Page<SearchResultDto> page = new PageImpl<>(Collections.emptyList(), org.springframework.data.domain.PageRequest.of(0, 10), 0);
        when(searchService.search(any())).thenReturn(page);

        mockMvc.perform(get("/api/search?query=indexing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("POST /api/search with valid body returns 200 OK")
    void searchPost_Valid_Returns200() throws Exception {
        SearchQueryRequest req = SearchQueryRequest.builder().query("vector search").build();
        Page<SearchResultDto> page = new PageImpl<>(Collections.emptyList(), org.springframework.data.domain.PageRequest.of(0, 10), 0);
        when(searchService.search(any())).thenReturn(page);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/sessions/{id}/search returns 200 OK")
    void searchInSession_Returns200() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Page<SearchResultDto> page = new PageImpl<>(Collections.emptyList(), org.springframework.data.domain.PageRequest.of(0, 10), 0);
        when(searchService.searchInSession(eq(sessionId), eq("audit"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/sessions/{sessionId}/search?query=audit", sessionId))
                .andExpect(status().isOk());
    }
}
