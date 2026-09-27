# Reads a public APK and a local release tag. Never reads a keystore or password.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SignedApk,
    [string]$SdkRoot = $env:ANDROID_HOME
)

$ErrorActionPreference = 'Stop'
$releaseProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$releasePackage = 'com.galaxyrio.gracelauncher'
$releaseVersion = '1.0.0'
$releaseCode = '1'
$releaseTag = "v$releaseVersion"

if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$releaseApkPath = (Resolve-Path -LiteralPath $SignedApk).Path
$releaseApkSigner = Join-Path $SdkRoot 'build-tools/34.0.0/apksigner.bat'
$releaseAapt = Join-Path $SdkRoot 'build-tools/36.1.0/aapt.exe'
foreach ($releaseTool in @($releaseApkSigner, $releaseAapt)) {
    if (-not (Test-Path -LiteralPath $releaseTool)) { throw "Missing Android SDK tool: $releaseTool" }
}

$releaseVerification = (& $releaseApkSigner verify --verbose --print-certs $releaseApkPath 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed. $releaseVerification" }
if ($releaseVerification -match 'CN=Android Debug') { throw 'A debug-signed APK cannot be used for this release.' }
$releaseFingerprints = [regex]::Matches($releaseVerification, 'Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9]{64})')
if ($releaseFingerprints.Count -ne 1) { throw 'Expected exactly one APK signing certificate.' }
$releaseFingerprint = $releaseFingerprints[0].Groups[1].Value.ToLowerInvariant()

$releaseBadging = (& $releaseAapt dump badging $releaseApkPath 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read APK package/version information.' }
if ($releaseBadging -notmatch "(?m)^package: name='$([regex]::Escape($releasePackage))' versionCode='$releaseCode' versionName='$([regex]::Escape($releaseVersion))'") {
    throw "The APK must be $releasePackage, versionName $releaseVersion, versionCode $releaseCode."
}

Push-Location $releaseProjectRoot
try {
    $releaseDirty = git status --porcelain
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git status.' }
    if ($releaseDirty) { throw 'Commit the release source and store assets before generating the submission metadata.' }
    $releaseCommit = (git rev-parse --verify "refs/tags/${releaseTag}^{commit}" 2>$null)
    if ($LASTEXITCODE -ne 0 -or $releaseCommit -notmatch '^[a-f0-9]{40}$') {
        throw "Create the release tag $releaseTag first."
    }
    $releaseHead = git rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $releaseHead -ne $releaseCommit) { throw "Check out the source tagged $releaseTag first." }

    $releaseTemplate = Get-Content -LiteralPath (Join-Path $releaseProjectRoot "fdroid/$releasePackage.yml.template") -Raw
    $releaseMetadata = $releaseTemplate.Replace("'@RELEASE_COMMIT@'", $releaseCommit)
    $releaseMetadata = $releaseMetadata.Replace("'@SIGNING_CERTIFICATE_SHA256@'", $releaseFingerprint)
    # Draft comments are not needed in the file copied to fdroiddata.
    $releaseMetadata = [regex]::Replace($releaseMetadata, '(?m)^#.*\r?\n', '')
    if ($releaseMetadata -match '@[A-Z0-9_]+@') { throw 'Unfilled template field.' }
    $releaseOutputDirectory = Join-Path $releaseProjectRoot 'build/fdroid'
    New-Item -ItemType Directory -Path $releaseOutputDirectory -Force | Out-Null
    $releaseOutputPath = Join-Path $releaseOutputDirectory "$releasePackage.yml"
    [System.IO.File]::WriteAllText($releaseOutputPath, $releaseMetadata.Replace("`r`n", "`n"), [System.Text.UTF8Encoding]::new($false))
    Write-Output "Created: $releaseOutputPath"
    Write-Output "Release commit: $releaseCommit"
    Write-Output "Public signing certificate SHA-256: $releaseFingerprint"
    Write-Output 'This verifies release identity and signature. F-Droid CI still needs to verify the build and reproducibility.'
} finally {
    Pop-Location
}
