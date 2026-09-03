<##
.SYNOPSIS
  One-command non-destructive import of existing-server SQL config and BPM assets.
.DESCRIPTION
  SQL fills only missing dictionary rows and role-menu grants. BPM assets are
  imported through the public admin API; Flowable ACT_* tables are never written.
  Existing models with the same key are skipped unless -Force is explicitly set.
#>
[CmdletBinding()]
param(
  [string]$Database = 'ruoyi-vue-pro',
  [string]$DbUser = 'root',
  [string]$DbPassword = '123456',
  [string]$MysqlContainer = 'yudao-mysql',
  [string]$ApiBase = 'http://127.0.0.1:48080/admin-api',
  [string]$AccessToken,
  [string]$TenantId = '1',
  [string]$Category = 'general-module',
  [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sql = Join-Path $repo 'script\sql\mysql\sync-existing-server-config.sql'
$manifestPath = Join-Path $repo 'script\bpm\manifest.json'

if (-not (Test-Path $sql) -or -not (Test-Path $manifestPath)) { throw '未找到同步 SQL 或 BPM manifest。' }

Write-Host '[1/3] 执行非破坏性 SQL 补齐...' -ForegroundColor Cyan
$sqlBytes = [IO.File]::ReadAllBytes($sql)
$sqlText = [Text.Encoding]::UTF8.GetString($sqlBytes)
$sqlText | docker exec -i $MysqlContainer mysql --default-character-set=utf8mb4 -u$DbUser -p$DbPassword $Database
if ($LASTEXITCODE -ne 0) { throw "SQL 执行失败，退出码 $LASTEXITCODE。" }

if ([string]::IsNullOrWhiteSpace($AccessToken)) {
  Write-Host '[2/3] 未提供 -AccessToken，已完成 SQL；跳过 BPM API 导入。' -ForegroundColor Yellow
  Write-Host '请使用管理员 token 重新运行并提供 -AccessToken。'
  exit 0
}

$headers = @{ Authorization = "Bearer $AccessToken"; 'tenant-id' = $TenantId }
$manifest = Get-Content -Raw $manifestPath | ConvertFrom-Json
$existing = Invoke-RestMethod -Method Get -Uri "$ApiBase/bpm/model/list" -Headers $headers
$existingKeys = @($existing.data | ForEach-Object { $_.key })

Write-Host '[3/3] 通过 BPM 管理 API 导入并发布缺失流程...' -ForegroundColor Cyan
foreach ($asset in $manifest.assets) {
  if (-not $Force -and $existingKeys -contains $asset.processKey) {
    Write-Host "跳过已存在模型: $($asset.processKey)" -ForegroundColor DarkGray
    continue
  }
  $assetFile = Join-Path (Join-Path $PSScriptRoot $asset.path.Split('/')[0..($asset.path.Split('/').Count-2)] -join '\') ($asset.path.Split('/')[-1])
  if (-not (Test-Path $assetFile)) { throw "资产不存在: $($asset.path)" }
  $ext = [IO.Path]::GetExtension($assetFile)
  if ($ext -eq '.json') {
    $uri = "$ApiBase/bpm/model/import?category=$([uri]::EscapeDataString($Category))"
    Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -Form @{ file = Get-Item $assetFile } | Out-Null
    $key = $asset.processKey
  } else {
    $xml = [IO.File]::ReadAllText($assetFile, [Text.Encoding]::UTF8)
    $body = @{ key=$asset.processKey; name=$asset.processKey; category=$Category; type=10; formType=20; bpmnXml=$xml; visible=$false } | ConvertTo-Json -Depth 20
    $created = Invoke-RestMethod -Method Post -Uri "$ApiBase/bpm/model/create" -Headers ($headers + @{ 'Content-Type'='application/json' }) -Body ([Text.Encoding]::UTF8.GetBytes($body))
    $key = $asset.processKey
  }
  $models = Invoke-RestMethod -Method Get -Uri "$ApiBase/bpm/model/list" -Headers $headers
  $model = @($models.data | Where-Object { $_.key -eq $key } | Select-Object -First 1)
  if ($model.Count -eq 0) { throw "导入后未找到模型: $key" }
  Invoke-RestMethod -Method Post -Uri "$ApiBase/bpm/model/deploy?id=$([uri]::EscapeDataString([string]$model.id))" -Headers $headers | Out-Null
  Write-Host "已导入并发布: $key" -ForegroundColor Green
}
Write-Host '完成。已有模型默认跳过；未执行任何删除或覆盖。' -ForegroundColor Green
