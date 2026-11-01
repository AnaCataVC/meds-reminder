# Configures git to use the repository's custom .githooks directory.
$ErrorActionPreference = "Stop"

Write-Host "Configuring Git hooks path to .githooks..." -ForegroundColor Cyan
git config core.hooksPath .githooks

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Custom Git hooks successfully activated (.githooks)." -ForegroundColor Green
    Write-Host "     Pre-push hook will run unit tests prior to pushing." -ForegroundColor DarkGray
} else {
    Write-Error "Failed to configure git core.hooksPath."
}
