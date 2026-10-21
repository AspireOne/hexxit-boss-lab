$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
# Avoid staging unrelated work or changing a partially staged file.
$staged = @(git diff --cached --name-only --diff-filter=ACMR)
$unstaged = @(git diff --name-only)
if (@($staged | Where-Object { $_ -in $unstaged }).Count -gt 0) {
    throw 'Commit whole files: a staged file also contains unstaged changes.'
}
& .\gradlew.bat spotlessApply
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
foreach ($path in $staged) { git add -- $path }
& .\gradlew.bat check
exit $LASTEXITCODE
