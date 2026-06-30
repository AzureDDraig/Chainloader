# ChainLoader Headless Interaction Simulator Runner (Self-Contained)
# Downloads JDK 21, dependencies, compiles all modules, copies resources, and launches HeadlessSimulator.
# This script is fully independent from run_simulation.ps1.

param(
    [switch]$CompileOnly = $false
)

$ErrorActionPreference = "Continue"

$workspace = "c:\Users\Ddraig__\Downloads\MODS_CREATION\ChainLoader"
Set-Location -Path $workspace

# ============================================================
# 1. Create output directories
# ============================================================
Write-Host "Creating output directories..." -ForegroundColor Cyan
$libDir = New-Item -ItemType Directory -Force -Path "lib"
$binDir = New-Item -ItemType Directory -Force -Path "bin"
$modsDir = New-Item -ItemType Directory -Force -Path "mods"

# ============================================================
# 2. Download portable JDK 21 (Adoptium Temurin) if not present
# ============================================================
$jdkDir = Join-Path $workspace "jdk21"
if (!(Test-Path -Path $jdkDir)) {
    Write-Host "Downloading portable JDK 21 (~190 MB) to compile and run Minecraft 1.21.1..." -ForegroundColor Cyan
    $jdkUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.3%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.3_9.zip"
    $jdkZip = Join-Path $workspace "jdk21.zip"
    Write-Host "  Downloading JDK 21 zip..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkZip -UseBasicParsing

    Write-Host "  Extracting JDK 21..." -ForegroundColor Yellow
    Expand-Archive -Path $jdkZip -DestinationPath $jdkDir -Force
    Remove-Item -Path $jdkZip -Force
    Write-Host "  JDK 21 setup complete." -ForegroundColor Green
}

# Resolve JDK executable paths
$jdkSubFolder = Get-ChildItem -Path $jdkDir -Filter "jdk-*" | Select-Object -First 1
$javacPath = Join-Path $jdkSubFolder.FullName "bin/javac.exe"
$javaPath  = Join-Path $jdkSubFolder.FullName "bin/java.exe"
$jarPath   = Join-Path $jdkSubFolder.FullName "bin/jar.exe"

# ============================================================
# 3. Download compile-time dependencies from Maven Central & Mojang
# ============================================================
Write-Host "Downloading compile-time dependencies..." -ForegroundColor Cyan

$urls = @{
    "asm-9.6.jar"            = "https://repo1.maven.org/maven2/org/ow2/asm/asm/9.6/asm-9.6.jar"
    "asm-commons-9.6.jar"    = "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.6/asm-commons-9.6.jar"
    "asm-tree-9.6.jar"       = "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar"
    "gson-2.10.1.jar"        = "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
    "annotations-24.0.1.jar" = "https://repo1.maven.org/maven2/org/jetbrains/annotations/24.0.1/annotations-24.0.1.jar"
    "slf4j-api-2.0.9.jar"    = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar"
}

foreach ($fileName in $urls.Keys) {
    $targetPath = Join-Path $libDir.FullName $fileName
    if (!(Test-Path -Path $targetPath)) {
        Write-Host "  Downloading $fileName..." -ForegroundColor Yellow
        Invoke-WebRequest -Uri $urls[$fileName] -OutFile $targetPath -UseBasicParsing
    } else {
        Write-Host "  $fileName already exists. Skipping." -ForegroundColor Green
    }
}

# ============================================================
# 4. Download the real Minecraft 1.21.1 client JAR from Mojang CDN
# ============================================================
$minecraftJarPath = Join-Path $libDir.FullName "minecraft-1.21.1.jar"
if (!(Test-Path -Path $minecraftJarPath)) {
    Write-Host "Querying Mojang API for Minecraft 1.21.1 client JAR url..." -ForegroundColor Cyan
    try {
        $manifest = Invoke-RestMethod -Uri "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        $versionEntry = $manifest.versions | Where-Object { $_.id -eq "1.21.1" }
        if ($versionEntry) {
            $meta = Invoke-RestMethod -Uri $versionEntry.url
            $clientUrl = $meta.downloads.client.url
            Write-Host "  Downloading Minecraft 1.21.1 client JAR (~31 MB) from Mojang..." -ForegroundColor Yellow
            Invoke-WebRequest -Uri $clientUrl -OutFile $minecraftJarPath -UseBasicParsing
            Write-Host "  Successfully downloaded Minecraft 1.21.1 client JAR." -ForegroundColor Green
        } else {
            Write-Host "  Warning: 1.21.1 version entry not found in Mojang manifest." -ForegroundColor Red
        }
    } catch {
        Write-Host "  Failed to download real Minecraft JAR: $_" -ForegroundColor Red
    }
} else {
    Write-Host "  Minecraft 1.21.1 client JAR already exists. Skipping." -ForegroundColor Green
}

# ============================================================
# 5. Clean output folders and prepare sources list
# ============================================================
Write-Host "Preparing source files list for compilation..." -ForegroundColor Cyan
if (!(Test-Path -Path "bin")) {
    New-Item -ItemType Directory -Path "bin" -Force | Out-Null
} else {
    Get-ChildItem -Path "bin" -Filter "*.class" -Recurse -ErrorAction SilentlyContinue | Remove-Item -Force
}

if (!(Test-Path -Path "bin-stubs")) {
    New-Item -ItemType Directory -Path "bin-stubs" -Force | Out-Null
} else {
    Get-ChildItem -Path "bin-stubs" -Filter "*.class" -Recurse -ErrorAction SilentlyContinue | Remove-Item -Force
}

# Distinguish between stub files (compile-time only) and non-stub compat files (runtime)
$compatBaseFiles = Get-ChildItem -Path "loader/loader-compat-base/src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$compatFiles = Get-ChildItem -Path "loader/loader-compat-1.21.1/src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$stubFiles = $compatFiles | Where-Object { $_ -like "*\net\minecraft\*" -or $_ -like "*/net/minecraft/*" -or $_ -like "*\com\mojang\*" -or $_ -like "*/com/mojang/*" }
$forgeCompatFiles = Get-ChildItem -Path "loader/loader-compat-1.21.1/src/main/java/net/minecraftforge", "loader/loader-compat-1.21.1/src/main/java/net/neoforged", "loader/loader-compat-1.21.1/src/main/java/net/fabricmc", "loader/loader-compat-1.21.1/src/main/java/dev", "loader/loader-compat-1.21.1/src/main/java/team/reborn", "loader/loader-compat-1.21.1/src/main/java/net/chainloader", "loader/loader-compat-1.21.1/src/main/java/mezz", "loader/loader-compat-1.21.1/src/main/java/me" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }

Write-Host "Diagnostic: compatFiles count = $($compatFiles.Count)"
Write-Host "Diagnostic: stubFiles count = $($stubFiles.Count)"
Write-Host "Diagnostic: forgeCompatFiles count = $($forgeCompatFiles.Count)"

$coreFiles = Get-ChildItem -Path "loader/loader-core/src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$apiFiles = @("mdk/chainloader-api/src/main/java/net/chainloader/api/environment/EnvType.java", "mdk/chainloader-api/src/main/java/net/chainloader/api/environment/ChainLoaderEnv.java") | ForEach-Object { (Resolve-Path $_).Path }
$compatTestFiles = Get-ChildItem -Path "loader/loader-compat-1.21.1/src/test/java" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
$testFiles = @(
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/TestScan.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/example/ExampleMod.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/example/ExampleNeoForgeMod.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/transform/BytecodeTransformerTest.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/HeadlessSimulator.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/MockMinecraftServer.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/MockServerLevel.java").Path
)

# ============================================================
# 6. Compile stubs to bin-stubs
# ============================================================
$stubsListPath = "stubs.txt"
if ($stubFiles) {
    $stubFiles | Out-File -FilePath $stubsListPath -Encoding ascii
    Write-Host "Compiling stubs..." -ForegroundColor Cyan
    & $javacPath -proc:none -cp "lib/*" -d bin-stubs '@stubs.txt'
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Stub compilation failed with exit code $LASTEXITCODE"
        Exit $LASTEXITCODE
    }
}

# ============================================================
# 7. Compile core + compat + test files to bin
# ============================================================
$sourcesListPath = "sources.txt"
$allCoreAndCompatFiles = $coreFiles + $apiFiles + $testFiles + $forgeCompatFiles + $compatBaseFiles
Write-Host "Sources list path: $sourcesListPath"
Write-Host "Core files count: $($coreFiles.Count)"
Write-Host "API files count: $($apiFiles.Count)"
Write-Host "Test files count: $($testFiles.Count)"
Write-Host "Forge compat files count: $($forgeCompatFiles.Count)"
Write-Host "Compat base files count: $($compatBaseFiles.Count)"
Write-Host "All files count: $($allCoreAndCompatFiles.Count)"
$allCoreAndCompatFiles | Out-File -FilePath $sourcesListPath -Encoding ascii
Write-Host "Diagnostic: sources.txt exists? $((Test-Path $sourcesListPath))"
Write-Host "Diagnostic: sources.txt length = $((Get-Item $sourcesListPath).Length)"

Write-Host "Compiling Java source files with JDK 21..." -ForegroundColor Cyan
$cp = "bin-stubs;lib/*;lib/asm-9.6.jar;lib/asm-commons-9.6.jar;lib/asm-tree-9.6.jar;lib/gson-2.10.1.jar;lib/minecraft-1.21.1.jar"
& $javacPath -proc:none -sourcepath bin/dummy -cp "$cp" -d bin "@$sourcesListPath"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Main compilation failed with exit code $LASTEXITCODE"
    Exit $LASTEXITCODE
}
if (Test-Path $sourcesListPath) {
    Remove-Item -Path $sourcesListPath -Force
}

# ============================================================
# 8. Copy resource folders (SPI Discovery & Configs)
# ============================================================
Write-Host "Copying resources to compilation output..." -ForegroundColor Cyan

# Copy META-INF services
$servicesSource = "loader/loader-core/src/main/resources/META-INF"
if (Test-Path -Path $servicesSource) {
    Copy-Item -Path $servicesSource -Destination "bin" -Recurse -Force
    Write-Host "  Copied services META-INF." -ForegroundColor Green
}

$compatServicesSource = "loader/loader-compat-1.21.1/src/main/resources/META-INF"
if (Test-Path -Path $compatServicesSource) {
    Copy-Item -Path $compatServicesSource -Destination "bin" -Recurse -Force
    Write-Host "  Copied compat services META-INF." -ForegroundColor Green
}

# Copy example mod chainmod.json
$modConfig = "samples/example-mod/src/main/resources/chainmod.json"
if (Test-Path -Path $modConfig) {
    Copy-Item -Path $modConfig -Destination "bin" -Force
    Write-Host "  Copied example mod config metadata." -ForegroundColor Green
}

Write-Host "Compilation and resource copy successful." -ForegroundColor Green

if ($CompileOnly) {
    Write-Host "CompileOnly switch detected. Skipping simulator execution." -ForegroundColor Green
    Exit 0
}

# ============================================================
# 9. Launch HeadlessSimulator
# ============================================================
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  LAUNCHING HEADLESS SIMULATION (JAVA 21)" -ForegroundColor Magenta
Write-Host "==================================================" -ForegroundColor Cyan

$runCp = "bin;bin-stubs;lib/*"
& $javaPath "-Dchainloader.debug=true" -cp "$runCp" net.chainloader.loader.core.HeadlessSimulator

Write-Host "Simulation Run Ended with Exit Code: $LASTEXITCODE" -ForegroundColor Green
