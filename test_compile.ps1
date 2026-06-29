$workspace = "c:\Users\Ddraig__\Downloads\MODS_CREATION\ChainLoader"
Set-Location -Path $workspace

$jdkSubFolder = Get-ChildItem -Path "jdk21" -Filter "jdk-*" | Select-Object -First 1
$javacPath = Join-Path $jdkSubFolder.FullName "bin/javac.exe"

# Gather all library jar files explicitly
$libJars = Get-ChildItem -Path "lib" -Filter "*.jar" | ForEach-Object { $_.FullName }
$classpath = (@(Join-Path $workspace "bin-stubs") + $libJars) -join ";"

# Prepare sources
$compatFiles = Get-ChildItem -Path "loader/loader-compat/src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$forgeCompatFiles = Get-ChildItem -Path "loader/loader-compat/src/main/java/net/minecraftforge", "loader/loader-compat/src/main/java/net/neoforged", "loader/loader-compat/src/main/java/net/fabricmc", "loader/loader-compat/src/main/java/dev", "loader/loader-compat/src/main/java/team/reborn", "loader/loader-compat/src/main/java/net/chainloader", "loader/loader-compat/src/main/java/mezz", "loader/loader-compat/src/main/java/me" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }

$coreFiles = Get-ChildItem -Path "loader/loader-core/src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$apiFiles = @("mdk/chainloader-api/src/main/java/net/chainloader/api/environment/EnvType.java", "mdk/chainloader-api/src/main/java/net/chainloader/api/environment/ChainLoaderEnv.java") | ForEach-Object { (Resolve-Path $_).Path }
$testFiles = @(
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/TestScan.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/example/ExampleMod.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/HeadlessSimulator.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/MockMinecraftServer.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/core/MockServerLevel.java").Path,
    (Resolve-Path "loader/loader-core/src/test/java/net/chainloader/loader/access/AccessWidenerTest.java").Path
)

$compatTestFiles = Get-ChildItem -Path "loader/loader-compat/src/test/java" -Filter "*.java" -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }

$allCoreAndCompatFiles = $coreFiles + $apiFiles + $testFiles + $forgeCompatFiles
$sourcesListPath = Join-Path $workspace "sources_test.txt"
$allCoreAndCompatFiles | Out-File -FilePath $sourcesListPath -Encoding ascii

if (Test-Path "bin") {
    Remove-Item "bin" -Recurse -Force
}
New-Item -ItemType Directory -Path "bin" -Force

Write-Host "Compiling with explicit classpath..."
& $javacPath -sourcepath bin/dummy -cp "$classpath" -d bin "@$sourcesListPath" 2>&1 | Out-File -FilePath "test_compile_out.log"

Write-Host "Test compilation completed. Errors:"
Get-Content "test_compile_out.log" | Select-String -Pattern "error:" -Context 0,2 | Select-Object -First 20
