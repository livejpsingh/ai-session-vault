param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("antigravity", "copilot")]
    [string] $Platform,

    [Parameter(Mandatory = $true)]
    [ValidateSet("SessionStart", "Stop")]
    [string] $Event
)

$ErrorActionPreference = "Stop"
$script:RepositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))

function Get-Value {
    param(
        [object] $Object,
        [string[]] $Names
    )

    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property -and $null -ne $property.Value) {
            return $property.Value
        }
    }
    return $null
}

function Get-Setting {
    param([string] $Name)

    $value = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($value)) {
        return $value
    }

    $envFile = Join-Path $script:RepositoryRoot ".env"
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        return $null
    }

    foreach ($line in [IO.File]::ReadLines($envFile)) {
        if ($line -match "^\s*$([regex]::Escape($Name))\s*=\s*(.*?)\s*$") {
            return $Matches[1].Trim().Trim('"').Trim("'")
        }
    }
    return $null
}

function Get-HttpStatusCode {
    param([object] $ErrorRecord)

    try {
        return [int] $ErrorRecord.Exception.Response.StatusCode
    } catch {
        return 0
    }
}

function Get-TranscriptPath {
    param([string] $Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    if ($Path.StartsWith("~/") -or $Path.StartsWith("~\")) {
        return Join-Path $HOME ($Path.Substring(2) -replace "/", "\")
    }
    return [Environment]::ExpandEnvironmentVariables($Path)
}

function Write-HookResult {
    param([object] $Result)

    [Console]::Out.WriteLine(($Result | ConvertTo-Json -Depth 10 -Compress))
}

$isAntigravity = $Platform -eq "antigravity"
$defaultResult = if ($isAntigravity -and $Event -eq "SessionStart") {
    @{ injectSteps = @() }
} elseif ($isAntigravity) {
    @{ decision = "stop" }
} else {
    @{}
}

try {
    $rawInput = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($rawInput)) {
        throw "The lifecycle hook did not provide a JSON payload."
    }
    $payload = $rawInput | ConvertFrom-Json

    $sessionId = Get-Value $payload @("conversationId", "session_id", "sessionId")
    if ([string]::IsNullOrWhiteSpace([string] $sessionId)) {
        throw "The lifecycle hook payload has no session identifier."
    }

    $workspacePath = Get-Value $payload @("cwd")
    $workspacePaths = Get-Value $payload @("workspacePaths")
    if ($null -ne $workspacePaths) {
        $workspacePaths = @($workspacePaths)
        if ($workspacePaths.Count -gt 0) {
            $workspacePath = $workspacePaths[0]
        }
    }
    if ([string]::IsNullOrWhiteSpace([string] $workspacePath)) {
        $workspacePath = $script:RepositoryRoot
    }

    $externalSessionId = if ($isAntigravity) {
        [string] $sessionId
    } else {
        "github-copilot:$sessionId"
    }
    $provider = if ($isAntigravity) { "google-antigravity" } else { "github-copilot" }
    $baseUrl = Get-Setting "VAULT_BASE_URL"
    if ([string]::IsNullOrWhiteSpace($baseUrl)) {
        $baseUrl = "http://localhost:8080"
    }
    $baseUrl = $baseUrl.TrimEnd("/")

    $apiKey = Get-Setting "VAULT_API_KEY"
    if ([string]::IsNullOrWhiteSpace($apiKey)) {
        $apiKey = "vault-secret-key-2026"
    }
    $headers = @{ "X-API-KEY" = $apiKey }

    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get -TimeoutSec 3
    if ($health.status -ne "UP") {
        throw "AI Session Vault is not healthy."
    }

    if ($Event -eq "SessionStart") {
        $encodedId = [Uri]::EscapeDataString($externalSessionId)
        $existing = $false
        try {
            Invoke-RestMethod `
                -Uri "$baseUrl/api/sessions/external/$encodedId" `
                -Method Get -Headers $headers -TimeoutSec 5 | Out-Null
            $existing = $true
        } catch {
            if ((Get-HttpStatusCode $_) -ne 404) {
                throw "Unable to check for an existing vault session (HTTP $(Get-HttpStatusCode $_))."
            }
        }

        if (-not $existing) {
            $leaf = Split-Path -Path $workspacePath -Leaf -ErrorAction SilentlyContinue
            $title = "$provider session $($sessionId.ToString().Substring(0, [Math]::Min(8, $sessionId.ToString().Length)))"
            $createRequest = @{
                externalSessionId = $externalSessionId
                title = $title
                provider = $provider
                workspacePath = $workspacePath
            }
            if (-not [string]::IsNullOrWhiteSpace($leaf)) {
                $createRequest.projectName = $leaf
            }
            $modelName = Get-Value $payload @("modelName", "model_name")
            if (-not [string]::IsNullOrWhiteSpace([string] $modelName)) {
                $createRequest.modelName = $modelName
            }
            Invoke-RestMethod `
                -Uri "$baseUrl/api/sessions" `
                -Method Post -Headers $headers `
                -ContentType "application/json" `
                -Body ($createRequest | ConvertTo-Json -Depth 6 -Compress) `
                -TimeoutSec 10 | Out-Null
        }
    }

    if ($Event -eq "Stop" -or $Event -eq "SessionStart") {
        $transcriptPath = Get-Value $payload @("transcriptPath", "transcript_path")
        $transcriptPath = Get-TranscriptPath ([string] $transcriptPath)
        $hasFile = $transcriptPath -and (Test-Path -LiteralPath $transcriptPath -PathType Leaf)

        $jsonlContent = Get-Value $payload @("jsonlContent", "jsonl_content")
        $inlineMessages = Get-Value $payload @("messages", "history", "chatHistory")
        if (-not $jsonlContent -and $inlineMessages) {
            $lines = foreach ($m in $inlineMessages) {
                $m | ConvertTo-Json -Depth 5 -Compress
            }
            $jsonlContent = $lines -join "`n"
        }
        $hasContent = -not [string]::IsNullOrWhiteSpace($jsonlContent)

        if ($hasFile -or $hasContent) {
            $workspaceLeaf = Split-Path -Path $workspacePath -Leaf -ErrorAction SilentlyContinue
            $ingestRequest = @{
                externalSessionId = $externalSessionId
                provider          = $provider
                workspacePath     = $workspacePath
            }
            if ($hasFile) {
                $ingestRequest.filePath = $transcriptPath
            } else {
                $ingestRequest.jsonlContent = $jsonlContent
            }
            if (-not [string]::IsNullOrWhiteSpace($workspaceLeaf)) {
                $ingestRequest.projectName = $workspaceLeaf
            }
            $modelName = Get-Value $payload @("modelName", "model_name")
            if (-not [string]::IsNullOrWhiteSpace([string] $modelName)) {
                $ingestRequest.modelName = $modelName
            }
            $jsonBody = [System.Text.Encoding]::UTF8.GetBytes(($ingestRequest | ConvertTo-Json -Depth 6 -Compress))
            $ingestResult = Invoke-RestMethod `
                -Uri "$baseUrl/api/transcripts/ingest" `
                -Method Post -Headers $headers `
                -ContentType "application/json; charset=utf-8" `
                -Body $jsonBody `
                -TimeoutSec 30

            if ($isAntigravity -and $Event -eq "SessionStart" -and $ingestResult.totalMessages -gt 0) {
                $defaultResult = @{
                    injectSteps = @(
                        @{
                            systemMessage = @{
                                systemMessage = "AI Session Vault has stored history for this conversation. Use its MCP recall tools when relevant."
                            }
                        }
                    )
                }
            }
        }
    }
} catch {
    [Console]::Error.WriteLine("AI Session Vault $Platform $Event hook could not sync this session: $($_.Exception.Message)")
}

Write-HookResult $defaultResult
exit 0
