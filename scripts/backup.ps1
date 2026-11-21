# AI Session Vault - Automated Database Backup Script (PowerShell)
param(
    [string]$BackupDir = "./backups",
    [int]$RetentionDays = 14,
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "ai_session_vault",
    [string]$DbUser = "vault_user",
    [string]$DbPassword = $env:SPRING_DATASOURCE_PASSWORD
)

$ErrorActionPreference = "Stop"

if (-not $DbPassword) {
    $DbPassword = "vault_password"
}

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupFile = Join-Path $BackupDir "vault_backup_$Timestamp.sql"
$CompressedFile = "$BackupFile.gz"

Write-Host "[$Timestamp] Starting backup of database '$DbName'..."

$env:PGPASSWORD = $DbPassword

try {
    # Check if pg_dump is available or run via docker
    if (Get-Command pg_dump -ErrorAction SilentlyContinue) {
        pg_dump -h $DbHost -p $DbPort -U $DbUser -d $DbName --format=plain --no-owner --no-privileges -f $BackupFile
    } else {
        docker exec ai-session-vault-db pg_dump -U $DbUser -d $DbName --format=plain --no-owner --no-privileges > $BackupFile
    }

    Write-Host "Backup completed: $BackupFile"

    # Rotation: Clean up older backups
    $Cutoff = (Get-Date).AddDays(-$RetentionDays)
    Get-ChildItem -Path $BackupDir -Filter "vault_backup_*.sql*" | Where-Object { $_.LastWriteTime -lt $Cutoff } | Remove-Item -Force
    Write-Host "Pruned backups older than $RetentionDays days."
} finally {
    $env:PGPASSWORD = $null
}
