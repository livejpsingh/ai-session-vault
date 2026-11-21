package com.jitendra.ai.sessionvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.dto.ChatSessionDto;
import com.jitendra.ai.sessionvault.dto.CreateSessionRequest;
import com.jitendra.ai.sessionvault.exception.GlobalExceptionHandler;
import com.jitendra.ai.sessionvault.exception.ResourceNotFoundException;
import com.jitendra.ai.sessionvault.service.SessionService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private SessionController sessionController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(sessionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/sessions returns 201 Created on valid request")
    void createSession_Valid_Returns201() throws Exception {
        CreateSessionRequest request = CreateSessionRequest.builder()
                .title("Architecture Review")
                .externalSessionId("ext-101")
                .build();

        ChatSessionDto responseDto = ChatSessionDto.builder()
                .id(sessionId)
                .title("Architecture Review")
                .externalSessionId("ext-101")
                .build();

        when(sessionService.createSession(any(CreateSessionRequest.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.title").value("Architecture Review"));
    }

    @Test
    @DisplayName("POST /api/sessions returns 400 Bad Request on blank title")
    void createSession_BlankTitle_Returns400() throws Exception {
        CreateSessionRequest request = CreateSessionRequest.builder()
                .title("   ")
                .build();

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    @DisplayName("GET /api/sessions/{id} returns 200 OK with session data")
    void getSessionById_Found_Returns200() throws Exception {
        ChatSessionDto dto = ChatSessionDto.builder().id(sessionId).title("Sample").build();
        when(sessionService.getSessionById(sessionId, false)).thenReturn(dto);

        mockMvc.perform(get("/api/sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()));
    }

    @Test
    @DisplayName("GET /api/sessions/{id} returns 404 Not Found for missing session")
    void getSessionById_NotFound_Returns404() throws Exception {
        when(sessionService.getSessionById(sessionId, false))
                .thenThrow(new ResourceNotFoundException("Session not found"));

        mockMvc.perform(get("/api/sessions/{id}", sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("GET /api/sessions returns paged list")
    void listSessions_Returns200() throws Exception {
        Page<ChatSessionDto> page = new PageImpl<>(Collections.emptyList(), org.springframework.data.domain.PageRequest.of(0, 10), 0);
        when(sessionService.listSessions(any(Pageable.class), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/sessions?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("DELETE /api/sessions/{id} returns 204 No Content")
    void deleteSession_Returns204() throws Exception {
        mockMvc.perform(delete("/api/sessions/{id}", sessionId))
                .andExpect(status().isNoContent());
    }
}
