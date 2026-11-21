package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.dto.ExportFormat;

import java.util.UUID;

public interface ExportService {
    String exportSession(UUID sessionId, ExportFormat format);
    String exportSessionByExternalId(String externalId, ExportFormat format);
}
