#!/usr/bin/env pwsh
# AI Session Vault - Stop Hook
# Fires after each turn ends — syncs the latest transcript to the vault.

param()

$payload = $null
try {
    $rawInput = [Console]::In.ReadToEnd()
    $payload  = $rawInput | ConvertFrom-Json
} catch { Write-Output '{}'; exit 0 }

$terminationReason = if ($payload.terminationReason) { $payload.terminationReason } else { "" }
if ($terminationReason -notin @("NO_TOOL_CALL", "MAX_INVOCATIONS", "")) { Write-Output '{}'; exit 0 }

$conversationId = if ($payload.conversationId) { $payload.conversationId } else { "" }
$transcriptPath = if ($payload.transcriptPath) { $payload.transcriptPath } else { "" }
$workspacePaths = if ($payload.workspacePaths) { $payload.workspacePaths } else { @() }
$workspacePath  = if ($workspacePaths.Count -gt 0) { $workspacePaths[0] } else { "" }

# Skip diagnostic smoke-test sessions
if ($conversationId -match "diagnostic|smoke|test-only") { Write-Output '{}'; exit 0 }

if (-not $conversationId -or -not $transcriptPath -or -not (Test-Path $transcriptPath)) {
    Write-Output '{}'
    exit 0
}

$apiKey = $env:VAULT_API_KEY
if (-not $apiKey -or $apiKey -eq "") {
    $envFile = Join-Path (Resolve-Path "$PSScriptRoot\..\..") ".env"
    if (Test-Path $envFile) {
        $line = Get-Content $envFile | Where-Object { $_ -match '^\s*VAULT_API_KEY\s*=' } | Select-Object -First 1
        if ($line) { $apiKey = ($line -split '=', 2)[1].Trim().Trim('"').Trim("'") }
    }
}
if (-not $apiKey -or $apiKey -eq "") { Write-Output '{}'; exit 0 }
$vaultBase = "http://localhost:8080"

try {
    $health = Invoke-RestMethod -Uri "$vaultBase/actuator/health" -Method Get -TimeoutSec 2 -ErrorAction Stop
    if ($health.status -ne "UP") { Write-Output '{}'; exit 0 }
} catch { Write-Output '{}'; exit 0 }

$body = @{
    externalSessionId = $conversationId
    filePath          = $transcriptPath
    workspacePath     = $workspacePath
    provider          = "google-antigravity"
} | ConvertTo-Json -Depth 5

$headers = @{ "X-API-KEY" = $apiKey; "Content-Type" = "application/json" }

try {
    Invoke-RestMethod `
        -Uri     "$vaultBase/api/transcripts/ingest" `
        -Method  Post `
        -Headers $headers `
        -Body    $body `
        -TimeoutSec 10 `
        -ErrorAction Stop | Out-Null
} catch { }

Write-Output '{}'
exit 0
