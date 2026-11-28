<#
.SYNOPSIS
    Installs and verifies lifecycle hooks for AI Session Vault in VS Code (Antigravity & GitHub Copilot).

.DESCRIPTION
    Configures:
    1. Google Antigravity hooks in .agents/hooks.json
    2. GitHub Copilot hooks in .github/hooks/ai-session-vault.json
    3. VS Code task configuration in .vscode/tasks.json
    4. Validates connectivity to the local AI Session Vault instance (http://localhost:8080)
#>

[CmdletBinding()]
param(
    [string]$VaultUrl = "http://localhost:8080",
    [string]$ApiKey = $env:VAULT_API_KEY
)

$ErrorActionPreference = "Stop"
$RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host " AI Session Vault — Hook & Auto-Capture Installer     " -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "Repository Root: $RepoRoot" -ForegroundColor Gray

# 1. Ensure .agents/hooks.json (for Google Antigravity)
$agentsDir = Join-Path $RepoRoot ".agents"
if (-not (Test-Path $agentsDir)) {
    New-Item -ItemType Directory -Path $agentsDir -Force | Out-Null
}
$agentsHookFile = Join-Path $agentsDir "hooks.json"
$agentsHookContent = @'
{
  "vault-session-sync": {
    "description": "Auto-sync Antigravity conversation transcripts to AI Session Vault. SessionStart registers/backfills the session; Stop syncs the transcript.",
    "SessionStart": [
      {
        "type": "command",
        "command": "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts/session-vault-hook.ps1 -Platform antigravity -Event SessionStart",
        "timeout": 15
      }
    ],
    "Stop": [
      {
        "type": "command",
        "command": "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts/session-vault-hook.ps1 -Platform antigravity -Event Stop",
        "timeout": 45
      }
    ]
  }
}
'@
Set-Content -Path $agentsHookFile -Value $agentsHookContent -Encoding UTF8
Write-Host " [OK] Configured Antigravity lifecycle hooks: .agents/hooks.json" -ForegroundColor Green

# 2. Ensure .github/hooks/ai-session-vault.json (for GitHub Copilot)
$copilotHooksDir = Join-Path $RepoRoot ".github\hooks"
if (-not (Test-Path $copilotHooksDir)) {
    New-Item -ItemType Directory -Path $copilotHooksDir -Force | Out-Null
}
$copilotHookFile = Join-Path $copilotHooksDir "ai-session-vault.json"
$copilotHookContent = @'
{
  "version": 1,
  "hooks": {
    "SessionStart": [
      {
        "type": "command",
        "powershell": "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts/session-vault-hook.ps1 -Platform copilot -Event SessionStart",
        "timeoutSec": 15
      }
    ],
    "Stop": [
      {
        "type": "command",
        "powershell": "powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts/session-vault-hook.ps1 -Platform copilot -Event Stop",
        "timeoutSec": 45
      }
    ]
  }
}
'@
Set-Content -Path $copilotHookFile -Value $copilotHookContent -Encoding UTF8
Write-Host " [OK] Configured GitHub Copilot hooks: .github/hooks/ai-session-vault.json" -ForegroundColor Green

# 3. Configure VS Code Tasks (.vscode/tasks.json)
$vscodeDir = Join-Path $RepoRoot ".vscode"
if (-not (Test-Path $vscodeDir)) {
    New-Item -ItemType Directory -Path $vscodeDir -Force | Out-Null
}
$tasksFile = Join-Path $vscodeDir "tasks.json"
$tasksContent = @'
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "AI Session Vault: Start Local Server",
      "type": "shell",
      "command": "./mvnw spring-boot:run",
      "windows": {
        "command": ".\\mvnw.cmd spring-boot:run"
      },
      "problemMatcher": [],
      "isBackground": true,
      "presentation": {
        "reveal": "silent"
      }
    },
    {
      "label": "AI Session Vault: Verify Health & DB Stats",
      "type": "shell",
      "command": "curl -s -H \"X-API-KEY: ${VAULT_API_KEY}\" http://localhost:8080/api/sessions",
      "windows": {
        "command": "powershell.exe -NoProfile -Command \"Invoke-RestMethod -Uri http://localhost:8080/api/sessions -Headers @{'X-API-KEY'=$env:VAULT_API_KEY}\""
      },
      "problemMatcher": []
    }
  ]
}
'@
Set-Content -Path $tasksFile -Value $tasksContent -Encoding UTF8
Write-Host " [OK] Configured VS Code tasks: .vscode/tasks.json" -ForegroundColor Green

# 4. Check Vault server health
Write-Host "`nTesting connection to AI Session Vault at $VaultUrl..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$VaultUrl/actuator/health" -Method Get -TimeoutSec 3 -ErrorAction Stop
    if ($health.status -eq "UP") {
        Write-Host " [SUCCESS] AI Session Vault is UP and RUNNING!" -ForegroundColor Green
    } else {
        Write-Host " [WARNING] AI Session Vault responded with status: $($health.status)" -ForegroundColor Yellow
    }
} catch {
    Write-Host " [INFO] AI Session Vault server is not currently running on $VaultUrl." -ForegroundColor Yellow
    Write-Host "        Start it with: docker compose up -d  OR  .\\mvnw.cmd spring-boot:run" -ForegroundColor Cyan
}

Write-Host "`nSetup complete! When you chat in VS Code using GitHub Copilot or Antigravity," -ForegroundColor Cyan
Write-Host "all conversations will be automatically saved to your local PostgreSQL database." -ForegroundColor Cyan
