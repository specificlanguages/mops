# Temporary probe for issue #299: starts a mops daemon on a copy of test-projects/mps-json with the given MPS home and
# Java home, stops it, and prints and collects its logs. Exits 1 when `daemon ping` fails.
param([string]$Name, [string]$MpsHome, [string]$JavaHome)
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path "$PSScriptRoot\..\..").Path
$mops = "$repo\cli\build\install\mops\bin\mops.bat"
$project = "$env:RUNNER_TEMP\project-$Name"
$daemonHome = "$env:RUNNER_TEMP\daemon-home-$Name"
$out = "$env:RUNNER_TEMP\logs\$Name"
Copy-Item -Recurse "$repo\test-projects\mps-json" $project
New-Item -ItemType Directory -Force $daemonHome, $out | Out-Null

$common = @('--java-home', $JavaHome, '--mps-home', $MpsHome, '--daemon-home', $daemonHome,
    '--project-root', $project)
Write-Host "MOPS_DAEMON_JAVA_OPTIONS=$env:MOPS_DAEMON_JAVA_OPTIONS"
Write-Host "===== mops daemon ping"
& $mops @common daemon ping 2>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath "$out\ping-output.txt"
$code = $LASTEXITCODE
Write-Host "ping exit code: $code"
Write-Host "===== mops daemon status"
& $mops @common daemon status 2>&1 | ForEach-Object { "$_" }
Write-Host "===== mops daemon stop"
& $mops @common daemon stop 2>&1 | ForEach-Object { "$_" }
Start-Sleep -Seconds 5

foreach ($log in Get-ChildItem -Recurse -Force -File -Include *.log -Path $daemonHome) {
    $relative = $log.FullName.Substring($daemonHome.Length + 1)
    $target = Join-Path $out $relative
    New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
    Copy-Item $log.FullName $target
    Write-Host "===== $($log.FullName)"
    if ($log.Name -eq 'idea.log') {
        Select-String -Path $log.FullName -Pattern 'jna|jnidispatch|UnsatisfiedLink' -CaseSensitive:$false |
            ForEach-Object { $_.Line }
    } else {
        Get-Content $log.FullName
    }
}
exit $(if ($code -eq 0) { 0 } else { 1 })
