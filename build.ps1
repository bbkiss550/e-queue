param([switch]$Test)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskJdk = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '.tools') -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -First 1
if ($taskJdk) { $env:JAVA_HOME = $taskJdk.FullName }
$taskMaven = Get-Command mvn -ErrorAction SilentlyContinue
if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'mvnw.cmd')) { $taskMavenPath=Join-Path $PSScriptRoot 'mvnw.cmd' }
elseif ($taskMaven) { $taskMavenPath=$taskMaven.Source } else {
  $taskMavenPath=Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (!$taskMavenPath) { throw 'ต้องมี Maven 3.6.3 ขึ้นไป หรือ Maven Wrapper' }
if ($Test) {
  if (!$env:TEST_DB_PASSWORD) { throw 'กำหนด TEST_DB_PASSWORD และสร้างฐานข้อมูล db_queue_test ก่อนรันทดสอบ' }
  & $taskMavenPath 'verify'
} else { & $taskMavenPath '-DskipTests' 'package' }
exit $LASTEXITCODE
