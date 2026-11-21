package com.jitendra.ai.sessionvault.controller;

import com.jitendra.ai.sessionvault.dto.ChatMessageDto;
import com.jitendra.ai.sessionvault.dto.SaveMessageRequest;
import com.jitendra.ai.sessionvault.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<ChatMessageDto> addMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SaveMessageRequest request) {
        ChatMessageDto created = messageService.addMessage(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<ChatMessageDto>> addMessagesBatch(
            @PathVariable UUID sessionId,
            @RequestBody List<@Valid SaveMessageRequest> requests) {
        List<ChatMessageDto> created = messageService.addMessagesBatch(sessionId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ChatMessageDto>> getMessagesBySessionId(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(messageService.getMessagesBySessionId(sessionId));
    }
}
