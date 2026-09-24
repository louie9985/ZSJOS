# Encoding: UTF-8
[CmdletBinding()]
param([switch]$SkipTypecheck, [switch]$RequireFullCoverage)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $repoRoot 'output'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$manifestPath = Join-Path $repoRoot 'backend/yudao-module-zsjos/src/test/resources/advanced-filter-contract.json'
$contract = Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json
if ($RequireFullCoverage -and $contract.pending.Count -gt 0) {
    $contract.pending | ForEach-Object { Write-Host "Pending: $_" }
    throw 'Full inventory acceptance is blocked by the gaps listed above.'
}

function Invoke-ContractCheck {
    param([string]$Name, [string]$Directory, [string]$Program, [string[]]$Arguments)
    $log = Join-Path $logDirectory "advanced-filter-contract-$Name.log"
    Write-Host "Checking $Name ..."
    Push-Location $Directory
    try {
        Get-Command $Program -ErrorAction Stop | Out-Null
        $previousPreference = $ErrorActionPreference
        try {
            # Windows PowerShell wraps native stderr warnings as ErrorRecords; exit status is authoritative.
            $ErrorActionPreference = 'Continue'
            & $Program @Arguments *> $log
            $exitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $previousPreference }
        if ($exitCode -ne 0) {
            Get-Content -LiteralPath $log -Tail 60 | Write-Host
            throw "$Name failed ($exitCode). Log: $log"
        }
        Write-Host "PASS $Name - $log"
    } finally { Pop-Location }
}

# Keep large JVM/TypeScript checks sequential and keep temporary test files off the system drive.
Invoke-ContractCheck -Name 'backend' -Directory (Join-Path $repoRoot 'backend') -Program 'mvn' -Arguments @(
    '-f', 'pom.xml', '-pl', 'yudao-module-zsjos', '-am', "-Djava.io.tmpdir=$logDirectory",
    '-Dtest=AdvancedFilter*Test', '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
Invoke-ContractCheck -Name 'consumers' -Directory (Join-Path $repoRoot 'frontend/workbench') -Program 'npm' -Arguments @(
    'test', '--', '--run', 'src/components/AdvancedFilterContract.test.ts',
    'src/components/AdvancedFilter.test.ts', 'src/components/AdvancedFilterSources.test.ts')
if (-not $SkipTypecheck) {
    Invoke-ContractCheck -Name 'admin-types' -Directory (Join-Path $repoRoot 'frontend/admin') -Program 'pnpm' -Arguments @('ts:check')
    Invoke-ContractCheck -Name 'workbench-types' -Directory (Join-Path $repoRoot 'frontend/workbench') -Program 'npm' -Arguments @('run', 'typecheck')
}

if ($contract.pending.Count -gt 0) {
    Write-Host 'Known inventory gaps (not covered as completed functionality):'
    $contract.pending | ForEach-Object { Write-Host "  - $_" }
}
Write-Host 'PASS: registered-page regression contract. This is not deployed HTTP/MySQL acceptance.'
