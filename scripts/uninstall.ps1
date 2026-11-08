# Engine Tick Optimizer - Automated Uninstaller
param(
    [string]$GameDir = "D:\Program Files\Steam\steamapps\common\ProjectZomboid",
    [switch]$RestoreBackup
)

$ErrorActionPreference = "Stop"

Write-Host "===================================================" -ForegroundColor Yellow
Write-Host " Engine Tick Optimizer (ETO) - Uninstaller" -ForegroundColor Yellow
Write-Host "===================================================" -ForegroundColor Yellow

if (-not (Test-Path $GameDir)) {
    Write-Error "Project Zomboid directory not found at: $GameDir"
}

$jsonPath = Join-Path $GameDir "ProjectZomboid64.json"
$installedJar = Join-Path $GameDir "engine-tick-optimizer.jar"
$backupPath = Join-Path $GameDir "ProjectZomboid64.json.eto-backup"

# 1. Remove JAR
if (Test-Path $installedJar) {
    Remove-Item -Path $installedJar -Force
    Write-Host "Removed JAR: $installedJar" -ForegroundColor Green
}

# 2. Revert ProjectZomboid64.json
if ($RestoreBackup -and (Test-Path $backupPath)) {
    Copy-Item -Path $backupPath -Destination $jsonPath -Force
    Write-Host "Restored original ProjectZomboid64.json from backup." -ForegroundColor Green
} elseif (Test-Path $jsonPath) {
    $jsonContent = Get-Content -Path $jsonPath -Raw | ConvertFrom-Json
    $jarName = "engine-tick-optimizer.jar"
    
    $filteredCp = @($jsonContent.classpath | Where-Object { $_ -ne $jarName })
    $jsonContent.classpath = $filteredCp
    
    $jsonFormatted = $jsonContent | ConvertTo-Json -Depth 10
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($jsonPath, $jsonFormatted, $utf8NoBom)
    Write-Host "Cleaned up classpath from ProjectZomboid64.json." -ForegroundColor Green
}

Write-Host "`n[SUCCESS] Engine Tick Optimizer has been cleanly uninstalled." -ForegroundColor Cyan
