<#
.SYNOPSIS
    Starts AI Session Vault in the background (windowless) on Windows login.
    Idempotent: exits cleanly if port 8080 is already listening.
#>

$ErrorActionPreference = "SilentlyContinue"
$RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$JarPath  = Join-Path $RepoRoot "target\ai-session-vault-0.0.1-SNAPSHOT.jar"
$LogDir   = Join-Path $RepoRoot "vault-storage"
$LogFile  = Join-Path $LogDir "vault-service.log"

if (-not (Test-Path $JarPath)) {
    exit 1
}

# 1. Exit if port 8080 is already listening
$alreadyRunning = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($alreadyRunning) {
    exit 0
}

# 2. Wait up to 60 seconds for PostgreSQL (port 5432) to be ready after machine boot
for ($i = 0; $i -lt 30; $i++) {
    $pgReady = Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue
    if ($pgReady) { break }
    Start-Sleep -Seconds 2
}

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

# 3. Launch Spring Boot fat JAR windowlessly via javaw.exe
$javaw = (Get-Command javaw.exe -ErrorAction SilentlyContinue).Source
if (-not $javaw) {
    $javaw = "javaw.exe"
}

Start-Process -FilePath $javaw `
    -ArgumentList "-jar `"$JarPath`" --logging.file.name=`"$LogFile`"" `
    -WorkingDirectory $RepoRoot `
    -WindowStyle Hidden
