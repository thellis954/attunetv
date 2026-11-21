# AttuneTV setup

This bundle includes the current signed Android APK, the configured unsigned Samsung WGT, checksums and installation guides. No GitHub account, source build or always-on computer is required for normal use. AI uses your provider API key and separate API billing.

## Fire TV (Android based)

1. Extract this ZIP completely. Download Google's Platform-Tools for your computer from https://developer.android.com/tools/releases/platform-tools . Extract the `platform-tools` folder **inside this setup folder** (or use an existing `adb` on PATH).
2. Enable **Settings → Device & Software / My Fire TV → Developer Options → ADB debugging**. If Developer Options is hidden, select the device name under About seven times. Note the TV IP under About → Network. Use a time when nobody is watching.
3. Run the installer for your computer:
   - **Mac:** open Terminal in this folder and run `bash install.sh`. You can also open `Install-Mac.command` if macOS permits it; use the Terminal command if the downloaded wrapper is blocked. Do not disable Gatekeeper.
   - **Linux:** open a terminal here and run `bash install.sh`.
   - **Windows:** open PowerShell here. Review `Install-Windows.ps1`, then run `powershell -NoProfile -ExecutionPolicy Bypass -File .\Install-Windows.ps1`. This applies only to this PowerShell process; it does not change your saved execution policy.
4. Enter the TV IP and approve the debugging prompt on the TV. The installer checks the APK checksum, installs with `-r`, verifies the version and opens AttuneTV. It never uninstalls an app or changes TV settings. Stop on a signature mismatch; see `docs/install.md` before choosing any reset.
5. Open **Ask → Pair phone**. Scan, approve the matching number, then follow the provider setup on your phone. Full guide: `docs/ai-setup.md`.

For optional Home-button redirection, follow `docs/install.md` section 4. It preserves other accessibility services. The separate Settings → Set up from your phone QR is for launcher personalization, not AI pairing.

## Samsung built-in apps / Tizen

Use `AttuneTV-Samsung-UNSIGNED.wgt` and `docs/samsung-install.md`. The service is already configured. You do **not** need Node, Git or a source build. You **do** need Tizen Studio and a Samsung certificate for your own TV, then Permit to install, sign and install. The Android installers above are not for Tizen. Never use someone else's device-bound certificate.

## Android TV / Google TV

Read `docs/android-tv-install.md` first. Compatibility is experimental. Wireless-debugging pairing may be required before using the Android installer. Do not apply Fire TV Home-redirection commands on these devices.

## Updates and help

Download a new complete setup ZIP for each update: https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV-Setup.zip . An old downloaded ZIP does not update itself. Keep the same Samsung author certificate for future WGT updates. Official APK updates use the pinned release signer.

Version: see VERSION.txt, release-info.json and samsung-release-info.json. Checksums: SHA256SUMS.txt. Report errors without keys or pairing codes: https://github.com/thellis954/attunetv/issues . Current platform limits and preview acceptance: docs/setup.md and docs/ai-setup.md.
