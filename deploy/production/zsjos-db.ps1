[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Arguments
)

$ErrorActionPreference = 'Stop'
$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = if ($env:ZSJOS_ENV_FILE) { $env:ZSJOS_ENV_FILE } else { Join-Path $deployDir '.env' }
$composeFile = Join-Path $deployDir 'compose.database.yml'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "Missing $envFile; create it from .env.example and create the referenced secret files."
}

& docker compose --env-file $envFile -f $composeFile run --rm db-migrator @Arguments
exit $LASTEXITCODE
