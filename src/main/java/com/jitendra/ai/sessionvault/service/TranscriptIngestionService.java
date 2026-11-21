package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.dto.TranscriptIngestRequest;
import com.jitendra.ai.sessionvault.dto.TranscriptIngestResult;

public interface TranscriptIngestionService {
    TranscriptIngestResult ingestTranscript(TranscriptIngestRequest request);
}
