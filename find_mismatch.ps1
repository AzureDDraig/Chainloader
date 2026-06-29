$content = Get-Content -Path loader/loader-core/src/main/java/net/chainloader/loader/core/ChainLauncher.java
$depth = 0
$lineNum = 0
foreach ($line in $content) {
    $lineNum++
    $oldDepth = $depth
    for ($i = 0; $i -lt $line.Length; $i++) {
        $c = $line[$i]
        if ($c -eq '{') {
            $depth++
        } elseif ($c -eq '}') {
            $depth--
        }
    }
    if ($oldDepth -eq 1 -and $depth -eq 2) {
        Write-Host "Depth went 1 -> 2 at line $lineNum : $line"
    } elseif ($oldDepth -eq 2 -and $depth -eq 1) {
        Write-Host "Depth went 2 -> 1 at line $lineNum : $line"
    }
}
