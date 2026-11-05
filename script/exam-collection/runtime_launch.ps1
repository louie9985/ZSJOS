# UTF-8. User approved these two cached libraries for isolated acceptance on 2026-11-05.
$ErrorActionPreference = 'Stop'
$fixtureRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$fixtureDirectory = Join-Path $fixtureRoot 'artifacts/exam-collection/runtime'
$manifest = Get-Content -LiteralPath (Join-Path $fixtureDirectory 'manifest.json') -Raw | ConvertFrom-Json
if ($manifest.database -ne 'exam_collection_runtime_20261104234105' -or $manifest.httpPort -ne 48089) {
    throw 'Unexpected runtime fixture'
}
if (Get-NetTCPConnection -LocalPort 48089 -State Listen -ErrorAction SilentlyContinue) {
    throw 'HTTP port already owned by a running process'
}
$clientJar = 'C:/Users/EDY/.m2/repository/org/apache/httpcomponents/httpclient/4.5.14/httpclient-4.5.14.jar'
$coreJar = 'C:/Users/EDY/.m2/repository/org/apache/httpcomponents/httpcore/4.4.16/httpcore-4.4.16.jar'
if ((Get-Item -LiteralPath $clientJar).Length -ne 785639 -or (Get-Item -LiteralPath $coreJar).Length -ne 327891) {
    throw 'Approved dependency identity mismatch'
}
Set-Location -LiteralPath $fixtureRoot
& python -B script/exam-collection/runtime_validate_config.py
if ($LASTEXITCODE -ne 0) {
    throw 'Runtime configuration validation failed'
}
$runtimeJar = Join-Path $fixtureDirectory 'application.jar'
Copy-Item -LiteralPath (Join-Path $fixtureRoot 'backend/yudao-server/target/yudao-server.jar') -Destination $runtimeJar -Force
$fixtureLog = Join-Path $fixtureDirectory ('launch-' + (Get-Date -Format 'yyyyMMddHHmmss') + '.log')
& java "-Dloader.path=$clientJar,$coreJar" -cp $runtimeJar org.springframework.boot.loader.launch.PropertiesLauncher --spring.profiles.active=exam-isolated "--spring.config.additional-location=file:$($fixtureDirectory.Replace('\','/'))/" --spring.autoconfigure.exclude=org.dromara.trans.config.TransServiceConfig --server.port=48089 --server.address=127.0.0.1 *> $fixtureLog
exit $LASTEXITCODE
