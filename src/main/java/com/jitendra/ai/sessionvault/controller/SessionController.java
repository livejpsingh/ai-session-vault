package com.jitendra.ai.sessionvault.controller;

import com.jitendra.ai.sessionvault.dto.ChatSessionDto;
import com.jitendra.ai.sessionvault.dto.CreateSessionRequest;
import com.jitendra.ai.sessionvault.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<ChatSessionDto> createSession(@Valid @RequestBody CreateSessionRequest request) {
        ChatSessionDto created = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatSessionDto> getSessionById(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean includeMessages) {
        return ResponseEntity.ok(sessionService.getSessionById(id, includeMessages));
    }

    @GetMapping("/external/{externalId}")
    public ResponseEntity<ChatSessionDto> getSessionByExternalId(
            @PathVariable String externalId,
            @RequestParam(defaultValue = "false") boolean includeMessages) {
        return ResponseEntity.ok(sessionService.getSessionByExternalId(externalId, includeMessages));
    }

    @GetMapping
    public ResponseEntity<Page<ChatSessionDto>> listSessions(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String tagName) {
        return ResponseEntity.ok(sessionService.listSessions(pageable, projectName, provider, tagName));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }
}
