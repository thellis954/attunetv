# Install/update only AttuneTV. No uninstall, downgrade or TV setting changes.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
function Invoke-Adb {
    & $script:Adb @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed. Check the TV prompt, connection, or signature error above. No automatic reset was attempted." }
}
try {
    Write-Host 'AttuneTV setup - install or update without erasing settings'
    if (!(Test-Path AttuneTV.apk) -or !(Test-Path SHA256SUMS.txt)) { throw 'Extract the entire AttuneTV-Setup.zip first.' }
    $checksum = @(Get-Content SHA256SUMS.txt | Where-Object { $_ -match '^([a-f0-9]{64})  AttuneTV\.apk$' })
    if ($checksum.Count -ne 1) { throw 'Missing or invalid APK checksum.' }
    $expected = $checksum[0].Split(' ')[0]
    if ((Get-FileHash AttuneTV.apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'APK checksum mismatch. Download a fresh setup ZIP from GitHub Releases.' }
    if (Test-Path '.\platform-tools\adb.exe') { $script:Adb = Join-Path $PSScriptRoot 'platform-tools\adb.exe' }
    else {
        $tool = Get-Command adb -ErrorAction SilentlyContinue
        if (!$tool) { throw 'Download Google Platform-Tools at https://developer.android.com/tools/releases/platform-tools . Extract platform-tools into this setup folder and retry.' }
        $script:Adb = $tool.Source
    }
    Write-Host 'Enable ADB debugging on your TV. Keep the TV visible for its approval prompt.'
    $address = Read-Host 'TV IPv4 address (optionally :port, default 5555)'
    if ($address -notmatch '^\d+\.\d+\.\d+\.\d+(:\d+)?$') { throw 'Enter an IPv4 address, optionally followed by :port.' }
    if (!$address.Contains(':')) { $address += ':5555' }
    Invoke-Adb connect $address
    $null = Read-Host 'Approve Allow USB debugging on the TV, then press Enter here'
    $state = (Invoke-Adb -s $address get-state | Out-String).Trim()
    if ($state -ne 'device') { throw 'TV must show device, not unauthorized or offline.' }
    Invoke-Adb -s $address install -r AttuneTV.apk
    $version = (Get-Content VERSION.txt -Raw).Trim()
    $installed = Invoke-Adb -s $address shell dumpsys package com.attunetv.launcher | Out-String
    if ($installed -notmatch ('versionName=' + [regex]::Escape($version) + '(\s|$)')) { throw 'Installed version does not match this setup bundle.' }
    Write-Host "Verified installed version: $version"
    Invoke-Adb -s $address shell am start -n com.attunetv.launcher/.HomeActivity
    Write-Host 'Open Ask -> Pair phone to connect your AI companion.'
    Write-Host 'For optional Home-button setup, follow docs/install.md section 4. No TV settings or other apps were changed.'
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
}
