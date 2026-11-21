#!/usr/bin/env pwsh
# AI Session Vault - Stop Hook
# Fires when the Antigravity execution loop terminates (after each turn).
# Syncs the latest transcript to the vault so the session is always up-to-date.

param()

$payload = $null
try {
    $rawInput = [Console]::In.ReadToEnd()
    $payload  = $rawInput | ConvertFrom-Json
} catch {
    Write-Output '{}'
    exit 0
}

# Only sync when the agent stopped normally (not on user cancel mid-turn)
$terminationReason = if ($payload.terminationReason) { $payload.terminationReason } else { "" }
if ($terminationReason -notin @("NO_TOOL_CALL", "MAX_INVOCATIONS", "")) {
    Write-Output '{}'
    exit 0
}

$conversationId = if ($payload.conversationId) { $payload.conversationId } else { "" }
$transcriptPath = if ($payload.transcriptPath) { $payload.transcriptPath } else { "" }
$workspacePaths = if ($payload.workspacePaths) { $payload.workspacePaths } else { @() }
$workspacePath  = if ($workspacePaths.Count -gt 0) { $workspacePaths[0] } else { "" }

if (-not $conversationId -or -not $transcriptPath) {
    Write-Output '{}'
    exit 0
}

if (-not (Test-Path $transcriptPath)) {
    Write-Output '{}'
    exit 0
}

$apiKey = if ($env:VAULT_API_KEY) { $env:VAULT_API_KEY } else { "vault-secret-key-2026" }
$vaultBase = "http://localhost:8080"

# Quick health check
try {
    $health = Invoke-RestMethod -Uri "$vaultBase/actuator/health" -Method Get -TimeoutSec 2 -ErrorAction Stop
    if ($health.status -ne "UP") { Write-Output '{}'; exit 0 }
} catch {
    Write-Output '{}'
    exit 0
}

$body = @{
    externalSessionId = $conversationId
    filePath          = $transcriptPath
    workspacePath     = $workspacePath
    provider          = "google-antigravity"
}

$headers = @{
    "X-API-KEY"    = $apiKey
    "Content-Type" = "application/json"
}

try {
    Invoke-RestMethod `
        -Uri     "$vaultBase/api/transcripts/ingest" `
        -Method  Post `
        -Headers $headers `
        -Body    ($body | ConvertTo-Json -Depth 5) `
        -TimeoutSec 10 `
        -ErrorAction Stop | Out-Null
} catch {
    # Fail silently — never block the agent
}

Write-Output '{}'
exit 0
