package com.jitendra.ai.sessionvault.controller;

import com.jitendra.ai.sessionvault.dto.ExportFormat;
import com.jitendra.ai.sessionvault.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/{id}/export")
    public ResponseEntity<String> exportSessionById(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "MARKDOWN") ExportFormat format) {
        String exported = exportService.exportSession(id, format);
        return buildExportResponse(exported, format, "session-" + id);
    }

    @GetMapping("/external/{externalId}/export")
    public ResponseEntity<String> exportSessionByExternalId(
            @PathVariable String externalId,
            @RequestParam(defaultValue = "MARKDOWN") ExportFormat format) {
        String exported = exportService.exportSessionByExternalId(externalId, format);
        return buildExportResponse(exported, format, "session-" + externalId);
    }

    private ResponseEntity<String> buildExportResponse(String content, ExportFormat format, String filenameBase) {
        MediaType mediaType;
        String filename;

        if (format == ExportFormat.JSON) {
            mediaType = MediaType.APPLICATION_JSON;
            filename = filenameBase + ".json";
        } else {
            mediaType = MediaType.TEXT_MARKDOWN;
            filename = filenameBase + ".md";
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }
}
