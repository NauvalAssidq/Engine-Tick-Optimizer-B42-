param(
    [string]$Mode = "agent"
)

# Engine Tick Optimizer - Build Script
$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$srcDir = Join-Path $projectRoot "src\main\java"
$manifestPath = Join-Path $projectRoot "src\main\resources\META-INF\MANIFEST.MF"
$targetDir = Join-Path $projectRoot "target"
$classesBaseDir = Join-Path $targetDir "classes-base"
$asmJar = Join-Path $projectRoot "lib\asm.jar"
$binDir = Join-Path $projectRoot "bin"

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
# Remove module-info.class so JVM does not treat this as a modular JAR with split-package violations
Remove-Item -Path (Join-Path $classesBaseDir "module-info.class") -Force -ErrorAction SilentlyContinue
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

# Package pure Java Agent fat jar
$outJar = Join-Path $targetDir "engine-tick-optimizer.jar"
$jarArgs = @("cfm", $outJar, $manifestPath, "-C", $classesBaseDir, ".")
& jar @jarArgs
if ($LASTEXITCODE -ne 0) { Write-Error "Packaging engine-tick-optimizer.jar failed!" }
Write-Host "Created: $outJar" -ForegroundColor Green

# Sync to shipping bin/ directory
if (-not (Test-Path $binDir)) { New-Item -ItemType Directory -Path $binDir -Force | Out-Null }
Copy-Item $outJar (Join-Path $binDir "engine-tick-optimizer.jar") -Force
# For backwards compatibility with older scripts:
Copy-Item $outJar (Join-Path $binDir "engine-tick-optimizer-standalone.jar") -Force
Write-Host "Updated shipping binary: $binDir\engine-tick-optimizer.jar" -ForegroundColor Green

Write-Host "`n=== Build Completed Successfully ===" -ForegroundColor Cyan
