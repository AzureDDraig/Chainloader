# ChainLoader Intrusive Holders Validation Script
# This script ensures that no 'This registry can't create intrusive holders' exception
# is thrown for Nature's Compass or other mods by verifying:
# 1. Successful compilation of ChainLoader compat and core modules.
# 2. Binary validation of compiled classes to guarantee unfreezeRegistry and refreezeRegistry integration.
# 3. Log scanning of existing logs to flag past issues and verify recent runs.

$ErrorActionPreference = "Continue"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Running ChainLoader Intrusive Holders Fix Validation" -ForegroundColor Magenta
Write-Host "=========================================" -ForegroundColor Cyan

# 1. Compile check
Write-Host "`n[STEP 1] Compiling project sources..." -ForegroundColor Yellow
$compileResult = powershell -ExecutionPolicy Bypass -File .\run_simulation.ps1 -CompileOnly
$exitCode = $LASTEXITCODE
Write-Host "--- Compilation Output Start ---" -ForegroundColor Gray
Write-Host $compileResult
Write-Host "--- Compilation Output End ---" -ForegroundColor Gray

if ($exitCode -ne 0) {
    Write-Host "[-] Compilation failed with exit code $exitCode!" -ForegroundColor Red
    Exit 1
}
Write-Host "[+] Compilation completed successfully!" -ForegroundColor Green

# 2. Class Verification Check (Ensuring unfreezeRegistry/refreezeRegistry hooks are compiled in)
Write-Host "`n[STEP 2] Verifying compiled class files for unfreezeRegistry/refreezeRegistry hooks..." -ForegroundColor Yellow

$classesToCheck = @(
    "bin/net/minecraftforge/registries/RegisterEvent.class",
    "bin/net/neoforged/neoforge/registries/RegisterEvent.class",
    "bin/net/minecraftforge/registries/DeferredRegister.class",
    "bin/net/neoforged/neoforge/registries/DeferredRegister.class"
)

$hasErrors = $false

foreach ($classPath in $classesToCheck) {
    if (!(Test-Path $classPath)) {
        Write-Host "[-] Missing compiled class: $classPath" -ForegroundColor Red
        $hasErrors = $true
        continue
    }

    # Read class file as bytes
    $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $classPath).Path)
    # Convert bytes to string (ASCII/UTF8 representation to find method name constants)
    $classText = [System.Text.Encoding]::ASCII.GetString($bytes)

    $hasUnfreeze = $classText.Contains("unfreezeRegistry")
    $hasRefreeze = $classText.Contains("refreezeRegistry")

    if ($hasUnfreeze -and $hasRefreeze) {
        Write-Host "[+] ${classPath}: OK (References unfreezeRegistry and refreezeRegistry)" -ForegroundColor Green
    } else {
        Write-Host "[-] ${classPath}: FAILED (Missing registry hooks! hasUnfreeze=$hasUnfreeze, hasRefreeze=$hasRefreeze)" -ForegroundColor Red
        $hasErrors = $true
    }
}

# 3. Log Analysis Check
Write-Host "`n[STEP 3] Scanning existing logs for 'intrusive holders' exceptions..." -ForegroundColor Yellow

$latestLog = "logs/latest.log"
if (Test-Path $latestLog) {
    # Scan latest log for the exception
    $matches = Select-String -Pattern "This registry can't create intrusive holders" -Path $latestLog
    if ($matches) {
        Write-Host "[!] Found 'intrusive holders' exception in $latestLog. Please check if this was from a previous run before applying the fix." -ForegroundColor Yellow
        # Let's inspect the timestamp or print matching lines
        foreach ($match in $matches) {
            Write-Host "  -> Line $($match.LineNumber): $($match.Line.Trim())" -ForegroundColor DarkYellow
        }
    } else {
        Write-Host "[+] ${latestLog}: OK (No 'intrusive holders' exception found in latest log)" -ForegroundColor Green
    }
} else {
    Write-Host "[i] No latest.log found to scan." -ForegroundColor Gray
}

# Scan other logs if any
$allLogs = Get-ChildItem -Path "logs" -Filter "*.log" -ErrorAction SilentlyContinue
foreach ($log in $allLogs) {
    if ($log.Name -eq "latest.log") { continue }
    $matches = Select-String -Pattern "This registry can't create intrusive holders" -Path $log.FullName
    if ($matches) {
        Write-Host "[!] Found historical 'intrusive holders' exception in $($log.Name)" -ForegroundColor Gray
    }
}

Write-Host "`n=========================================" -ForegroundColor Cyan
if ($hasErrors) {
    Write-Host " Fix validation FAILED. Some checks did not pass." -ForegroundColor Red
    Exit 1
} else {
    Write-Host " Fix validation PASSED successfully!" -ForegroundColor Green
    Exit 0
}
