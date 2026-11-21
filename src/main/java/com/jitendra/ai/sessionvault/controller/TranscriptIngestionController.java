package com.jitendra.ai.sessionvault.controller;

import com.jitendra.ai.sessionvault.dto.TranscriptIngestRequest;
import com.jitendra.ai.sessionvault.dto.TranscriptIngestResult;
import com.jitendra.ai.sessionvault.service.TranscriptIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transcripts")
@RequiredArgsConstructor
public class TranscriptIngestionController {

    private final TranscriptIngestionService ingestionService;

    @PostMapping("/ingest")
    public ResponseEntity<TranscriptIngestResult> ingestTranscript(@Valid @RequestBody TranscriptIngestRequest request) {
        TranscriptIngestResult result = ingestionService.ingestTranscript(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
