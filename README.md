# AttuneTV

A clean, ad-free home screen for Fire TV. It works like other community launcher
projects: your apps sit in a simple grid, with no sponsored rows, autoplaying
trailers, or "recommended for you."

Built for and tested against **Fire OS 8.1.6.4** on an Insignia NS65-UQFL26 (Fire TV
Edition, Android 11 base). It should work on any Fire OS 6–8 device.

## What you get

- **Favorites row** with big tiles. On first run it fills in from your installed streaming apps.
- **All apps grid**. It uses each app's TV banner when the app has one, and icon + name otherwise.
- **☰ Menu (or long-press OK) on any tile** to Pin, Unpin, Move, Hide, or open App info.
- **Move mode**: use the arrows to reposition a tile, then press OK.
- Clock and date, five calm dark backgrounds, and a "show hidden apps" toggle.
- **Home button redirect**: pressing Home takes you to AttuneTV instead of Amazon's screen.
- Settings shortcuts: Fire TV Settings, Accessibility, and "Open Amazon home" (a 20-second pass so you can reach Amazon-only screens).

## Why there's an accessibility service

Fire OS doesn't let you choose a default home app, and on Fire OS 8, disabling Amazon's
launcher over ADB breaks Settings and profiles. Projects like Home on Fire and
LauncherHijack work around this instead. AttuneTV does the same:

`HomeButtonService` is an accessibility service that listens **only** for window changes
from `com.amazon.tv.launcher`. When Amazon's home screen comes up, the service brings
AttuneTV back on top. It can't read screen content (`canRetrieveWindowContent=false`),
and it ignores every other app.

You'll see Amazon's home screen for a split second before AttuneTV appears. That's
expected with this approach.

## Install

### 1. Get the APK

- **CI:** each push builds `AttuneTV-apk` under **Actions → Build APK → Artifacts**.
- **Locally:** `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

### 2. Enable ADB on the TV

**Settings → My Fire TV → About**. Click the device name 7 times to unlock Developer Options.
Then go to **Settings → My Fire TV → Developer Options** and turn on **ADB debugging** and
**Apps from Unknown Sources**. Find the TV's IP under **About → Network**.

### 3. Install and turn on the Home button service

```bash
adb connect <TV_IP>:5555          # accept the prompt on the TV
adb install -r app-release.apk

# Turn on the Home-button service (Fire OS 8 hides most accessibility toggles in the UI)
adb shell settings put secure enabled_accessibility_services \
  com.attunetv.launcher/com.attunetv.launcher.HomeButtonService
adb shell settings put secure accessibility_enabled 1
```

> If you already use another accessibility service, append to the existing list
> instead of replacing it:
> `adb shell settings get secure enabled_accessibility_services`, then set the value to
> `existing:com.attunetv.launcher/com.attunetv.launcher.HomeButtonService`.

Press Home and AttuneTV should come up. Without ADB, you can still open AttuneTV from
**Your Apps**. It just won't take over the Home button.

### Undo

```bash
adb shell settings put secure enabled_accessibility_services ""
adb uninstall com.attunetv.launcher
```

Or flip **Settings → Redirect Home button** off inside AttuneTV.

## Notes and known limits

- **Amazon updates can turn the accessibility service off.** If Home starts going to
  Amazon again, run the `settings put` command again. AttuneTV shows a "not set up" banner when the service is off.
- **The profile picker passes through.** Amazon screens whose class names contain
  `Dialog`, `Toast`, or `Profile` are not redirected (see `PASSTHROUGH_CLASS_HINTS` in
  `HomeButtonService.kt`). Settings shows the last Amazon screen class it saw, which makes it easy to add more.
- **Live TV / antenna input** on Insignia TVs lives in Amazon's UI. Use
  "Open Amazon home", or pin your input app if one shows up in the grid.
- **The release build is signed with the debug key** so the CI artifact installs on your
  own TVs. Set up a real keystore before you share it more widely.

## Project layout

```
app/src/main/java/com/attunetv/launcher/
  HomeActivity.kt       home screen: favorites row, app grid, menus, move mode
  SettingsActivity.kt   toggles, theme, shortcuts, reset
  HomeButtonService.kt  Home-button redirect (accessibility service)
  AppRepository.kt      app discovery (leanback first, then phone launchers)
  AppAdapter.kt         tile rendering and focus animation
  Prefs.kt / Themes.kt  persistence and backgrounds
```

## Ideas for next

Weather and calendar widget, input switcher (HDMI tiles), "continue watching" via
deep links, screensaver photos, per-profile layouts, ADB-free setup via a companion
web page on your phone.
