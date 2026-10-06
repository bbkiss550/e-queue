$ErrorActionPreference='Stop'
$taskRecordPath=Join-Path $PSScriptRoot '.local/server.json'
if (!(Test-Path -LiteralPath $taskRecordPath)) { Write-Output 'No managed background server.'; exit 0 }
$taskRecord=Get-Content -Raw -LiteralPath $taskRecordPath | ConvertFrom-Json
$taskRuntimeRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '.local/runtime'))+[IO.Path]::DirectorySeparatorChar
$taskJar=[IO.Path]::GetFullPath($taskRecord.jar)
if (!$taskJar.StartsWith($taskRuntimeRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Server jar is outside this project runtime directory.' }
$taskProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$taskRecord.pid)"
if ($taskProcess) {
  if (!$taskProcess.ExecutablePath.EndsWith('\java.exe',[StringComparison]::OrdinalIgnoreCase) -or !$taskProcess.CommandLine.Contains($taskJar)) { throw 'PID does not belong to the recorded E-Queue process.' }
  Stop-Process -Id ([int]$taskRecord.pid)
  Wait-Process -Id ([int]$taskRecord.pid) -Timeout 10 -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $taskRecordPath
Remove-Item -LiteralPath $taskJar -ErrorAction SilentlyContinue
Write-Output 'E-Queue stopped.'
