# ChainLoader Verification Simulation Runner
# Downloads JDK 21, dependencies, compiles the multi-module source files, copies resources, and launches the loader.

param(
    [switch]$CompileOnly = $false
)

$ErrorActionPreference = "Continue"

$workspace = "c:\Users\Ddraig__\Downloads\MODS_CREATION\ChainLoader"
Set-Location -Path $workspace

# 1. Create directories
Write-Host "Creating output directories..." -ForegroundColor Cyan
$libDir = New-Item -ItemType Directory -Force -Path "lib"
$binDir = New-Item -ItemType Directory -Force -Path "bin"
$modsDir = New-Item -ItemType Directory -Force -Path "mods"

# 2. Download portable JDK 21 (Adoptium Temurin) if not present
# Minecraft 1.21.1 uses Java 21 classes (class file version 65.0) which cannot be compiled or run on Java 17.
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

# Resolve the executable paths from the extracted JDK directory
$jdkSubFolder = Get-ChildItem -Path $jdkDir -Filter "jdk-*" | Select-Object -First 1
$javacPath = Join-Path $jdkSubFolder.FullName "bin/javac.exe"
$javaPath = Join-Path $jdkSubFolder.FullName "bin/java.exe"
$jarPath = Join-Path $jdkSubFolder.FullName "bin/jar.exe"

# Query the official Minecraft Java runtime (UWP / MS Store)
$launcherJava = Get-ChildItem -Path "$env:LOCALAPPDATA\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName -First 1
if ($null -eq $launcherJava) {
    # Check classic Program Files launcher path as fallback
    $launcherJava = Get-ChildItem -Path "C:\Program Files (x86)\Minecraft Launcher\runtime" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName -First 1
}

if ($null -ne $launcherJava) {
    Write-Host "Detected official Minecraft Java Runtime: $launcherJava" -ForegroundColor Green
    Write-Host "Using official runtime to ensure graphics driver compatibility..." -ForegroundColor Green
    $javaPath = $launcherJava
}

# 3. Download dependencies from Maven Central and Mojang
Write-Host "Downloading compile-time dependencies..." -ForegroundColor Cyan

$urls = @{
    "asm-9.6.jar" = "https://repo1.maven.org/maven2/org/ow2/asm/asm/9.6/asm-9.6.jar"
    "asm-commons-9.6.jar" = "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.6/asm-commons-9.6.jar"
    "asm-tree-9.6.jar" = "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar"
    "gson-2.10.1.jar" = "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
    "annotations-24.0.1.jar" = "https://repo1.maven.org/maven2/org/jetbrains/annotations/24.0.1/annotations-24.0.1.jar"
}

foreach ($fileName in $urls.Keys) {
    $targetPath = Join-Path $libDir.FullName $fileName
    if (!(Test-Path -Path $targetPath)) {
        Write-Host "  Downloading $fileName..." -ForegroundColor Yellow
        Invoke-WebRequest -Uri $urls[$fileName] -OutFile $targetPath -UseBasicParsing
    } else {
        Write-Host "  $fileName already exists. Skipping download." -ForegroundColor Green
    }
}

# 4. Download the real Minecraft 1.21.1 client JAR from Mojang CDN dynamically
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
            Write-Host "  Warning: 1.21.1 version entry not found in Mojang manifest. Skipping download." -ForegroundColor Red
        }
    } catch {
        Write-Host "  Failed to download real Minecraft JAR: $_" -ForegroundColor Red
    }
} else {
    Write-Host "  Minecraft 1.21.1 client JAR already exists. Skipping download." -ForegroundColor Green
}

# 5. Clean output folders and prepare sources list
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
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/transform/BytecodeTransformerTest.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/HeadlessSimulator.java").Path
)

# First compile stubs to bin-stubs
$stubsListPath = Join-Path $workspace "stubs.txt"
if ($stubFiles) {
    $stubFiles | Out-File -FilePath $stubsListPath -Encoding ascii
    Write-Host "Compiling stubs..." -ForegroundColor Cyan
    & $javacPath -proc:none -cp "lib/*" -d bin-stubs "@$stubsListPath"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Stub compilation failed with exit code $LASTEXITCODE"
        Exit $LASTEXITCODE
    }
    # if (Test-Path $stubsListPath) {
    #     Remove-Item -Path $stubsListPath -Force
    # }
    Write-Host "Packaging stubs into stubs.jar..." -ForegroundColor Cyan
    if (Test-Path "stubs.jar") {
        Remove-Item -Path "stubs.jar" -Force
    }
    & $jarPath cf stubs.jar -C bin-stubs .
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to package stubs.jar" -ForegroundColor Red
        Exit $LASTEXITCODE
    }
}

# Then compile core files and non-stub compat files to bin (referencing stubs.jar on the classpath)
$sourcesListPath = Join-Path $workspace "sources.txt"
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

# 6. Compile classes using the extracted JDK 21 javac (including stubs.jar on classpath)
Write-Host "Compiling Java source files with JDK 21..." -ForegroundColor Cyan
$cp = "stubs.jar;lib/*;lib/asm-9.6.jar;lib/asm-commons-9.6.jar;lib/asm-tree-9.6.jar;lib/gson-2.10.1.jar;lib/minecraft-1.21.1.jar"
& $javacPath -proc:none -cp "$cp" -d bin "@$sourcesListPath"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Main compilation failed with exit code $LASTEXITCODE"
    Exit $LASTEXITCODE
}
if (Test-Path $sourcesListPath) {
    Remove-Item -Path $sourcesListPath -Force
}
if (Test-Path "stubs.jar") {
    Remove-Item -Path "stubs.jar" -Force
}

# 7. Copy Resource Folders (Required for SPI Discovery & Configs)
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

# 8. Execute ChainLauncher Bootloader using the extracted JDK 21 java
if ($CompileOnly) {
    Write-Host "CompileOnly switch detected. Compilation and copy successful. Skipping game execution." -ForegroundColor Green
    Exit 0
}
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  LAUNCHING CHAINLOADER SIMULATION (JAVA 21)" -ForegroundColor Magenta
Write-Host "==================================================" -ForegroundColor Cyan

# Run the launcher under JDK 21. It will load net.example.ExampleMod and boot successfully.
# We also include the real Minecraft JAR and all its library dependencies on the classpath,
# and point to the native libraries to perform a full game launch.
& $javaPath "-Dchainloader.additionalClasspath=lib/minecraft-1.21.1.jar;bin" "-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump" -Xmx8G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M "-Djava.library.path=natives" "-Dorg.lwjgl.system.SharedLibraryExtractPath=natives" -cp "bin;lib/*" net.chainloader.loader.core.ChainLauncher --username Player --version 1.21.1 --gameDir . --uuid 5084e6f3-8f54-43f1-8df5-1dca109e430f --accessToken 0 --assetsDir C:\Users\Ddraig__\AppData\Roaming\.minecraft\assets --assetIndex 17
