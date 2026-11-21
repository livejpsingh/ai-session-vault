#!/usr/bin/env pwsh
# AI Session Vault - SessionStart Hook
# Fires once when Antigravity opens a new conversation.
# Reads the hook payload from stdin, then auto-ingests the transcript into the vault.

param()

# --- Read & parse the hook payload from stdin ---
$payload = $null
try {
    $rawInput = [Console]::In.ReadToEnd()
    $payload  = $rawInput | ConvertFrom-Json
} catch {
    # If stdin is empty or not JSON, exit cleanly (no-op)
    Write-Output '{"injectSteps":[]}'
    exit 0
}

# --- Extract context fields ---
$conversationId    = if ($payload.conversationId)       { $payload.conversationId }       else { "" }
$transcriptPath    = if ($payload.transcriptPath)       { $payload.transcriptPath }       else { "" }
$workspacePaths    = if ($payload.workspacePaths)       { $payload.workspacePaths }       else { @() }
$workspacePath     = if ($workspacePaths.Count -gt 0)  { $workspacePaths[0] }            else { "" }

# --- Resolve API key (env var takes precedence over default) ---
$apiKey = if ($env:VAULT_API_KEY) { $env:VAULT_API_KEY } else { "vault-secret-key-2026" }

# --- Check vault service is reachable ---
$vaultBase = "http://localhost:8080"
try {
    $health = Invoke-RestMethod -Uri "$vaultBase/actuator/health" -Method Get -TimeoutSec 2 -ErrorAction Stop
    if ($health.status -ne "UP") {
        Write-Output '{"injectSteps":[]}'
        exit 0
    }
} catch {
    # Vault not running — skip silently, don't block the session
    Write-Output '{"injectSteps":[]}'
    exit 0
}

# --- Build ingest request payload ---
$body = @{
    externalSessionId = $conversationId
    workspacePath     = $workspacePath
    provider          = "google-antigravity"
}

if ($transcriptPath -and (Test-Path $transcriptPath)) {
    $body["filePath"] = $transcriptPath
} else {
    # No transcript yet (brand-new session) — just ensure the session record exists
    $body["jsonlContent"] = ""
    # Send a minimal no-op; service will create the shell session
    Write-Output '{"injectSteps":[]}'
    exit 0
}

$headers = @{
    "X-API-KEY"    = $apiKey
    "Content-Type" = "application/json"
}

# --- POST to ingest endpoint ---
$ingestResult = $null
try {
    $ingestResult = Invoke-RestMethod `
        -Uri     "$vaultBase/api/transcripts/ingest" `
        -Method  Post `
        -Headers $headers `
        -Body    ($body | ConvertTo-Json -Depth 5) `
        -TimeoutSec 10 `
        -ErrorAction Stop
} catch {
    # Ingest failed — don't block the chat session
    Write-Output '{"injectSteps":[]}'
    exit 0
}

# --- Inject a system message so Antigravity knows history is available ---
$msgCount = if ($ingestResult.totalMessages) { $ingestResult.totalMessages } else { 0 }
$newCount  = if ($ingestResult.messagesIngested) { $ingestResult.messagesIngested } else { 0 }

if ($msgCount -gt 0) {
    $vaultMsg = "**AI Session Vault:** Chat history loaded. " +
                "Session `$conversationId` has **$msgCount messages** stored " +
                "($newCount newly ingested). " +
                "Use MCP tools vault_search_messages or vault_list_sessions to recall past context."

    $response = @{
        injectSteps = @(
            @{
                systemMessage = @{
                    systemMessage = "AI Session Vault: Chat history is available for session $conversationId. Total messages in vault: $msgCount."
                    metadata = @{
                        user_markdown = $vaultMsg
                    }
                }
            }
        )
    }
    Write-Output ($response | ConvertTo-Json -Depth 10 -Compress)
} else {
    Write-Output '{"injectSteps":[]}'
}

exit 0
