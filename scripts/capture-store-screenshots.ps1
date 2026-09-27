[CmdletBinding()]
param(
    [string]$Device = 'emulator-5554',
    [string]$SdkRoot = $env:ANDROID_HOME
)

$ErrorActionPreference = 'Stop'
$captureProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$captureAdb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$captureOriginalSerial = $env:ANDROID_SERIAL
Push-Location $captureProjectRoot
try {
    $env:ANDROID_SERIAL = $Device
    & "$captureProjectRoot/gradlew.bat" :app:connectedDebugAndroidTest `
        '-Pandroid.testInstrumentationRunnerArguments.class=com.galaxyrio.gracelauncher.StoreScreenshotTest' `
        '-Pandroid.testInstrumentationRunnerArguments.storeScreenshots=true'
    if ($LASTEXITCODE -ne 0) { throw 'Screenshot capture failed; existing store images were not changed.' }

    $captureDestination = Join-Path $captureProjectRoot 'fastlane/metadata/android/en-US/images/phoneScreenshots'
    New-Item -ItemType Directory -Path $captureDestination -Force | Out-Null
    foreach ($captureName in @('01-home.png', '02-agenda.png', '03-music.png', '04-app-list-c.png')) {
        & $captureAdb -s $Device pull "/sdcard/Download/grace-launcher-store-screenshots/$captureName" (Join-Path $captureDestination $captureName)
        if ($LASTEXITCODE -ne 0) { throw "Could not retrieve $captureName" }
    }
    Write-Output "Saved four English screenshots to $captureDestination"
} finally {
    $env:ANDROID_SERIAL = $captureOriginalSerial
    Pop-Location
}
