#!/usr/bin/env pwsh
# AI Session Vault - SessionStart Hook
# Fires once when Antigravity/GitHub Copilot opens a new conversation.
# Auto-ingests the transcript so chat history is always available via MCP tools.

param()

# --- Read & parse the hook payload from stdin ---
$payload = $null
try {
    $rawInput = [Console]::In.ReadToEnd()
    $payload  = $rawInput | ConvertFrom-Json
} catch {
    Write-Output '{"injectSteps":[]}'
    exit 0
}

# --- Extract context fields ---
$conversationId = if ($payload.conversationId) { $payload.conversationId } else { "" }
$transcriptPath = if ($payload.transcriptPath) { $payload.transcriptPath } else { "" }
$workspacePaths = if ($payload.workspacePaths) { $payload.workspacePaths } else { @() }
$workspacePath  = if ($workspacePaths.Count -gt 0) { $workspacePaths[0] } else { "" }

# Skip diagnostic / smoke-test sessions — these are MCP client self-tests, not real chats
if ($conversationId -match "diagnostic|smoke|test-only") {
    Write-Output '{"injectSteps":[]}'
    exit 0
}

# --- API key: env var takes priority, then hardcoded default ---
$apiKey = if ($env:VAULT_API_KEY -and $env:VAULT_API_KEY -ne "") { $env:VAULT_API_KEY } else { "vault-secret-key-2026" }
$vaultBase = "http://localhost:8080"

# --- Check vault service is reachable ---
try {
    $health = Invoke-RestMethod -Uri "$vaultBase/actuator/health" -Method Get -TimeoutSec 2 -ErrorAction Stop
    if ($health.status -ne "UP") { Write-Output '{"injectSteps":[]}'; exit 0 }
} catch {
    Write-Output '{"injectSteps":[]}'
    exit 0
}

# --- Nothing to ingest if transcript file doesn't exist yet (brand-new empty session) ---
if (-not $transcriptPath -or -not (Test-Path $transcriptPath)) {
    Write-Output '{"injectSteps":[]}'
    exit 0
}

$headers = @{ "X-API-KEY" = $apiKey; "Content-Type" = "application/json" }
$body = @{
    externalSessionId = $conversationId
    filePath          = $transcriptPath
    workspacePath     = $workspacePath
    provider          = "google-antigravity"
} | ConvertTo-Json -Depth 5

# --- POST to ingest endpoint ---
$result = $null
try {
    $result = Invoke-RestMethod `
        -Uri     "$vaultBase/api/transcripts/ingest" `
        -Method  Post `
        -Headers $headers `
        -Body    $body `
        -TimeoutSec 10 `
        -ErrorAction Stop
} catch {
    Write-Output '{"injectSteps":[]}'
    exit 0
}

# --- Inject a markdown system message showing history is available ---
$total = if ($result.totalMessages) { $result.totalMessages } else { 0 }
$newOnes = if ($result.messagesIngested) { $result.messagesIngested } else { 0 }

if ($total -gt 0) {
    $mdMsg = "**AI Session Vault** — Chat history loaded for session \`$conversationId\`. " +
             "**$total messages** stored ($newOnes newly synced). " +
             "Use MCP tools: \`vault_search_messages\`, \`vault_list_sessions\`, \`vault_get_session\`."

    $response = @{
        injectSteps = @(
            @{
                systemMessage = @{
                    systemMessage = "AI Session Vault: $total messages available for session $conversationId."
                    metadata = @{ user_markdown = $mdMsg }
                }
            }
        )
    }
    Write-Output ($response | ConvertTo-Json -Depth 10 -Compress)
} else {
    Write-Output '{"injectSteps":[]}'
}

exit 0
