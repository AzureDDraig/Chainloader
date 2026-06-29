$workspace = "c:\Users\Ddraig__\Downloads\MODS_CREATION\ChainLoader"
Set-Location -Path $workspace

$compatFiles = Get-ChildItem -Path "loader/loader-compat/src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$stubFiles = $compatFiles | Where-Object { $_ -like "*\net\minecraft\*" -or $_ -like "*/net/minecraft/*" -or $_ -like "*\com\mojang\*" -or $_ -like "*/com/mojang/*" }

$jdkSubFolder = Get-ChildItem -Path "jdk21" -Filter "jdk-*" | Select-Object -First 1
$javacPath = Join-Path $jdkSubFolder.FullName "bin/javac.exe"

$stubsListPath = Join-Path $workspace "stubs_test.txt"
$stubFiles | Out-File -FilePath $stubsListPath -Encoding ascii

Write-Host "Stub files count: $($stubFiles.Count)"
if (Test-Path "bin-stubs") {
    Remove-Item "bin-stubs" -Recurse -Force
}
New-Item -ItemType Directory -Path "bin-stubs" -Force

$outputFile = Join-Path $workspace "stubs_compile.log"
& $javacPath -cp "lib/*" -d bin-stubs "@$stubsListPath" 2>&1 | Out-File -FilePath $outputFile

Write-Host "Stubs compilation output:"
Get-Content $outputFile | Select-Object -First 50
