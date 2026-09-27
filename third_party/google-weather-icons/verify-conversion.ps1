# Check that the generated Android resources retain every SVG path and its styling.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$weatherVendorRoot = $PSScriptRoot
$weatherProjectRoot = (Resolve-Path (Join-Path $weatherVendorRoot '../..')).Path
$weatherAndroidNamespace = 'http://schemas.android.com/apk/res/android'
$weatherVerifiedPathCount = 0
$weatherVerifiedClipCount = 0

function Assert-Equal($actual, $expected, [string]$context) {
    if ($actual -cne $expected) { throw "$context differs: expected '$expected', found '$actual'" }
}

$weatherSvgFiles = Get-ChildItem (Join-Path $weatherVendorRoot 'set-5') -Recurse -Filter '*.svg'
foreach ($weatherSvgFile in $weatherSvgFiles) {
    [xml]$weatherSvg = Get-Content -LiteralPath $weatherSvgFile.FullName -Raw
    $weatherTheme = $weatherSvgFile.Directory.Name
    $weatherOutput = Join-Path $weatherProjectRoot ('app/src/main/res/drawable/weather_google_' + $weatherSvgFile.BaseName + '_' + $weatherTheme + '.xml')
    [xml]$weatherVector = Get-Content -LiteralPath $weatherOutput -Raw
    Assert-Equal $weatherVector.vector.GetAttribute('viewportWidth', $weatherAndroidNamespace) '48' 'Viewport width'
    Assert-Equal $weatherVector.vector.GetAttribute('viewportHeight', $weatherAndroidNamespace) '48' 'Viewport height'
    $weatherSvgPaths = $weatherSvg.SelectNodes("//*[local-name()='path' and not(ancestor::*[local-name()='defs'])]")
    $weatherVectorPaths = $weatherVector.SelectNodes('//path')
    Assert-Equal $weatherVectorPaths.Count $weatherSvgPaths.Count "$weatherOutput path count"
    for ($weatherPathIndex = 0; $weatherPathIndex -lt $weatherSvgPaths.Count; $weatherPathIndex++) {
        $weatherSourcePath = $weatherSvgPaths[$weatherPathIndex]
        $weatherDestinationPath = $weatherVectorPaths[$weatherPathIndex]
        Assert-Equal $weatherDestinationPath.GetAttribute('pathData', $weatherAndroidNamespace) $weatherSourcePath.GetAttribute('d') 'Path data'
        $weatherExpectedFill = if ($weatherSourcePath.HasAttribute('fill')) { $weatherSourcePath.GetAttribute('fill') } else { $weatherSvg.svg.GetAttribute('fill') }
        if ($weatherExpectedFill -eq 'none') { $weatherExpectedFill = '#00000000' }
        Assert-Equal $weatherDestinationPath.GetAttribute('fillColor', $weatherAndroidNamespace) $weatherExpectedFill 'Fill color'
        Assert-Equal $weatherDestinationPath.GetAttribute('strokeColor', $weatherAndroidNamespace) $weatherSourcePath.GetAttribute('stroke') 'Stroke color'
        Assert-Equal $weatherDestinationPath.GetAttribute('strokeWidth', $weatherAndroidNamespace) $weatherSourcePath.GetAttribute('stroke-width') 'Stroke width'
        $weatherExpectedRule = if ($weatherSourcePath.GetAttribute('fill-rule') -eq 'evenodd') { 'evenOdd' } else { '' }
        Assert-Equal $weatherDestinationPath.GetAttribute('fillType', $weatherAndroidNamespace) $weatherExpectedRule 'Fill rule'
        $weatherVerifiedPathCount++
    }
    $weatherSourceClips = $weatherSvg.SelectNodes("//*[local-name()='clipPath']/*[local-name()='path']")
    $weatherDestinationClips = $weatherVector.SelectNodes('//clip-path')
    Assert-Equal $weatherDestinationClips.Count $weatherSourceClips.Count 'Clip count'
    for ($weatherClipIndex = 0; $weatherClipIndex -lt $weatherSourceClips.Count; $weatherClipIndex++) {
        Assert-Equal $weatherDestinationClips[$weatherClipIndex].GetAttribute('pathData', $weatherAndroidNamespace) $weatherSourceClips[$weatherClipIndex].GetAttribute('d') 'Clip path data'
        $weatherVerifiedClipCount++
    }
}
Write-Output "Verified $($weatherSvgFiles.Count) vectors: $weatherVerifiedPathCount exact paths and $weatherVerifiedClipCount exact clipping paths, with original palettes, strokes, fill rules, and viewports."
