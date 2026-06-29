$workspace = "c:\Users\Ddraig__\Downloads\MODS_CREATION\ChainLoader"
Set-Location -Path $workspace
$jdkSubFolder = Get-ChildItem -Path jdk21 -Filter "jdk-*" | Select-Object -First 1
$javacPath = Join-Path $jdkSubFolder.FullName "bin/javac.exe"

$compatFiles = Get-ChildItem -Path "loader/loader-compat/src/main/java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$stubFiles = $compatFiles | Where-Object { $_ -like "*\net\minecraft\*" -or $_ -like "*/net/minecraft/*" -or $_ -like "*\com\mojang\*" -or $_ -like "*/com/mojang/*" }

if (Test-Path "bin-stubs") {
    Remove-Item "bin-stubs" -Recurse -Force
}
New-Item -ItemType Directory -Path "bin-stubs" -Force

$stubsListPath = Join-Path $workspace "stubs.txt"
$stubFiles | Out-File -FilePath $stubsListPath -Encoding ascii

Write-Host "Compiling stubs..."
& $javacPath -proc:none -cp "lib/*" -d bin-stubs "@$stubsListPath"

if (Test-Path $stubsListPath) {
    Remove-Item -Path $stubsListPath -Force
}
