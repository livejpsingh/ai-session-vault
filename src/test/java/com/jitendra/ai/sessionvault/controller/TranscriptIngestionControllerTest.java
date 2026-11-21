package com.jitendra.ai.sessionvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.dto.TranscriptIngestRequest;
import com.jitendra.ai.sessionvault.dto.TranscriptIngestResult;
import com.jitendra.ai.sessionvault.exception.GlobalExceptionHandler;
import com.jitendra.ai.sessionvault.service.TranscriptIngestionService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TranscriptIngestionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TranscriptIngestionService ingestionService;

    @InjectMocks
    private TranscriptIngestionController ingestionController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ingestionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/transcripts/ingest returns 201 Created with ingestion result")
    void ingestTranscript_Returns201() throws Exception {
        TranscriptIngestRequest req = TranscriptIngestRequest.builder()
                .externalSessionId("conv-101")
                .jsonlContent("{\"content\": \"test message\"}")
                .build();

        TranscriptIngestResult result = TranscriptIngestResult.builder()
                .sessionId(UUID.randomUUID())
                .externalSessionId("conv-101")
                .messagesIngested(1)
                .duplicatesSkipped(0)
                .totalMessages(1)
                .success(true)
                .message("Transcript ingested successfully")
                .build();

        when(ingestionService.ingestTranscript(any(TranscriptIngestRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/transcripts/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messagesIngested").value(1))
                .andExpect(jsonPath("$.duplicatesSkipped").value(0));
    }
}
