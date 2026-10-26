# PokePass build script (javac + jar, no Maven/Gradle)
# ASCII only on purpose: PowerShell 5.1 reads BOM-less scripts as GBK and non-ASCII comments break parsing.
param(
    [switch]$Deploy
)

$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

$Jdk = "C:\Program Files\Java\jdk1.8.0_191"
$Javac = Join-Path $Jdk "bin\javac.exe"
$Jar = Join-Path $Jdk "bin\jar.exe"

$Src = "plugin\src\main\java"
$Res = "plugin\src\main\resources"
$StubSrc = "stub"
$Classes = "build\classes"
$Stubs = "build\stubs"
$Dist = "dist"
$JarName = "PokePass-1.0.0.jar"

# Reuse the dependency jars already sitting in the PokeTraining project.
$LibRoot = "..\PokeTraining\libs"
$Cs = (Resolve-Path (Join-Path $LibRoot "CatServer-1.12.2-universal.jar")).Path
$Pm = (Resolve-Path (Join-Path $LibRoot "Pixelmon-1.12.2-8.4.3-universal.jar")).Path

New-Item -ItemType Directory -Force -Path $Classes, $Stubs, $Dist | Out-Null
if (Test-Path $Classes) { [IO.Directory]::Delete((Resolve-Path $Classes).Path, $true) }
if (Test-Path $Stubs) { [IO.Directory]::Delete((Resolve-Path $Stubs).Path, $true) }
New-Item -ItemType Directory -Force -Path $Classes, $Stubs | Out-Null
if (Test-Path "$Dist\$JarName") { Remove-Item "$Dist\$JarName" -Force -ErrorAction SilentlyContinue }

Write-Output "==> compile stubs"
$stubFiles = @(Get-ChildItem $StubSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($stubFiles.Count -gt 0) {
    $stubList = Join-Path $Root "build\stub-sources.txt"
    [IO.File]::WriteAllLines($stubList, $stubFiles)
    & $Javac -encoding UTF-8 -source 1.8 -target 1.8 -nowarn -d $Stubs "@$stubList"
    if ($LASTEXITCODE -ne 0) { Write-Output "STUB COMPILE FAILED"; exit 1 }
}

Write-Output "==> compile plugin"
$srcFiles = @(Get-ChildItem $Src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$srcList = Join-Path $Root "build\sources.txt"
[IO.File]::WriteAllLines($srcList, $srcFiles)
$cp = "$Cs;$Pm;$Stubs"
& $Javac -encoding UTF-8 -source 1.8 -target 1.8 -nowarn -d $Classes -cp $cp "@$srcList"
if ($LASTEXITCODE -ne 0) { Write-Output "PLUGIN COMPILE FAILED"; exit 1 }

Write-Output "==> copy resources"
Copy-Item "$Res\*" $Classes -Recurse -Force

Write-Output "==> build jar"
& $Jar cf "$Dist\$JarName" -C $Classes .
if ($LASTEXITCODE -ne 0) { Write-Output "JAR FAILED"; exit 1 }

$info = Get-Item "$Dist\$JarName"
$classCount = @(Get-ChildItem $Classes -Recurse -Filter *.class).Count
Write-Output ("    jar  : dist\$JarName")
Write-Output ("    size : " + $info.Length + " bytes")
Write-Output ("    class: " + $classCount)

$DeployDir = "..\TestServer\plugins"
if ($Deploy -and (Test-Path $DeployDir)) {
    $listening = $false
    try {
        $conn = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction Stop
        if ($conn) { $listening = $true }
    } catch { }
    if ($listening) {
        Write-Output "    SKIP deploy: server is running (25565 listening)"
    } else {
        Copy-Item "$Dist\$JarName" $DeployDir -Force
        Write-Output ("    deployed to " + $DeployDir)
    }
}
