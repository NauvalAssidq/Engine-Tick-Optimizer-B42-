# Engine Tick Optimizer - Interactive Community Installer
[CmdletBinding()]
param(
    [string]$GameDir = "D:\Program Files\Steam\steamapps\common\ProjectZomboid",
    [string]$Preset,
    [switch]$NonInteractive
)

$ErrorActionPreference = "Stop"

$scriptDir = $PSScriptRoot
$projectRoot = Split-Path $scriptDir -Parent
$presetsDir = Join-Path $projectRoot "presets"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "      PROJECT ZOMBOID: ENGINE TICK OPTIMIZER (ETO)          " -ForegroundColor Cyan
Write-Host "      Architecture: Dynamic Java Agent (Build 42.22+)       " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Locate and Validate Game Directory
if (-not (Test-Path $GameDir) -or -not (Test-Path (Join-Path $GameDir "projectzomboid.jar"))) {
    Write-Host "[!] Default game directory not found at: $GameDir" -ForegroundColor Yellow
    $GameDir = Read-Host "Please enter the full path to your Project Zomboid directory"
    if (-not (Test-Path $GameDir) -or -not (Test-Path (Join-Path $GameDir "projectzomboid.jar"))) {
        Write-Error "Invalid game directory. projectzomboid.jar was not found."
    }
}
$jsonPath = Join-Path $GameDir "ProjectZomboid64.json"
if (-not (Test-Path $jsonPath)) {
    Write-Error "ProjectZomboid64.json was not found in: $GameDir"
}

# 2. Backup ProjectZomboid64.json if not already backed up
$backupPath = Join-Path $GameDir "ProjectZomboid64.json.eto-backup"
if (-not (Test-Path $backupPath)) {
    Copy-Item -Path $jsonPath -Destination $backupPath -Force
    Write-Host "[+] Backed up original launcher JSON to: ProjectZomboid64.json.eto-backup" -ForegroundColor DarkGray
}

# 3. Clean up loose zombie classes from older offline patchers/pzopt (prevents B42.22 signed JAR mismatch)
$looseZombieDir = Join-Path $GameDir "zombie"
if (Test-Path $looseZombieDir) {
    Write-Host "[!] Removing obsolete loose zombie/ directory to prevent signer mismatch..." -ForegroundColor Yellow
    Remove-Item -Path $looseZombieDir -Recurse -Force
}

# 4. Preset Selection
if (-not $Preset -and -not $NonInteractive) {
    Write-Host "`nSelect Hardware Performance Preset:" -ForegroundColor Yellow
    Write-Host "  [1] Low-End / APU Laptop (AMD Ryzen Radeon / Intel Iris Xe / 8-16 GB Shared RAM)" -ForegroundColor White
    Write-Host "      -> Standard vanilla heap (6 GB), strict FBO cap (160), ground floor immunity." -ForegroundColor Gray
    Write-Host "  [2] Balanced / Mid-Range (GTX/RTX discrete GPU 4-6 GB, 16 GB RAM)" -ForegroundColor White
    Write-Host "      -> Medium FBO pool (220), 4-floor culling, 5120m JVM heap." -ForegroundColor Gray
    Write-Host "  [3] High-End Rig (RTX 3070/4070+, 8 GB+ Dedicated VRAM, 32 GB RAM)" -ForegroundColor White
    Write-Host "      -> Large FBO pool (350), relaxed retention, 6144m JVM heap." -ForegroundColor Gray

    $presetChoice = Read-Host "`nChoose Preset [1-3] (Default: 1)"
    if ([string]::IsNullOrWhiteSpace($presetChoice)) { $presetChoice = "1" }
    $Preset = switch ($presetChoice) {
        "2" { "balanced" }
        "3" { "high-end" }
        Default { "low-end-apu" }
    }
} elseif (-not $Preset) {
    $Preset = "low-end-apu"
}

# Load Preset File
$presetFile = Join-Path $presetsDir "$Preset.json"
if (-not (Test-Path $presetFile)) {
    Write-Error "Preset file not found: $presetFile"
}
$presetData = Get-Content -Path $presetFile -Raw | ConvertFrom-Json

Write-Host "`n--- Installation Configuration ---" -ForegroundColor Yellow
Write-Host "Game Location       : $GameDir" -ForegroundColor White
Write-Host "Target Mode         : Standalone Dynamic Java Agent (B42.22+ signed jar safe)" -ForegroundColor White
Write-Host "Hardware Preset     : $($presetData.name)" -ForegroundColor White
Write-Host "Preset Description  : $($presetData.description)" -ForegroundColor Gray

# 5. Locate Pre-Built Target JAR
$sourceJarPath = Join-Path $projectRoot "bin\engine-tick-optimizer.jar"
if (-not (Test-Path $sourceJarPath)) {
    $sourceJarPath = Join-Path $projectRoot "target\engine-tick-optimizer.jar"
}
if (-not (Test-Path $sourceJarPath)) {
    Write-Error "Pre-built binary 'engine-tick-optimizer.jar' was not found in 'bin\'. Run build.ps1 first."
}

# 6. Copy JAR to Game Root
$installedJar = Join-Path $GameDir "engine-tick-optimizer.jar"
Write-Host "Deploying optimizer Java Agent to game root: $installedJar" -ForegroundColor Green
Copy-Item -Path $sourceJarPath -Destination $installedJar -Force

# 7. Configure ProjectZomboid64.json
Write-Host "Configuring ProjectZomboid64.json with dynamic agent hooks..." -ForegroundColor Green
$json = Get-Content -Path $jsonPath -Raw | ConvertFrom-Json

# Reset Classpath to pure projectzomboid.jar (removes loose overrides that break signed jars)
$json.classpath = @("projectzomboid.jar")

# Filter out old / conflicting flags and apply clean JVM arguments
$cleanedVmArgs = [System.Collections.ArrayList]@($json.vmArgs | Where-Object {
    $_ -notlike "-Xms*" -and
    $_ -notlike "-Xmx*" -and
    $_ -notlike "-XX:MaxDirectMemorySize=*" -and
    $_ -notlike "-Dcom.pzoptimizer.*" -and
    $_ -notlike "*-javaagent:*" -and
    $_ -notlike "-Dpzopt.*" -and
    $_ -notlike "-XX:PerMethodTrapLimit=*" -and
    $_ -notlike "-XX:PerBytecodeTrapLimit=*" -and
    $_ -notlike "-XX:-UseTypeSpeculation*"
})

# Register Java Agent
[void]$cleanedVmArgs.Add("-javaagent:engine-tick-optimizer.jar")

# Append preset arguments
foreach ($arg in $presetData.vmArgs) {
    [void]$cleanedVmArgs.Add($arg)
}
$json.vmArgs = $cleanedVmArgs

# Reset Windows OS vmArgs to empty (removes obsolete pzopt GC parameters that caused microstutters)
if ($json.windows) {
    if ($json.windows.'10.0.17134') {
        $json.windows.'10.0.17134'.vmArgs = @()
    }
    if ($json.windows.'6.1') {
        $json.windows.'6.1'.vmArgs = @()
    }
}

# Write strictly UTF-8 WITHOUT BOM
$jsonText = $json | ConvertTo-Json -Depth 10
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($jsonPath, $jsonText, $utf8NoBom)

# 8. Apply In-Game Options Tuning
$pzOptionsPath = Join-Path ([Environment]::GetFolderPath("UserProfile")) "Zomboid\options.ini"
if (Test-Path $pzOptionsPath) {
    $pzOptions = [System.Collections.ArrayList]@(Get-Content -Path $pzOptionsPath)
    for ($i = 0; $i -lt $pzOptions.Count; $i++) {
        if ($Preset -eq "low-end-apu") {
            if ($pzOptions[$i] -like "3DGroundItem=*") { $pzOptions[$i] = "3DGroundItem=false" }
            if ($pzOptions[$i] -like "maxVehicleTextureSize=*") { $pzOptions[$i] = "maxVehicleTextureSize=1" }
        }
    }
    [System.IO.File]::WriteAllLines($pzOptionsPath, $pzOptions)
    Write-Host "[+] Applied hardware tuning to Zomboid\options.ini." -ForegroundColor Green
}

# 9. Summary
Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host " [SUCCESS] Engine Tick Optimizer Installed Successfully!     " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "`nLaunch Project Zomboid normally via Steam or ProjectZomboid64.exe.`n" -ForegroundColor Green
