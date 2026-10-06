param([int]$Port = 8090, [switch]$Background)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskJdk = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '.tools') -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
$taskJava = if ($taskJdk) { Join-Path $taskJdk.FullName 'bin\java.exe' } elseif ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
if (!(Test-Path -LiteralPath 'target/e-queue-1.0.0.jar')) { throw 'กรุณา build โปรเจกต์ก่อนด้วย .\build.ps1' }
$taskRuntime=Join-Path $PSScriptRoot '.local/runtime'
New-Item -ItemType Directory -Force -Path $taskRuntime | Out-Null
$taskJar=Join-Path $taskRuntime ("e-queue-"+[guid]::NewGuid().ToString('N')+'.jar')
Copy-Item -LiteralPath 'target/e-queue-1.0.0.jar' -Destination $taskJar
if ($Background) {
  $taskProcess=Start-Process -WindowStyle Hidden -FilePath $taskJava -ArgumentList @('-jar', ('"'+$taskJar+'"'), "--server.port=$Port") -WorkingDirectory $PSScriptRoot -RedirectStandardOutput (Join-Path $PSScriptRoot '.local/app.log') -RedirectStandardError (Join-Path $PSScriptRoot '.local/app-error.log') -PassThru
  @{pid=$taskProcess.Id;jar=$taskJar;port=$Port} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot '.local/server.json') -Encoding utf8
  Write-Output "E-Queue: http://localhost:$Port/ (PID $($taskProcess.Id)). Stop with .\stop.ps1"
  exit 0
}
try { & $taskJava '-jar' $taskJar "--server.port=$Port" } finally { Remove-Item -LiteralPath $taskJar -ErrorAction SilentlyContinue }
