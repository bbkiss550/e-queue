$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskTools=Join-Path $PSScriptRoot '.tools'
New-Item -ItemType Directory -Force -Path $taskTools | Out-Null
$taskExisting=Get-ChildItem -LiteralPath $taskTools -Directory | Where-Object {Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe')} | Select-Object -First 1
if ($taskExisting) { Write-Output "Portable JDK: $($taskExisting.FullName)"; exit 0 }
$ProgressPreference='SilentlyContinue'
$taskReleases=Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
$taskPackage=$taskReleases[0].binary.package
$taskZip=Join-Path $taskTools 'jdk21.zip'
Invoke-WebRequest -Uri $taskPackage.link -OutFile $taskZip
if ((Get-FileHash -LiteralPath $taskZip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskPackage.checksum.ToLowerInvariant()) { throw 'JDK checksum mismatch' }
Expand-Archive -LiteralPath $taskZip -DestinationPath $taskTools
Write-Output 'Portable Java 21 ready. Run .\build.ps1 and .\start.ps1.'
