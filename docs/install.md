# Install AttuneTV

[← Back to AttuneTV](../README.md) · [Building from source instead?](../CONTRIBUTING.md)

You do not need to write code or install Android Studio. This guide uses a small tool called **ADB** to send the app from your computer to your TV. Once setup is complete, you can disconnect the computer and use the remote normally.

## Before you start

Have these ready:

- An **Android-based Fire TV**, its remote and a Mac, Windows or Linux computer on the **same home network**.
- Internet access to download the APK and ADB. **No GitHub account is needed.**
- A few minutes when nobody is watching the TV.

**Tested device:** Insignia NS65-UQFL26, Fire OS 8.1.6.4. The app requires Android 7.1 or newer; that minimum is not a guarantee of launcher compatibility on every Fire OS version. Non-Android Fire TV devices cannot run this APK. Other models and firmware versions need testing.

**Official releases use a consistent signing identity.** Keep using the official release APK for future updates. If you installed an earlier development or CI build, its signing key may differ; read [Updating](#updating) before replacing it.

## 1. Download the app and ADB

1. **[Download AttuneTV.apk](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV.apk)** to your computer. This is the installable app; no login, source-code ZIP or build step is needed.
2. Optionally read the [release notes](https://github.com/thellis954/attunetv/releases/latest). The same page includes `SHA256SUMS.txt` and `release-info.json` so you can check the file, app version and source commit.
3. Download [Android SDK Platform-Tools for your computer](https://developer.android.com/tools/releases/platform-tools) from Google and unzip it. This is ADB; you do not need the full Android SDK.
4. Move **`AttuneTV.apk` into the extracted `platform-tools` folder**. Keep the folder open.

<details>
<summary><strong>Optional: check the APK download</strong></summary>

Download `SHA256SUMS.txt` from the same release. On Mac, run `shasum -a 256 AttuneTV.apk`; on Linux, run `sha256sum AttuneTV.apk`; on Windows PowerShell, run `Get-FileHash .\AttuneTV.apk -Algorithm SHA256`. Compare the result with the checksum file. The release metadata also records the signing certificate and source commit.

</details>

## 2. Prepare your TV

On the TV, open **Settings → Device & Software → About**. Some models call this **My Fire TV**.

1. If Developer Options is hidden, highlight the device name and press **OK seven times**.
2. Go back to **Developer Options** and enable **ADB Debugging**.
3. Open **About → Network** and write down the TV's **IP address**. Use the IP address, not the gateway or DNS address.

See [Amazon's connection guide](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html) if your menus differ.

## 3. Connect and install

Choose your computer below. Run each command in order. Keep the TV visible so you can approve its **Allow USB debugging?** prompt.

<details open>
<summary><strong>Mac or Linux — Terminal</strong></summary>

Open Terminal. Type `cd ` (including the space), drag the extracted **platform-tools** folder into Terminal, and press Enter. Alternatively, enter its full path after `cd`.

Set the TV address below, replacing the example `192.168.1.50` with **your TV's IP**:

```bash
tv="192.168.1.50:5555"
./adb connect "$tv"
```

**Stop here and approve the prompt on the TV with your remote.** Then run:

```bash
./adb devices
```

Your TV must show **`device`**, not `unauthorized` or `offline`. If it does, install:

```bash
./adb -s "$tv" install -r AttuneTV.apk
```

Wait for **`Success`**, then open AttuneTV:

```bash
./adb -s "$tv" shell am start -n com.attunetv.launcher/.HomeActivity
```

</details>

<details>
<summary><strong>Windows — PowerShell</strong></summary>

In File Explorer, open the extracted **platform-tools** folder. Click its address bar, type `powershell` and press Enter.

Set the TV address below, replacing the example `192.168.1.50` with **your TV's IP**:

```powershell
$tv = "192.168.1.50:5555"
.\adb.exe connect $tv
```

**Stop here and approve the prompt on the TV with your remote.** Then run:

```powershell
.\adb.exe devices
```

Your TV must show **`device`**, not `unauthorized` or `offline`. If it does, install:

```powershell
.\adb.exe -s $tv install -r AttuneTV.apk
```

Wait for **`Success`**, then open AttuneTV:

```powershell
.\adb.exe -s $tv shell am start -n com.attunetv.launcher/.HomeActivity
```

</details>

If installation fails, stop and use [Troubleshooting](#troubleshooting). Do not continue to the Home setup until the app installs successfully.

## 4. Make the Home button open AttuneTV

This step enables AttuneTV's accessibility service. **It keeps any accessibility services you already use.** It does not disable or uninstall Amazon packages.

First, enter the TV's command shell from the same terminal window:

**Mac / Linux:**

```bash
./adb -s "$tv" shell
```

**Windows PowerShell:**

```powershell
.\adb.exe -s $tv shell
```

The prompt will change because you are now entering commands **on the TV**. Paste this entire block; it is the same for all three computer types:

```sh
service='com.attunetv.launcher/com.attunetv.launcher.HomeButtonService'
current=$(settings get secure enabled_accessibility_services | tr -d '\r')
printf 'Before: %s\n' "$current"
case "$current" in
  ''|null) updated="$service" ;;
  *)
    case ":$current:" in
      *":$service:"*) updated="$current" ;;
      *) updated="$current:$service" ;;
    esac
    ;;
esac
settings put secure enabled_accessibility_services "$updated"
settings put secure accessibility_enabled 1
printf 'After: '
settings get secure enabled_accessibility_services
exit
```

The **After** line should contain `com.attunetv.launcher/com.attunetv.launcher.HomeButtonService` along with any entries from **Before**. If you see a permission error, stop and [report your model and Fire OS version](https://github.com/thellis954/attunetv/issues/new).

Press **Home** on the remote. AttuneTV should appear; a brief flash of Amazon Home is normal. You can also launch AttuneTV from **Your Apps** without enabling Home redirection.

## 5. Make it yours

On AttuneTV, open **Settings → Set up from your phone**. Scan the QR code using a phone on the same Wi-Fi. Leave the TV on that setup screen while you edit.

- Choose your favorite apps, hide the ones you do not use and set your greeting.
- Set your weather location and temperature units.
- Pick your background, clock format and ambient-mode timeout.
- Optionally add calendar feeds for the “Up next” widget.

The setup page runs locally over HTTP and requires the random PIN shown on the TV. Use trusted home Wi-Fi. The page closes when you leave the TV's phone-setup screen. **This QR code is for launcher settings, separate from Ask → Pair phone for AI chat.**

<details>
<summary><strong>Adding a calendar</strong></summary>

Paste an iCal/ICS subscription link into phone setup, one per line. Use your calendar provider's sharing or subscription settings to obtain the link. Some providers offer private subscription URLs; others require publishing the calendar. Choose the sharing level deliberately and treat private feed URLs like passwords. Do not put them in bug reports or screenshots.

</details>

**Done.** Close the terminal and shut down the computer if you like. The home screen runs on the TV. For optional chat, see [Ask with AttuneTV](../README.md#meet-ask-with-attunetv); select Ask → Pair phone for the guided API-key connection.

## Updating

Download a newer APK, put it in `platform-tools`, reconnect using Step 3 and run the same **`install -r`** command. Official release updates share the pinned signing key and preserve app data. Open AttuneTV again and press Home.

To confirm what is actually installed, use the same shell command as Step 4, then run:

```sh
dumpsys package com.attunetv.launcher | grep versionName
exit
```

If the error says **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**, the signing key may differ. Stop: uninstalling would erase AttuneTV preferences and its saved chat pairing. Official releases match the original maintainer-installed 0.4.1 signing identity, but not every early CI or locally built APK. Prefer a build signed with the same key. If you deliberately choose to start over, note your settings first, use the removal instructions below and install again. Do not uninstall Amazon apps to solve this.

## Go back or uninstall

**Just want Amazon Home back?** In AttuneTV Settings, turn **Redirect Home button** off. You can still open AttuneTV like any other app.

**Want to remove AttuneTV completely?** Enter the TV shell as in Step 4. This block removes only AttuneTV's service entry, leaving other entries and the global accessibility switch alone:

```sh
service='com.attunetv.launcher/com.attunetv.launcher.HomeButtonService'
current=$(settings get secure enabled_accessibility_services | tr -d '\r')
printf 'Before: %s\n' "$current"
if [ "$current" != null ] && [ -n "$current" ]; then
  remaining=''
  saved_ifs="$IFS"
  IFS=:
  set -f
  for entry in $current; do
    [ "$entry" = "$service" ] && continue
    [ "$entry" = 'com.attunetv.launcher/.HomeButtonService' ] && continue
    [ -z "$entry" ] && continue
    remaining="${remaining:+$remaining:}$entry"
  done
  set +f
  IFS="$saved_ifs"
  if [ -n "$remaining" ]; then
    settings put secure enabled_accessibility_services "$remaining"
  else
    settings delete secure enabled_accessibility_services
  fi
fi
printf 'After: '
settings get secure enabled_accessibility_services
exit
```

Then uninstall from the **computer** terminal. This deletes AttuneTV's own data:

**Mac / Linux:** `./adb -s "$tv" uninstall com.attunetv.launcher`

**Windows:** `.\adb.exe -s $tv uninstall com.attunetv.launcher`

## Troubleshooting

| What you see | What to do |
| --- | --- |
| Download link does not work | Open the [Releases page](https://github.com/thellis954/attunetv/releases), expand the newest release’s **Assets** and choose `AttuneTV.apk`. A GitHub account is not required. |
| `adb` not found | Open your terminal in the extracted `platform-tools` folder. Use `./adb` on Mac/Linux or `.\adb.exe` on Windows. |
| Cannot connect / connection refused | Recheck the TV's IP, ADB Debugging and shared network. Guest-network isolation or a VPN can prevent the connection. |
| `unauthorized` | Approve the debugging prompt on the TV. Do not continue until `adb devices` shows `device`. |
| `offline` | Run `adb disconnect` for the TV address using your OS's ADB prefix, reconnect and check the TV prompt. |
| Signature or version downgrade error | Follow [Updating](#updating). Do not automatically uninstall or force a downgrade. |
| Home still opens Amazon | Confirm **Redirect Home button** is on in AttuneTV Settings. Check the service's **After** value in Step 4. Fire OS updates can remove the accessibility entry. |
| Amazon Home flashes briefly | Expected: AttuneTV responds after Amazon's Home activity opens. |
| Phone setup page will not load | Keep the QR screen open and use the same trusted Wi-Fi. Rescan the current QR code; an old PIN may no longer work. |
| Chat is unavailable inside Netflix or another player | Streaming integration is gated pending native device tests. Use the panel preview in Ask setup; see [current status](standalone-chat.md). |
| Playback controls stay visible | Close chat and note the app/version. Report the behavior before making system changes; do not disable Amazon packages. |
| Tiles or text are clipped | Include a screenshot, TV model, Fire OS version and AttuneTV version in an issue. Developers can also collect `wm size` and `wm density` without changing them. |

[Need help? Open an issue →](https://github.com/thellis954/attunetv/issues/new)
