# Engine Tick Optimizer - Interactive Community Installer
[CmdletBinding()]
param(
    [string]$GameDir = "D:\Program Files\Steam\steamapps\common\ProjectZomboid",
    [string]$Mode,
    [string]$Preset,
    [switch]$NonInteractive
)

$ErrorActionPreference = "Stop"

$scriptDir = $PSScriptRoot
$projectRoot = Split-Path $scriptDir -Parent
$presetsDir = Join-Path $projectRoot "presets"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "      PROJECT ZOMBOID: ENGINE TICK OPTIMIZER (ETO)          " -ForegroundColor Cyan
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

# 3. Detect PZ_Optimization
$pzoptDetected = (Test-Path (Join-Path $GameDir "pzopt-installed.txt")) -or (Test-Path (Join-Path $GameDir "media\lua\client\pzopt"))
Write-Host "`n--- Environment Scan ---" -ForegroundColor Yellow
Write-Host "Game Location       : $GameDir" -ForegroundColor White
if ($pzoptDetected) {
    Write-Host "PZ_Optimization     : DETECTED" -ForegroundColor Green
} else {
    Write-Host "PZ_Optimization     : Not detected (Stock / Other Mods)" -ForegroundColor Gray
}

# 4. Mode Selection
if (-not $Mode -and -not $NonInteractive) {
    Write-Host "`n[Step 1 of 2] Select Operating Mode:" -ForegroundColor Yellow
    Write-Host "  [1] Standalone Full Engine (Recommended for Vanilla / Standard Mods)" -ForegroundColor White
    Write-Host "      -> Activates all 4 systems: FBO VRAM caching, Zombie distance LOD," -ForegroundColor Gray
    Write-Host "         Crowd pathfinding rate limiter, and Skyscraper vertical pager." -ForegroundColor Gray
    Write-Host "  [2] PZ_Optimization Companion Mode $(if ($pzoptDetected) { '(Recommended for your setup)' } else { '' })" -ForegroundColor White
    Write-Host "      -> Focuses on the Build 42 FBO VRAM manager and skyscraper culling," -ForegroundColor Gray
    Write-Host "         preserving 100% of pzopt's classes and integrity." -ForegroundColor Gray
    
    $defaultModeChoice = if ($pzoptDetected) { "2" } else { "1" }
    $modeChoice = Read-Host "`nChoose Mode [1-2] (Default: $defaultModeChoice)"
    if ([string]::IsNullOrWhiteSpace($modeChoice)) { $modeChoice = $defaultModeChoice }
    $Mode = if ($modeChoice -eq "2") { "companion" } else { "standalone" }
} elseif (-not $Mode) {
    $Mode = if ($pzoptDetected) { "companion" } else { "standalone" }
}

# 5. Preset Selection
if (-not $Preset -and -not $NonInteractive) {
    Write-Host "`n[Step 2 of 2] Select Hardware Performance Preset:" -ForegroundColor Yellow
    Write-Host "  [1] Low-End / APU Laptop (AMD Ryzen Radeon / Intel Iris Xe / 8-16 GB Shared RAM)" -ForegroundColor White
    Write-Host "      -> Strict FBO cap (160), ground floor immunity, 4096m JVM heap," -ForegroundColor Gray
    Write-Host "         disables experimental horde batching on APUs." -ForegroundColor Gray
    Write-Host "  [2] Balanced / Gaming Laptop & Mid Desktop (GTX/RTX discrete GPU 4-6 GB, 16 GB RAM)" -ForegroundColor White
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
Write-Host "Selected Mode       : $Mode" -ForegroundColor White
Write-Host "Hardware Preset     : $($presetData.name)" -ForegroundColor White
Write-Host "Preset Description  : $($presetData.description)" -ForegroundColor Gray

# 6. Locate Pre-Built Target JAR
$sourceJarName = if ($Mode -eq "companion") { "engine-tick-optimizer-companion.jar" } else { "engine-tick-optimizer-standalone.jar" }
$sourceJarPath = Join-Path $projectRoot "bin\$sourceJarName"
if (-not (Test-Path $sourceJarPath)) {
    $sourceJarPath = Join-Path $projectRoot "target\$sourceJarName"
}

if (-not (Test-Path $sourceJarPath)) {
    Write-Error "Pre-built binary '$sourceJarName' was not found in the 'bin\' folder.`nPlease make sure the mod archive was extracted completely."
}

# 7. Copy JAR to Game Root
$installedJar = Join-Path $GameDir "engine-tick-optimizer.jar"
Write-Host "Deploying optimizer JAR to game root: $installedJar" -ForegroundColor Green
Copy-Item -Path $sourceJarPath -Destination $installedJar -Force

# 8. Configure ProjectZomboid64.json
Write-Host "Updating ProjectZomboid64.json with preset parameters..." -ForegroundColor Green
$json = Get-Content -Path $jsonPath -Raw | ConvertFrom-Json

# Set Classpath preserving existing launcher entries (e.g. pzopt/aot/pzopt.jar)
$currentCp = [System.Collections.ArrayList]@($json.classpath | Where-Object { $_ -ne "engine-tick-optimizer.jar" })
if ($Mode -eq "companion") {
    $pzJarIndex = $currentCp.IndexOf("projectzomboid.jar")
    if ($pzJarIndex -ge 0) {
        $currentCp.Insert($pzJarIndex, "engine-tick-optimizer.jar")
    } else {
        [void]$currentCp.Add("engine-tick-optimizer.jar")
    }
} else {
    $currentCp.Insert(0, "engine-tick-optimizer.jar")
}
$json.classpath = $currentCp

# Filter out old flags and apply preset JVM arguments
$cleanedVmArgs = [System.Collections.ArrayList]@($json.vmArgs | Where-Object {
    $_ -notlike "-Xmx*" -and
    $_ -notlike "-XX:MaxDirectMemorySize=*" -and
    $_ -notlike "-Dcom.pzoptimizer.*" -and
    $_ -notlike "*-javaagent:*" -and
    $_ -notlike "-Dpzopt.heap=*"
})

foreach ($arg in $presetData.vmArgs) {
    [void]$cleanedVmArgs.Add($arg)
}
$json.vmArgs = $cleanedVmArgs

# Write strictly UTF-8 WITHOUT BOM
$jsonText = $json | ConvertTo-Json -Depth 10
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($jsonPath, $jsonText, $utf8NoBom)

# 9. APU Safeguard for PZ_Optimization (Fix black zombies & VRAM pressure on APUs)
if ($Mode -eq "companion" -and $Preset -eq "low-end-apu") {
    $pzoptIniPath = Join-Path ([Environment]::GetFolderPath("UserProfile")) "Zomboid\pzopt\options.ini"
    if (Test-Path $pzoptIniPath) {
        $pzoptIni = [System.Collections.ArrayList]@(Get-Content -Path $pzoptIniPath)
        
        # hordeBatch=off (fixes black unanimated zombie models on AMD drivers)
        $hordeIdx = -1
        for ($i = 0; $i -lt $pzoptIni.Count; $i++) {
            if ($pzoptIni[$i] -like "hordeBatch=*") { $hordeIdx = $i; break }
        }
        if ($hordeIdx -ge 0) { $pzoptIni[$hordeIdx] = "hordeBatch=off" } else { [void]$pzoptIni.Add("hordeBatch=off") }

        # gcHeap=3072m (clamps pzopt heap calculator to 3 GB)
        $gcIdx = -1
        for ($i = 0; $i -lt $pzoptIni.Count; $i++) {
            if ($pzoptIni[$i] -like "gcHeap=*") { $gcIdx = $i; break }
        }
        if ($gcIdx -ge 0) { $pzoptIni[$gcIdx] = "gcHeap=3072m" } else { [void]$pzoptIni.Add("gcHeap=3072m") }

        # chunkGridWidth=7 (reduces active chunk FBOs from 81 to 49, saving ~600 MB VRAM)
        $gridIdx = -1
        for ($i = 0; $i -lt $pzoptIni.Count; $i++) {
            if ($pzoptIni[$i] -like "chunkGridWidth=*") { $gridIdx = $i; break }
        }
        if ($gridIdx -ge 0) {
            $currentGrid = [int]($pzoptIni[$gridIdx] -replace "chunkGridWidth=", "")
            if ($currentGrid -gt 7) { $pzoptIni[$gridIdx] = "chunkGridWidth=7" }
        }

        # Anti-Stutter Safeguards for Door/Building transitions on APUs:
        # workers=4 (enables multithreaded lighting and chunk bakes)
        $workIdx = -1
        for ($i = 0; $i -lt $pzoptIni.Count; $i++) {
            if ($pzoptIni[$i] -like "workers=*") { $workIdx = $i; break }
        }
        if ($workIdx -ge 0) { $pzoptIni[$workIdx] = "workers=4" } else { [void]$pzoptIni.Add("workers=4") }

        # bakeMaxWaitCutaway=8 (relaxes building-entry cutaway rebaking from 3 frames to 8 frames)
        $bakeIdx = -1
        for ($i = 0; $i -lt $pzoptIni.Count; $i++) {
            if ($pzoptIni[$i] -like "bakeMaxWaitCutaway=*") { $bakeIdx = $i; break }
        }
        if ($bakeIdx -ge 0) { $pzoptIni[$bakeIdx] = "bakeMaxWaitCutaway=8" } else { [void]$pzoptIni.Add("bakeMaxWaitCutaway=8") }

        # godRaysDoorDustPct=0 (disables heavy volumetric door dust raymarching shader on door toggle)
        $dustIdx = -1
        for ($i = 0; $i -lt $pzoptIni.Count; $i++) {
            if ($pzoptIni[$i] -like "godRaysDoorDustPct=*") { $dustIdx = $i; break }
        }
        if ($dustIdx -ge 0) { $pzoptIni[$dustIdx] = "godRaysDoorDustPct=0" } else { [void]$pzoptIni.Add("godRaysDoorDustPct=0") }

        # pixelLight=false (avoids indoor/outdoor per-pixel light recomputation stalls)
        $pixIdx = -1
        for ($i = 0; $i -lt $pzoptIni.Count; $i++) {
            if ($pzoptIni[$i] -like "pixelLight=*") { $pixIdx = $i; break }
        }
        if ($pixIdx -ge 0) { $pzoptIni[$pixIdx] = "pixelLight=false" } else { [void]$pzoptIni.Add("pixelLight=false") }

        [System.IO.File]::WriteAllLines($pzoptIniPath, $pzoptIni)
        Write-Host "[+] Safeguards applied to pzopt/options.ini: hordeBatch=off, gcHeap=3072m, workers=4, bakeMaxWaitCutaway=8, godRaysDoorDustPct=0, pixelLight=false." -ForegroundColor Green
    }

    # Also tune vanilla options.ini for Low-End APU
    $pzOptionsPath = Join-Path ([Environment]::GetFolderPath("UserProfile")) "Zomboid\options.ini"
    if (Test-Path $pzOptionsPath) {
        $pzOptions = [System.Collections.ArrayList]@(Get-Content -Path $pzOptionsPath)
        for ($i = 0; $i -lt $pzOptions.Count; $i++) {
            if ($pzOptions[$i] -like "3DGroundItem=*") { $pzOptions[$i] = "3DGroundItem=false" }
            if ($pzOptions[$i] -like "maxVehicleTextureSize=*") { $pzOptions[$i] = "maxVehicleTextureSize=1" }
        }
        [System.IO.File]::WriteAllLines($pzOptionsPath, $pzOptions)
        Write-Host "[+] Applied APU texture tuning to Zomboid/options.ini: 3DGroundItem=false, maxVehicleTextureSize=1." -ForegroundColor Green
    }
}

# 10. Summary and Recommendations Checklist
Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host " [SUCCESS] Engine Tick Optimizer Installed Successfully!     " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "`nRecommended In-Game Settings for $($presetData.name):" -ForegroundColor White
Write-Host "  - Texture Quality   : $(if ($presetData.inGameRecommendations.texture2x) { '2x (Double-Sized Textures)' } else { '1x Textures (Recommended for APU / RAM Savings)' })" -ForegroundColor Gray
Write-Host "  - 3D Ground Items   : $(if ($presetData.inGameRecommendations.'3DGroundItem') { 'Enabled' } else { 'Disabled (Recommended for APU)' })" -ForegroundColor Gray
Write-Host "  - Vehicle Textures  : $(if ($presetData.inGameRecommendations.maxVehicleTextureSize -eq 1) { 'Medium / 1024' } else { 'High / 2048' })" -ForegroundColor Gray
if ($presetData.inGameRecommendations.chunkGridWidth) {
    Write-Host "  - Chunk Grid Width  : $($presetData.inGameRecommendations.chunkGridWidth)" -ForegroundColor Gray
}

Write-Host "`nYou can launch Project Zomboid via Steam now.`n" -ForegroundColor Green
