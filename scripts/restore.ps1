# AI Session Vault - Database Restore Script (PowerShell)
param(
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "ai_session_vault",
    [string]$DbUser = "vault_user",
    [string]$DbPassword = $env:SPRING_DATASOURCE_PASSWORD
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Error "Backup file does not exist: $BackupFile"
}

if (-not $DbPassword) {
    $DbPassword = "vault_password"
}

$env:PGPASSWORD = $DbPassword

try {
    Write-Host "Restoring database '$DbName' from '$BackupFile'..."
    if (Get-Command psql -ErrorAction SilentlyContinue) {
        psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $BackupFile
    } else {
        Get-Content $BackupFile | docker exec -i ai-session-vault-db psql -U $DbUser -d $DbName
    }
    Write-Host "Database restore completed successfully."
} finally {
    $env:PGPASSWORD = $null
}
