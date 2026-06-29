$workspace = "c:\Users\Ddraig__\Downloads\MODS_CREATION\ChainLoader"
Set-Location -Path $workspace
$jdkSubFolder = Get-ChildItem -Path jdk21 -Filter "jdk-*" | Select-Object -First 1
$javacPath = Join-Path $jdkSubFolder.FullName "bin/javac.exe"

$coreFiles = Get-ChildItem -Path "loader/loader-core/src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$apiFiles = @("mdk/chainloader-api/src/main/java/net/chainloader/api/environment/EnvType.java", "mdk/chainloader-api/src/main/java/net/chainloader/api/environment/ChainLoaderEnv.java") | ForEach-Object { (Resolve-Path $_).Path }
$testFiles = @(
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/TestScan.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/example/ExampleMod.java").Path
)
$forgeCompatFiles = Get-ChildItem -Path "loader/loader-compat/src/main/java/net/minecraftforge", "loader/loader-compat/src/main/java/net/neoforged", "loader/loader-compat/src/main/java/net/fabricmc", "loader/loader-compat/src/main/java/dev", "loader/loader-compat/src/main/java/team/reborn", "loader/loader-compat/src/main/java/net/chainloader", "loader/loader-compat/src/main/java/mezz", "loader/loader-compat/src/main/java/me" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }

$sourcesListPath = Join-Path $workspace "sources.txt"
$allCoreAndCompatFiles = $coreFiles + $apiFiles + $testFiles + $forgeCompatFiles
Write-Host "Found $($allCoreAndCompatFiles.Count) files to compile."
$allCoreAndCompatFiles | Out-File -FilePath $sourcesListPath -Encoding ascii

$cp = "bin-stubs;lib/*;lib/asm-9.6.jar;lib/asm-commons-9.6.jar;lib/asm-tree-9.6.jar;lib/gson-2.10.1.jar;lib/minecraft-1.21.1.jar"
& $javacPath -proc:none -cp "$cp" -d bin "@$sourcesListPath"
