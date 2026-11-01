<#
.SYNOPSIS
    Runs local quality checks (Unit Tests, Lint, and optionally Build) with automatic JDK/JBR resolution.
.PARAMETER Fast
    Runs only unit tests, skipping the slower Android Lint check.
.PARAMETER Build
    Also builds the debug APK (assembleDebug) after checks pass.
#>
param(
    [switch]$Fast,
    [switch]$Build
)

$ErrorActionPreference = "Stop"

# Resolve JDK 17+ / Android Studio JBR if JAVA_HOME is invalid or points to an incompatible version
function Resolve-JavaEnvironment {
    $needsResolution = $false
    
    if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
        $needsResolution = $true
    } else {
        # Check if javac version is below 17
        try {
            $versionOutput = & "$env:JAVA_HOME\bin\javac.exe" -version 2>&1 | Out-String
            if ($versionOutput -match "1\.8" -or $versionOutput -match "1\.7") {
                $needsResolution = $true
            }
        } catch {
            $needsResolution = $true
        }
    }

    if ($needsResolution) {
        $candidatePaths = @(
            "$env:ProgramFiles\Android\Android Studio\jbr",
            "${env:ProgramFiles(x86)}\Android\Android Studio\jbr",
            "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
        )
        $found = $false
        foreach ($path in $candidatePaths) {
            if (Test-Path "$path\bin\javac.exe") {
                $env:JAVA_HOME = $path
                Write-Host "[check.ps1] Resolved JAVA_HOME to Android Studio JBR: $path" -ForegroundColor Cyan
                $found = $true
                break
            }
        }
        if (-not $found) {
            Write-Warning "[check.ps1] Could not automatically locate Android Studio JBR. Ensure JAVA_HOME points to JDK 17+."
        }
    } else {
        Write-Host "[check.ps1] Using existing JAVA_HOME: $env:JAVA_HOME" -ForegroundColor DarkGray
    }
}

Resolve-JavaEnvironment

$gradlew = Join-Path $PSScriptRoot "..\gradlew.bat"
if (-not (Test-Path $gradlew)) {
    $gradlew = ".\gradlew.bat"
}

Write-Host "=== Running Unit Tests (testDebugUnitTest) ===" -ForegroundColor Yellow
& $gradlew testDebugUnitTest
if ($LASTEXITCODE -ne 0) {
    Write-Error "[check.ps1] Unit tests failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}
Write-Host "[OK] Unit tests passed successfully." -ForegroundColor Green

if (-not $Fast) {
    Write-Host "`n=== Running Android Lint (lintDebug) ===" -ForegroundColor Yellow
    & $gradlew lintDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[check.ps1] Lint checks failed with exit code $LASTEXITCODE."
        exit $LASTEXITCODE
    }
    Write-Host "[OK] Lint checks passed successfully." -ForegroundColor Green
}

if ($Build) {
    Write-Host "`n=== Assembling Debug APK (assembleDebug) ===" -ForegroundColor Yellow
    & $gradlew assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[check.ps1] assembleDebug failed with exit code $LASTEXITCODE."
        exit $LASTEXITCODE
    }
    Write-Host "[OK] Debug APK built successfully." -ForegroundColor Green
}

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host " All requested quality checks completed! " -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
