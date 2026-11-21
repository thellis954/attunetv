# Android TV / Google TV: experimental installation

[← Choose your screen](setup.md)

**This is a testing path, not a verified compatibility claim.** AttuneTV's APK targets Android 7.1+ and declares Android TV and Home activities. Our physical launcher checks are on Fire TV; other Android TV/Google TV devices still need remote, layout, search and Home-button testing.

For an easier first look at chat, use the [hosted companion](https://attunetv-samsung.vercel.app/tv) on a computer and [pair a phone](ai-setup.md).

## Install the APK

1. Download the [public APK](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV.apk) and [Google's Platform-Tools](https://developer.android.com/tools/releases/platform-tools). The public 0.5.1 APK includes shared API-key chat. The [setup ZIP](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV-Setup.zip) includes the same APK and guided installers; complete any wireless-debugging pairing first.
2. Extract Platform-Tools and put `AttuneTV.apk` in that folder. Open Terminal/PowerShell there; the [Fire TV guide](install.md#3-connect-and-install) explains how on each computer OS.
3. On the TV, find **Settings → System / Device Preferences → About**, then select the OS build entry repeatedly to enable Developer Options. Labels vary by manufacturer.
4. Enable the debugging method your TV provides. If it offers **Wireless debugging → Pair using pairing code**, use the address and pairing port displayed there. The later connection port can be different. If it exposes network ADB on port 5555, use that address instead. Do not assume every device supports the same method. Follow [Google's current connection instructions](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi).

Mac/Linux example with wireless pairing (replace both displayed addresses):

```sh
./adb pair 192.168.1.50:PAIRING_PORT
# Enter the pairing code shown on your TV when prompted.
./adb connect 192.168.1.50:CONNECTION_PORT
./adb devices
```

For network ADB without pairing, use `./adb connect 192.168.1.50:5555` and approve the TV prompt. On Windows replace `./adb` with `.\adb.exe`.

Once the target is listed as **device**, install and launch (replace `TV_ADDRESS` with that exact device address, including port):

```sh
./adb -s TV_ADDRESS install -r AttuneTV.apk
./adb -s TV_ADDRESS shell am start -n com.attunetv.launcher/.HomeActivity
```

## Check it before changing Home

Open apps, navigate the grid, check focus and text size, and test Back. Search may depend on Amazon-specific services. AttuneTV's Fire TV accessibility redirect is designed for Amazon Home, so **do not apply the Fire TV accessibility commands to Google TV**.

If your device offers a normal **Default apps → Home app** setting, you may choose AttuneTV there after testing. Menu availability varies. Keep the original launcher installed; do not disable system packages to force a replacement. If Home cannot be selected, use AttuneTV from the apps list and report your device.

## Update or remove

Use `install -r` with a newer APK signed by the same publisher. A signature mismatch requires a deliberate choice about losing AttuneTV's data; do not automatically uninstall. To remove it, first restore the previous default Home app if you changed it, then uninstall AttuneTV through the TV's Apps settings.

[Send a device report](https://github.com/thellis954/attunetv/issues/new) with model, OS, version, remote behavior and screenshots. We use physical evidence before adding a device to the verified list.
