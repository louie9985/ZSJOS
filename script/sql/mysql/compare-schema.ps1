[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $SourceHost,
    [Parameter(Mandatory = $true)] [string] $SourceDatabase,
    [Parameter(Mandatory = $true)] [string] $TargetHost,
    [Parameter(Mandatory = $true)] [string] $TargetDatabase,
    [string] $User = 'root'
)

$ErrorActionPreference = 'Stop'
$dump = Get-Command mysqldump -ErrorAction SilentlyContinue
if (-not $dump) {
    throw 'mysqldump was not found. Install the MySQL client tools before running a schema comparison.'
}

$sourceFile = [System.IO.Path]::GetTempFileName()
$targetFile = [System.IO.Path]::GetTempFileName()
try {
    & $dump.Source --host=$SourceHost --user=$User --no-data --skip-comments --skip-add-drop-table --skip-add-locks --skip-locks --skip-set-charset $SourceDatabase *> $sourceFile
    if ($LASTEXITCODE -ne 0) { throw "mysqldump failed for source database $SourceDatabase" }
    & $dump.Source --host=$TargetHost --user=$User --no-data --skip-comments --skip-add-drop-table --skip-add-locks --skip-locks --skip-set-charset $TargetDatabase *> $targetFile
    if ($LASTEXITCODE -ne 0) { throw "mysqldump failed for target database $TargetDatabase" }

    $source = (Get-Content -Raw $sourceFile) -replace '(?m)^\s*AUTO_INCREMENT=\d+\s*', ''
    $target = (Get-Content -Raw $targetFile) -replace '(?m)^\s*AUTO_INCREMENT=\d+\s*', ''
    if ($source -ceq $target) {
        Write-Output 'PASS: normalized schemas are identical.'
        exit 0
    }

    Write-Output 'FAIL: normalized schemas differ.'
    Compare-Object ($source -split "`r?`n") ($target -split "`r?`n") -SideIndicator '<source / target>'
    exit 1
}
finally {
    Remove-Item -LiteralPath $sourceFile, $targetFile -Force -ErrorAction SilentlyContinue
}
