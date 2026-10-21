param(
    [Parameter(Position = 0)]
    [ValidateSet('setup', 'check', 'build', 'apply', 'play', 'smoke', 'reset-world', 'rollback')]
    [string]$Command = 'check'
)
$ErrorActionPreference = 'Stop'
& python (Join-Path $PSScriptRoot 'scripts\lab.py') $Command
exit $LASTEXITCODE
