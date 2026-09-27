# Mechanical conversion of the vendored Google set-5 SVG subset. See NOTICE.md.
# This intentionally rejects unknown SVG constructs instead of silently losing artwork.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$weatherVendorRoot = $PSScriptRoot
$weatherProjectRoot = (Resolve-Path (Join-Path $weatherVendorRoot '../..')).Path
$weatherDrawableRoot = Join-Path $weatherProjectRoot 'app/src/main/res/drawable'
$weatherAndroidNamespace = 'http://schemas.android.com/apk/res/android'

function Set-AndroidAttribute($node, [string]$name, [string]$value) {
    $attribute = $node.OwnerDocument.CreateAttribute('android', $name, $weatherAndroidNamespace)
    $attribute.Value = $value
    [void]$node.Attributes.Append($attribute)
}

function Assert-Attributes($node, [string[]]$allowed) {
    foreach ($attribute in $node.Attributes) {
        if ($attribute.Name -notin $allowed) { throw "Unsupported SVG attribute: $($node.Name).$($attribute.Name)" }
    }
}

function Convert-Color([string]$value) {
    if ($value -eq 'none') { return '#00000000' }
    if ($value -match '^#[\da-fA-F]{6}$') { return $value }
    if ($value -match '^#([\da-fA-F])([\da-fA-F])([\da-fA-F])$') {
        return '#' + $Matches[1] + $Matches[1] + $Matches[2] + $Matches[2] + $Matches[3] + $Matches[3]
    }
    throw "Unsupported SVG color: $value"
}

function Convert-SvgChildren($source, $target, $svgDocument, [string]$inheritedFill) {
    foreach ($node in $source.ChildNodes) {
        if ($node.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
        switch ($node.LocalName) {
            'path' {
                Assert-Attributes $node @('d', 'fill', 'stroke', 'stroke-width', 'fill-rule', 'clip-rule')
                $path = $target.OwnerDocument.CreateElement('path')
                Set-AndroidAttribute $path 'pathData' $node.GetAttribute('d')
                $fill = if ($node.HasAttribute('fill')) { $node.GetAttribute('fill') } else { $inheritedFill }
                Set-AndroidAttribute $path 'fillColor' (Convert-Color $fill)
                if ($node.HasAttribute('stroke')) {
                    Set-AndroidAttribute $path 'strokeColor' (Convert-Color $node.GetAttribute('stroke'))
                    $width = if ($node.HasAttribute('stroke-width')) { $node.GetAttribute('stroke-width') } else { '1' }
                    Set-AndroidAttribute $path 'strokeWidth' $width
                }
                if ($node.HasAttribute('fill-rule')) {
                    $rule = $node.GetAttribute('fill-rule')
                    if ($rule -notin @('evenodd', 'nonzero')) { throw "Unsupported fill rule: $rule" }
                    Set-AndroidAttribute $path 'fillType' $(if ($rule -eq 'evenodd') { 'evenOdd' } else { 'nonZero' })
                }
                [void]$target.AppendChild($path)
            }
            'g' {
                Assert-Attributes $node @('clip-path')
                $group = $target.OwnerDocument.CreateElement('group')
                if ($node.HasAttribute('clip-path')) {
                    if ($node.GetAttribute('clip-path') -notmatch '^url\(#([\w-]+)\)$') { throw 'Unsupported clip reference' }
                    $clipId = $Matches[1]
                    $clipDefinition = $svgDocument.SelectSingleNode("//*[local-name()='clipPath' and @id='$clipId']")
                    if ($null -eq $clipDefinition) { throw "Missing clip definition: $clipId" }
                    Assert-Attributes $clipDefinition @('id')
                    if ($clipDefinition.ChildNodes.Count -ne 1 -or $clipDefinition.FirstChild.LocalName -ne 'path') {
                        throw 'Only single-path clipping is supported'
                    }
                    $clipSource = $clipDefinition.FirstChild
                    Assert-Attributes $clipSource @('d', 'fill', 'clip-rule')
                    $clip = $target.OwnerDocument.CreateElement('clip-path')
                    Set-AndroidAttribute $clip 'pathData' $clipSource.GetAttribute('d')
                    if ($clipSource.GetAttribute('clip-rule') -eq 'evenodd') { Set-AndroidAttribute $clip 'fillType' 'evenOdd' }
                    [void]$group.AppendChild($clip)
                }
                Convert-SvgChildren $node $group $svgDocument $inheritedFill
                [void]$target.AppendChild($group)
            }
            'defs' { } # Only referenced clip definitions are included above.
            default { throw "Unsupported SVG element: $($node.LocalName)" }
        }
    }
}

$weatherSvgFiles = Get-ChildItem (Join-Path $weatherVendorRoot 'set-5') -Recurse -Filter '*.svg'
foreach ($weatherSvgFile in $weatherSvgFiles) {
    [xml]$weatherSvg = Get-Content -LiteralPath $weatherSvgFile.FullName -Raw
    Assert-Attributes $weatherSvg.svg @('width', 'height', 'viewBox', 'fill', 'xmlns')
    if ($weatherSvg.svg.GetAttribute('viewBox') -ne '0 0 48 48') { throw 'Expected a 48 × 48 source viewport' }
    $weatherVector = [System.Xml.XmlDocument]::new()
    [void]$weatherVector.AppendChild($weatherVector.CreateXmlDeclaration('1.0', 'utf-8', $null))
    [void]$weatherVector.AppendChild($weatherVector.CreateComment(' Generated from Google Weather set-5; see third_party/google-weather-icons/NOTICE.md. '))
    $weatherRoot = $weatherVector.CreateElement('vector')
    Set-AndroidAttribute $weatherRoot 'width' '48dp'
    Set-AndroidAttribute $weatherRoot 'height' '48dp'
    Set-AndroidAttribute $weatherRoot 'viewportWidth' '48'
    Set-AndroidAttribute $weatherRoot 'viewportHeight' '48'
    [void]$weatherVector.AppendChild($weatherRoot)
    Convert-SvgChildren $weatherSvg.svg $weatherRoot $weatherSvg $weatherSvg.svg.GetAttribute('fill')
    $weatherTheme = $weatherSvgFile.Directory.Name
    $weatherOutput = Join-Path $weatherDrawableRoot ('weather_google_' + $weatherSvgFile.BaseName + '_' + $weatherTheme + '.xml')
    $weatherWriterSettings = [System.Xml.XmlWriterSettings]::new()
    $weatherWriterSettings.Indent = $true
    $weatherWriterSettings.Encoding = [System.Text.UTF8Encoding]::new($false)
    $weatherWriterSettings.NewLineChars = "`n"
    $weatherWriter = [System.Xml.XmlWriter]::Create($weatherOutput, $weatherWriterSettings)
    try { $weatherVector.Save($weatherWriter) } finally { $weatherWriter.Dispose() }
}
Write-Output "Converted $($weatherSvgFiles.Count) original SVGs without altering path data or palette."
