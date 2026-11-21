package com.jitendra.ai.sessionvault.controller;

import com.jitendra.ai.sessionvault.dto.ExportFormat;
import com.jitendra.ai.sessionvault.exception.GlobalExceptionHandler;
import com.jitendra.ai.sessionvault.service.ExportService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExportService exportService;

    @InjectMocks
    private ExportController exportController;

    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        mockMvc = MockMvcBuilders.standaloneSetup(exportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/sessions/{id}/export?format=MARKDOWN returns text/markdown with attachment header")
    void exportMarkdown_ReturnsMarkdownContent() throws Exception {
        when(exportService.exportSession(sessionId, ExportFormat.MARKDOWN))
                .thenReturn("# Session Export Markdown");

        mockMvc.perform(get("/api/sessions/{id}/export?format=MARKDOWN", sessionId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_MARKDOWN))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"session-" + sessionId + ".md\""))
                .andExpect(content().string("# Session Export Markdown"));
    }

    @Test
    @DisplayName("GET /api/sessions/{id}/export?format=JSON returns application/json with attachment header")
    void exportJson_ReturnsJsonContent() throws Exception {
        when(exportService.exportSession(sessionId, ExportFormat.JSON))
                .thenReturn("{\"title\": \"Session Export\"}");

        mockMvc.perform(get("/api/sessions/{id}/export?format=JSON", sessionId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"session-" + sessionId + ".json\""))
                .andExpect(content().string("{\"title\": \"Session Export\"}"));
    }

    @Test
    @DisplayName("GET /api/sessions/external/{externalId}/export returns exported content")
    void exportByExternalId_Returns200() throws Exception {
        when(exportService.exportSessionByExternalId("conv-999", ExportFormat.MARKDOWN))
                .thenReturn("# External Session Markdown");

        mockMvc.perform(get("/api/sessions/external/{externalId}/export?format=MARKDOWN", "conv-999"))
                .andExpect(status().isOk())
                .andExpect(content().string("# External Session Markdown"));
    }
}
