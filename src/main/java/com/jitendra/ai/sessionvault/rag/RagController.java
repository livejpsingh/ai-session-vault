package com.jitendra.ai.sessionvault.rag;

import com.jitendra.ai.sessionvault.rag.dto.RagSearchRequest;
import com.jitendra.ai.sessionvault.rag.dto.RagSearchResponse;
import com.jitendra.ai.sessionvault.rag.dto.ReindexAllResponse;
import com.jitendra.ai.sessionvault.rag.dto.ReindexResponse;
import com.jitendra.ai.sessionvault.rag.dto.ReindexStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagController {

    private final RagSearchService searchService;
    private final RagIndexingService indexingService;

    @PostMapping("/search")
    public ResponseEntity<RagSearchResponse> search(@Valid @RequestBody RagSearchRequest request) {
        return ResponseEntity.ok(searchService.search(request));
    }

    @PostMapping("/reindex/{sessionId}")
    public ResponseEntity<ReindexResponse> reindex(@PathVariable UUID sessionId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(indexingService.reindex(sessionId));
    }

    @GetMapping("/reindex/{sessionId}")
    public ResponseEntity<ReindexStatusResponse> reindexStatus(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(indexingService.status(sessionId));
    }

    @PostMapping("/reindex-all")
    public ResponseEntity<ReindexAllResponse> reindexAll() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(indexingService.reindexAll());
    }
}
