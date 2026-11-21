#!/usr/bin/env bash
# Install/update only AttuneTV. No uninstall, downgrade or TV setting changes.
set -euo pipefail
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
fail() { printf '\n%s\n' "$*" >&2; exit 1; }
trap 'printf "\nSetup stopped. No automatic uninstall or reset was attempted. See START-HERE.md.\n" >&2' ERR
printf 'AttuneTV setup — install or update without erasing settings\n'
[ -f AttuneTV.apk ] && [ -f SHA256SUMS.txt ] || fail 'Extract the entire AttuneTV-Setup.zip first.'
expected=$(awk '$2 == "AttuneTV.apk" {print $1}' SHA256SUMS.txt)
[[ "$expected" =~ ^[a-f0-9]{64}$ ]] || fail 'Missing or invalid APK checksum.'
if command -v shasum >/dev/null; then actual=$(shasum -a 256 AttuneTV.apk); else actual=$(sha256sum AttuneTV.apk); fi
[ "${actual%% *}" = "$expected" ] || fail 'APK checksum mismatch. Download a fresh setup ZIP from GitHub Releases.'
if [ -x ./platform-tools/adb ]; then adb=./platform-tools/adb
elif command -v adb >/dev/null; then adb=$(command -v adb)
else fail 'Download Google Platform-Tools: https://developer.android.com/tools/releases/platform-tools . Extract platform-tools into this setup folder, then run setup again.'; fi
printf 'Enable ADB debugging on your TV. Keep the TV visible for its approval prompt.\n'
read -r -p 'TV IPv4 address (optionally :port, default 5555): ' address
[[ "$address" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(:[0-9]+)?$ ]] || fail 'Enter an IPv4 address, optionally followed by :port.'
case "$address" in *:*) ;; *) address="$address:5555" ;; esac
"$adb" connect "$address"
read -r -p 'Approve Allow USB debugging on the TV, then press Enter here: ' ignored
state=$("$adb" -s "$address" get-state 2>/dev/null) || fail 'TV is unauthorized, offline or unreachable. Approve the TV prompt and retry.'
[ "$(printf '%s' "$state" | tr -d '\r\n')" = device ] || fail 'TV must show device before installation.'
printf 'Installing the bundled release. A signature mismatch stops setup; your data is not erased.\n'
"$adb" -s "$address" install -r AttuneTV.apk
version=$(tr -d '\r\n' < VERSION.txt)
installed=$("$adb" -s "$address" shell dumpsys package com.attunetv.launcher)
printf '%s\n' "$installed" | grep -Eq "versionName=${version//./\\.}([[:space:]]|$)" || fail 'Installed version did not match this setup bundle. See the manual guide.'
printf 'Verified installed version: %s\n' "$version"
"$adb" -s "$address" shell am start -n com.attunetv.launcher/.HomeActivity
printf '\nOpen Ask → Pair phone to connect your AI companion.\nFor optional Home-button setup, follow docs/install.md section 4.\nNo TV settings or other apps were changed. You can close this computer after setup.\n'
