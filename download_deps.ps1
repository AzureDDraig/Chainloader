# download_deps.ps1 - Downloads all 1.21.1 libraries & extracts natives for Windows

$ErrorActionPreference = "Stop"
$libDir = "lib"
$nativesDir = "natives"

if (!(Test-Path -Path $libDir)) {
    New-Item -ItemType Directory -Path $libDir -Force
}
if (!(Test-Path -Path $nativesDir)) {
    New-Item -ItemType Directory -Path $nativesDir -Force
}

Write-Host "Querying Mojang for Minecraft 1.21.1 package list..." -ForegroundColor Cyan
$meta = Invoke-RestMethod -Uri "https://piston-meta.mojang.com/v1/packages/eb8e6accb7544bf7f666ffa18e0140db1136d7ad/1.21.1.json"

Write-Host "Filtering libraries for Windows compatibility..." -ForegroundColor Cyan
$downloads = New-Object System.Collections.Generic.List[PSObject]

foreach ($lib in $meta.libraries) {
    $allowed = $true
    if ($null -ne $lib.rules) {
        $allowed = $false
        foreach ($rule in $lib.rules) {
            $osMatch = $false
            if ($null -eq $rule.os) {
                $osMatch = $true
            } elseif ($rule.os.name -eq "windows") {
                $osMatch = $true
            }
            
            # Skip demo/features specific rules for testing
            if ($osMatch -and $null -eq $rule.features) {
                if ($rule.action -eq "allow") {
                    $allowed = $true
                } elseif ($rule.action -eq "disallow") {
                    $allowed = $false
                }
            }
        }
    }
    
    if ($allowed -and $null -ne $lib.downloads.artifact) {
        $downloads.Add($lib.downloads.artifact)
    }
}

Write-Host "Found $($downloads.Count) libraries compatible with Windows." -ForegroundColor Green

# Download each library
$count = 0
foreach ($artifact in $downloads) {
    $count++
    $fileName = Split-Path -Leaf $artifact.path
    $targetPath = Join-Path $libDir $fileName
    
    if (!(Test-Path -Path $targetPath)) {
        Write-Host "[$count/$($downloads.Count)] Downloading $fileName..." -ForegroundColor Yellow
        try {
            Invoke-WebRequest -Uri $artifact.url -OutFile $targetPath -UseBasicParsing
        } catch {
            Write-Host "  Failed to download ${fileName}: $_" -ForegroundColor Red
        }
    } else {
        Write-Host "[$count/$($downloads.Count)] $fileName already exists. Skipping." -ForegroundColor Green
    }
}

# Extract DLL natives from the natives jar files
Write-Host "Extracting native DLLs to $nativesDir..." -ForegroundColor Cyan
$nativesJars = Get-ChildItem -Path $libDir -Filter "*-natives-windows.jar"
foreach ($jar in $nativesJars) {
    Write-Host "  Extracting natives from $($jar.Name)..." -ForegroundColor Yellow
    
    # We use .NET ZipFile to extract without changing directory/path limits
    [System.Reflection.Assembly]::LoadWithPartialName("System.IO.Compression.FileSystem") | Out-Null
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
        foreach ($entry in $zip.Entries) {
            # Only extract .dll and .txt files (avoid directories/other structures)
            if ($entry.Name.EndsWith(".dll") -or $entry.Name.EndsWith(".git")) {
                $targetFile = Join-Path $nativesDir $entry.Name
                if (!(Test-Path -Path $targetFile)) {
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $targetFile, $true)
                }
            }
        }
        $zip.Dispose()
    } catch {
        Write-Host "  Failed to extract natives from $($jar.Name): $_" -ForegroundColor Red
    }
}

Write-Host "Minecraft 1.21.1 dependency download and setup complete." -ForegroundColor Green
