[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Arguments
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    throw 'Python 3 is required to run zsjos-db.'
}

& $python.Source (Join-Path $root 'script/sql/mysql/tools/zsjos_db.py') @Arguments
exit $LASTEXITCODE
