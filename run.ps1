param([switch]$Test)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$jdkRoot = $env:JAVA_HOME
if (-not $jdkRoot -or -not (Test-Path (Join-Path $jdkRoot 'bin/javac.exe'))) {
    $compiler = Get-Command javac -ErrorAction SilentlyContinue
    if ($compiler) { $jdkRoot = Split-Path (Split-Path $compiler.Source) }
    else {
        $installation = Get-ChildItem 'C:/Program Files/Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($installation) { $jdkRoot = $installation.FullName }
    }
}
if (-not $jdkRoot) { throw 'Install JDK 21 or newer and set JAVA_HOME.' }
$sources = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | Select-Object -ExpandProperty FullName
& (Join-Path $jdkRoot 'bin/javac.exe') -encoding UTF-8 -d build $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
if ($Test) {
    New-Item -ItemType Directory -Path runs -Force | Out-Null
    & (Join-Path $jdkRoot 'bin/java.exe') -cp build LabSmokeTest
    if ($LASTEXITCODE -ne 0) { throw 'UDP integration checks failed' }
    & (Join-Path $jdkRoot 'bin/java.exe') -cp build ScenarioTest
    if ($LASTEXITCODE -ne 0) { throw 'Scenario checks failed' }
    & (Join-Path $jdkRoot 'bin/java.exe') '-Djava.awt.headless=true' -cp build TracePanelTest
} else {
    & (Join-Path $jdkRoot 'bin/java.exe') -cp build PacketLab
}
if ($LASTEXITCODE -ne 0) { throw 'PacketLab exited with an error' }
