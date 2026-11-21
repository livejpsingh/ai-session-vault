package com.jitendra.ai.sessionvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.domain.MessageRole;
import com.jitendra.ai.sessionvault.dto.ChatMessageDto;
import com.jitendra.ai.sessionvault.dto.SaveMessageRequest;
import com.jitendra.ai.sessionvault.exception.GlobalExceptionHandler;
import com.jitendra.ai.sessionvault.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageController messageController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(messageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/sessions/{id}/messages creates message")
    void addMessage_Valid_Returns201() throws Exception {
        SaveMessageRequest req = SaveMessageRequest.builder()
                .role(MessageRole.USER)
                .content("What is Spring Boot?")
                .build();

        ChatMessageDto res = ChatMessageDto.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .role(MessageRole.USER)
                .content("What is Spring Boot?")
                .sequenceNumber(1)
                .build();

        when(messageService.addMessage(eq(sessionId), any(SaveMessageRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("What is Spring Boot?"))
                .andExpect(jsonPath("$.sequenceNumber").value(1));
    }

    @Test
    @DisplayName("POST /api/sessions/{id}/messages with blank content returns 400 Bad Request")
    void addMessage_BlankContent_Returns400() throws Exception {
        SaveMessageRequest req = SaveMessageRequest.builder()
                .role(MessageRole.USER)
                .content("   ")
                .build();

        mockMvc.perform(post("/api/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    @DisplayName("GET /api/sessions/{id}/messages returns message list")
    void getMessages_Returns200() throws Exception {
        ChatMessageDto dto = ChatMessageDto.builder().id(UUID.randomUUID()).content("A").build();
        when(messageService.getMessagesBySessionId(sessionId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/sessions/{sessionId}/messages", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("A"));
    }
}
