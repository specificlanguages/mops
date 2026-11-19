# Temporary probe for issue #299: prints the JNA layout of an MPS home and tries to load JNA with its Java.
param([string]$MpsHome, [string]$JavaHome)
$ErrorActionPreference = 'Continue'
$java = Join-Path $JavaHome 'bin\java.exe'

Write-Host "===== MPS home: $MpsHome"
Get-Content (Join-Path $MpsHome 'build.txt') -ErrorAction SilentlyContinue
Write-Host "===== product-info.json present: $(Test-Path (Join-Path $MpsHome 'product-info.json'))"
Write-Host "===== lib\jna (recursive)"
Get-ChildItem -Recurse -Force (Join-Path $MpsHome 'lib\jna') | ForEach-Object { '{0}  {1}' -f $_.FullName, $_.Length }
Write-Host "===== jnidispatch* anywhere in the MPS home"
Get-ChildItem -Recurse -Force -Filter 'jnidispatch*' $MpsHome | ForEach-Object { '{0}  {1}' -f $_.FullName, $_.Length }
Write-Host "===== Java home: $JavaHome"
Get-Content (Join-Path $JavaHome 'release') -ErrorAction SilentlyContinue
& $java -version 2>&1 | ForEach-Object { "$_" }

# Compiled with the job's JDK, because the MPS runtime may lack the compiler for single-file source launching.
$classes = Join-Path $env:RUNNER_TEMP 'probe-classes'
& (Join-Path $env:JAVA_HOME 'bin\javac.exe') --release 17 -cp "$MpsHome\lib\*" -d $classes "$PSScriptRoot\Probe.java" 2>&1 | ForEach-Object { "$_" }
$cp = "$classes;$MpsHome\lib\*"

$dirs = @((Join-Path $MpsHome 'lib\jna'), (Join-Path $MpsHome 'lib\jna\amd64'))
$dlls = @(Get-ChildItem -Recurse -Force -Filter 'jnidispatch.dll' (Join-Path $MpsHome 'lib\jna'))
$dirs += $dlls | ForEach-Object { $_.DirectoryName }
foreach ($dir in ($dirs | Select-Object -Unique)) {
    Write-Host "===== JNA probe, jna.boot.library.path=$dir (exists: $(Test-Path $dir))"
    & $java '-Djna.debug_load=true' '-Djna.debug_load.jna=true' "-Djna.boot.library.path=$dir" `
        -cp $cp Probe jna 2>&1 | ForEach-Object { "$_" }
}
foreach ($dll in $dlls) {
    Write-Host "===== System.load $($dll.FullName)"
    & $java -cp $cp Probe load $dll.FullName 2>&1 | ForEach-Object { "$_" }
}
exit 0
