package com.jitendra.ai.sessionvault.controller;

import com.jitendra.ai.sessionvault.dto.ContextOptimizationRequest;
import com.jitendra.ai.sessionvault.dto.OptimizedContextResponse;
import com.jitendra.ai.sessionvault.service.AgentContextGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agent Context Gateway", description = "Centralized gateway providing budget-optimized memory context for coding and research agents")
public class AgentContextController {

    private final AgentContextGateway contextGateway;

    @PostMapping("/context")
    @Operation(summary = "Retrieve scoped, budget-optimized context for an AI agent")
    public ResponseEntity<OptimizedContextResponse> getAgentContext(
            @Valid @RequestBody ContextOptimizationRequest request) {
        log.info("AgentContextController: Context request received for agentId='{}', budget={}",
                request.getAgentId(), request.getTokenBudget());
        return ResponseEntity.ok(contextGateway.getAgentContext(request));
    }
}
