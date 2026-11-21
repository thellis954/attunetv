# Install the Samsung companion

[← Choose your screen](setup.md) · [AI setup](ai-setup.md) · [Developer/service guide](../samsung/README.md)

**What you get:** a cinematic Home inside AttuneTV with a photo, clock, installed apps, favorites, app search and an **Ask** shortcut to TV/phone chat. The phone conversation stays available while you watch through Samsung's built-in streaming apps. It does not replace Smart Hub or overlay Netflix, Prime Video or other players.

**Current download: 0.2.0**, configured for the hosted companion. It is unsigned until you apply your own certificate. **Hardware checkpoint:** 0.1.0 was signed, installed and launched on a **Samsung QN65Q65FN / Tizen 4.0** on November 20, 2026. Visual quality, physical phone pairing and real AI answers are separate acceptance checks still in progress. Other Tizen models are unverified. There is no Samsung Store listing or universal one-click installer yet.

**Want to try chat first?** Open the [hosted preview](https://attunetv-samsung.vercel.app/tv) on your computer and [pair your phone](ai-setup.md). Native Samsung installation below is more involved, but you only need the computer for setup and updates—not normal viewing.

## Before you start

- Samsung Tizen TV and remote, with time available for setup and a restart.
- A Mac, Windows or Linux computer on the same trusted home network.
- A Samsung account for signing. Enter its password only in Samsung's sign-in window.
- [Tizen Studio](https://developer.tizen.org/development/tizen-studio/download), with the **Samsung TV Extension** and **Samsung Certificate Extension** installed through Package Manager. Use Samsung's [SDK setup guide](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/installing-tv-sdk.html) if your installer differs.
- For a source build: Git, **Node 24** and **Python 3**. You do not need an AI key to build or install.

The `.apk` download is for Android/Fire TV. Samsung needs a **`.wgt` signed for your TV's device ID (DUID)**. Someone else's development-signed package is not a universal Samsung download.

## 1. Record the two IP addresses

On the TV, open **Settings → General → Network → Network Status → IP Settings** and note the TV IP. On your computer, open its network settings and note its IP on the same Wi-Fi. The TV's **Host PC IP** needs the computer's address—not the TV address, gateway or DNS server.

Examples in this guide use TV `192.168.1.50` and computer `192.168.1.25`. Replace them with yours. If you change networks later, check both again.

## 2. Open the hidden Developer Mode dialog

On the tested 2018 model:

1. Press **Home → Apps**. Stay on the **main Apps storefront with banners**.
2. Enter the five number keys **1, 2, 3, 4, 5** in order. This opens a hidden dialog; Developer Mode is not an item in the app-management list.
3. Set Developer Mode **On**.
4. Move focus to **Host PC IP**. Select a number box with the remote's center/OK button, enter that part of the computer's IP, and move Right to the next box. A visible keypad is not always shown. Check all four values before saving.
5. Select **OK** and restart as prompted. Reopen Apps; it should display **DEVELOP MODE**.

**Remote has no number keys?** Try its **123** virtual keypad. If that button does nothing, do not repeatedly change app settings or assume the remote is unpaired. Use a compatible numeric remote or network remote capable of sending individual number keys. In our installation, a network remote sent `1–2–3–4–5` and the IP digits after the field was selected.

Newer models may put the sequence under **Apps Settings**. See [Samsung's model-dependent instructions](https://developer.samsung.com/smarttv/develop/getting-started/using-sdk/tv-device.html). On our 2018 TV, the gear opened per-app Delete/Update controls; backing out to the storefront was the working route.

## 3. Connect the computer

Use Tizen Studio **Tools → Device Manager → Remote Device Manager** to add the TV IP and port **26101**, then connect. Or use SDB from the SDK:

```sh
sdb connect 192.168.1.50:26101
sdb devices
```

The TV must be listed as **device**. SDK commands below assume `tizen` and `sdb` are on PATH. They live in `tizen-studio/tools/ide/bin` and `tizen-studio/tools`; Windows uses the corresponding `.bat` / `.exe` launchers. Prefer Device Manager if you do not want to configure PATH.

## 4. Create your Samsung signing profile

Open **Tizen Certificate Manager**, then:

1. Click **+ → Samsung → TV**. Name the profile **AttuneTV-Samsung**.
2. Create an author certificate. Enter your name and choose a certificate password; save it in your password manager. This password protects the signing files and is separate from your Samsung account password.
3. Sign in to Samsung when prompted. On **Congratulations**, the author certificate has been created. The optional **Backup path** saves an extra copy; the screen shows the existing certificate folder. Keep any backup private and outside the repository.
4. Continue to create a **Public** distributor certificate. With the TV connected, its DUID should appear automatically. Select your TV; do not substitute its IP, serial number or network UUID.
5. Complete the wizard and **Finish**. The profile should become active.

Keep this author certificate for future updates. Never commit `.p12`, `.pri`, passwords, SDK profile files or device authorization files. [Samsung's certificate guide](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html) covers the wizard and certificate renewal.

## 5. Permit installation—do not skip this

In **Device Manager**, right-click the connected device's filesystem and choose **Permit to install applications** with your profile active. [Samsung documents this step here](https://developer.samsung.com/smarttv/develop/faq/tizen-studio.html).

On our Tizen 4 TV, skipping this caused **install failed[118012]**, even though the signing certificates were valid and contained the correct DUID. Transferring the profile to the TV fixed it. The standard, unmodified SDK signer then installed successfully; no hashing or SDK patch was needed.

<details>
<summary>Command-line fallback for the tested Tizen 4 TV</summary>

Read the TV's advertised staging path:

```sh
sdb -s 192.168.1.50:26101 capability
```

The tested TV reports `sdk_toolpath:/home/owner/share/tmp/sdk_tools`. With **that same value** on your device, send the `device-profile.xml` generated in your Samsung certificate folder:

```sh
sdb -s 192.168.1.50:26101 push "/YOUR/CERTIFICATE/FOLDER/device-profile.xml" /home/owner/share/tmp/sdk_tools/device-profile.xml
```

Use the exact certificate folder shown by your wizard. Do not share its contents. Confirm the transfer succeeds with no path error. `/home/developer` was rejected by our TV; do not keep retrying that path or attempt to change TV filesystem permissions. For another SDK path/model, prefer Device Manager.

</details>

## 6. Get the unsigned package

**Recommended:** [download AttuneTV-Samsung-UNSIGNED.wgt](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV-Samsung-UNSIGNED.wgt), or get it with all instructions in the [setup ZIP](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV-Setup.zip). No GitHub account, Node, Python or source build is required for this download. The hosted origin is already configured. Download checksums and `samsung-release-info.json` from [the same release](https://github.com/thellis954/attunetv/releases/latest). Signing for your own TV is still required.

Put the downloaded WGT in a folder named `dist`, then continue to Step 7. The source-build option below is for developers.

**Build option:** clone the public source (no GitHub account required), then:

```sh
git clone --branch main https://github.com/thellis954/attunetv.git
cd attunetv/samsung
npm ci
npm run check
npm test
```

Mac/Linux:

```sh
ATTUNE_SERVICE_ORIGIN=https://attunetv-samsung.vercel.app npm run build
```

Windows PowerShell:

```powershell
$env:ATTUNE_SERVICE_ORIGIN = "https://attunetv-samsung.vercel.app"
npm run build
```

The output is `dist/AttuneTV-Samsung-0.2.0-UNSIGNED.wgt`. Building without the service origin produces a shell that cannot connect. You do not need to host a server to use the existing preview service.

## 7. Sign, install and open

Copy `AttuneTV-Samsung-UNSIGNED.wgt` (or the versioned file from a source build) to a new file named **AttuneTV-Samsung-0.2.0.wgt** in the same folder. This copy remains unsigned until the next command succeeds. From `samsung/` after a source build (or adjust `dist` to your extracted artifact folder):

```sh
tizen package -t wgt -s AttuneTV-Samsung -- dist/AttuneTV-Samsung-0.2.0.wgt
tizen install -n AttuneTV-Samsung-0.2.0.wgt -t YOUR_DEVICE_NAME -- dist
tizen run -p AttuneTV01.Companion -t YOUR_DEVICE_NAME
```

Replace `YOUR_DEVICE_NAME` with the target name in Device Manager / `sdb devices`; ours was `QN65Q65FN`. Look for **Tizen application is successfully installed**, then **successfully launched**. The standard CLI can sign an existing WGT, so an extra `tizen build-web` step is unnecessary for this already-built static package.

## 8. Pair and settle in

Open **AttuneTV** in the TV's Apps list, choose **Pair phone**, and follow [the AI setup guide](ai-setup.md). Check remote focus, text size and QR readability. Enter the title, episode and progress, then try a real answer after connecting your provider privately.

Return to your streaming app and use the paired phone while watching. Reopen AttuneTV to see the conversation on the TV. The installer computer can shut down after setup.

## Updating, removal and troubleshooting

Use the same author certificate and application ID for updates. Sign a newer WGT, reconnect, install and launch as above. Never automatically uninstall to resolve a certificate error: removal can erase pairing/settings. To remove deliberately, use Samsung Apps' normal Delete option for AttuneTV.

| Problem | Check |
| --- | --- |
| 123 button does nothing | Stay on the main Apps page for the tested 2018 model; try a compatible numeric/network remote. |
| IP field does not type | Focus a box and press center/OK before number entry; verify all four values. |
| Cannot connect | Same network, current TV IP, Developer Mode, correct computer host IP, restart. Avoid guest-network isolation. |
| `118012` / certificate rejection | Correct Samsung profile and DUID, **Permit to install applications**, certificate validity, and matching author identity for updates. Do not remove another app or weaken TV protections. |
| CLI reports Java/JAXB errors | Check your SDK's Java requirements. Our macOS CLI worked with Java 8; this is separate from Android's JDK 17 requirement. |
| `build-web` class-loading failure | Use `npm run build`, then sign the existing WGT with `tizen package` as above. |
| “Open AttuneTV from its original address” during pairing | The packaged Tizen 4 app uses `Origin: file://`. The hosted service must include the November 20 origin-compatibility fix. After it is deployed, choose **Start a new pairing**; the TV package does not need reinstalling for this server fix. |
| “Service setup needed” | Rebuild with the HTTPS service origin before signing. |
| Installed but blank/error screen | Report model, firmware, app version and a photo. Installation success alone does not prove UI or HTTPS compatibility. |

[Report an installation issue](https://github.com/thellis954/attunetv/issues/new) · [Full verification checklist](../samsung/README.md#acceptance-checklist)

## Use Samsung Home

Opening AttuneTV starts at its Home screen. Left/right selects a favorite; OK opens it. Down opens All apps. The top shortcuts are Search, Apps and Ask. Search finds installed apps; it does not search streaming catalogs. Use Menu (where supported) or the visible App options button to pin/unpin or reorder favorites. Back closes menus and the grid, then offers exit. Ask opens the existing companion; Back to Home returns without unpairing your phone.

Samsung's remote Home button still opens Smart Hub. Pin AttuneTV there through Home → Apps → Settings → AttuneTV → Add to Home, then move it near the start. There is no supported Home-button replacement or automatic startup change in this package.

The photo is bundled for offline use. App names/icons come from the TV; system workers are filtered and missing images use readable names. Favorites stay on this TV and never go to the AI service. Apps unavailable through Samsung's launch API must be opened from Smart Hub. This app does not install streaming services or bypass their subscriptions.
