param(
    [string]$Mode = "all"
)

# Engine Tick Optimizer - Build Script
$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$srcDir = Join-Path $projectRoot "src\main\java"
$manifestPath = Join-Path $projectRoot "src\main\resources\META-INF\MANIFEST.MF"
$targetDir = Join-Path $projectRoot "target"
$classesBaseDir = Join-Path $targetDir "classes-base"
$asmJar = Join-Path $projectRoot "lib\asm.jar"
$pzDir = "D:\Program Files\Steam\steamapps\common\ProjectZomboid"

Write-Host "=== Building Engine Tick Optimizer (Mode: $Mode) ===" -ForegroundColor Cyan

# Clean and recreate base classes dir
if (Test-Path $classesBaseDir) {
    Remove-Item -Path $classesBaseDir -Recurse -Force
}
New-Item -ItemType Directory -Path $classesBaseDir -Force | Out-Null

# Unpack ASM dependency into base classes dir for fat jar
Write-Host "Unpacking ASM runtime into target classes..." -ForegroundColor Green
Set-Location $classesBaseDir
& jar -xf $asmJar
Set-Location $projectRoot

# Collect all java files
$javaFiles = Get-ChildItem -Path $srcDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
if ($javaFiles.Count -eq 0) {
    Write-Error "No Java source files found!"
}

Write-Host "Compiling $($javaFiles.Count) Java sources with javac..." -ForegroundColor Green
$javacArgs = @(
    "--add-modules", "java.instrument",
    "--add-reads", "org.objectweb.asm=java.instrument",
    "-cp", $asmJar,
    "-d", $classesBaseDir,
    "-source", "21",
    "-target", "21"
) + $javaFiles

& javac @javacArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed!"
}

function Build-Variant($variantName, $outputJarName) {
    Write-Host "`n--- Packaging Variant: $variantName ($outputJarName) ---" -ForegroundColor Yellow
    $variantClassesDir = Join-Path $targetDir "classes-$variantName"
    if (Test-Path $variantClassesDir) { Remove-Item -Path $variantClassesDir -Recurse -Force }
    Copy-Item -Path $classesBaseDir -Destination $variantClassesDir -Recurse

    $patcherCp = "$variantClassesDir;$asmJar"
    & java -cp $patcherCp com.pzoptimizer.transformer.OfflinePatcher $pzDir $variantClassesDir $variantName
    if ($LASTEXITCODE -ne 0) { Write-Error "OfflinePatcher failed for $variantName!" }

    $outJar = Join-Path $targetDir $outputJarName
    $jarArgs = @("cfm", $outJar, $manifestPath, "-C", $variantClassesDir, ".")
    & jar @jarArgs
    if ($LASTEXITCODE -ne 0) { Write-Error "Packaging $outputJarName failed!" }
    Write-Host "Created: $outJar" -ForegroundColor Green
}

if ($Mode -eq "all" -or $Mode -eq "standalone") {
    Build-Variant "standalone" "engine-tick-optimizer-standalone.jar"
}
if ($Mode -eq "all" -or $Mode -eq "companion") {
    Build-Variant "companion" "engine-tick-optimizer-companion.jar"
    Copy-Item (Join-Path $targetDir "engine-tick-optimizer-companion.jar") (Join-Path $targetDir "engine-tick-optimizer.jar") -Force
}

# Sync to shipping bin/ directory
$binDir = Join-Path $projectRoot "bin"
if (-not (Test-Path $binDir)) { New-Item -ItemType Directory -Path $binDir -Force | Out-Null }
if (Test-Path (Join-Path $targetDir "engine-tick-optimizer-standalone.jar")) {
    Copy-Item (Join-Path $targetDir "engine-tick-optimizer-standalone.jar") "$binDir\engine-tick-optimizer-standalone.jar" -Force
}
if (Test-Path (Join-Path $targetDir "engine-tick-optimizer-companion.jar")) {
    Copy-Item (Join-Path $targetDir "engine-tick-optimizer-companion.jar") "$binDir\engine-tick-optimizer-companion.jar" -Force
}
Write-Host "Updated shipping binaries in: $binDir" -ForegroundColor Green

Write-Host "`n=== Build Completed Successfully ===" -ForegroundColor Cyan
