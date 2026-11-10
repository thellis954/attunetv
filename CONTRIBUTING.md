# Build something better for the living room

[← AttuneTV](README.md) · [Install a prebuilt app](docs/install.md) · [Native chat architecture](docs/standalone-chat.md)

AttuneTV is a native Kotlin Android app. Contributions can be as small as a useful device report or as ambitious as a carefully tested streaming integration. A TV remote is the primary input device; make every change work from the couch.

## Build locally

You need **Git**, **JDK 17**, and an **Android SDK** with **platform 35**, **build-tools 35.0.0** and **platform-tools**. The checked-in Gradle wrapper supplies Gradle. You do not need an OpenAI API key to build or test.

### Android Studio

1. Clone the repo below and open its root folder in Android Studio.
2. In SDK Manager, install **Android SDK Platform 35**, **Android SDK Build-Tools 35.0.0** and **Android SDK Platform-Tools**.
3. Set **Gradle JDK** to **17**. Let Studio sync the project and create `local.properties` with your SDK location.
4. Run the wrapper commands below in the project terminal.

### Command line

```bash
git clone --branch claude/blissful-hypatia-4dfwls https://github.com/thellis954/attunetv.git
cd attunetv
```

Set `JAVA_HOME` to your JDK 17 installation. Install the required SDK packages with the `sdkmanager` supplied by [Android Command-Line Tools](https://developer.android.com/studio#command-tools):

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --licenses
```

Create an untracked `local.properties` in the repo root with the **actual absolute path** to your SDK. Examples (use only the line for your system and replace the username):

```properties
# macOS example
sdk.dir=/Users/YOUR_NAME/Library/Android/sdk
# Linux example
# sdk.dir=/home/YOUR_NAME/Android/Sdk
# Windows example; forward slashes avoid properties-file escaping
# sdk.dir=C:/Users/YOUR_NAME/AppData/Local/Android/Sdk
```

Then build and test:

```bash
./gradlew assembleRelease testDebugUnitTest
```

On Windows PowerShell, use `./gradlew.bat assembleRelease testDebugUnitTest`. For a completely fresh build, add `clean` before `assembleRelease`.

**APK:** `app/build/outputs/apk/release/app-release.apk`

**Test report:** `app/build/reports/tests/testDebugUnitTest/index.html`

Local builds use your development key and carry a `-dev` version suffix. They are for development, not a replacement for the officially signed download. CI builds, tests and lints pushes and pull requests; its APK artifact is named `AttuneTV-development-apk`. Run checks locally before opening a PR. For normal installation and updates, use the [official APK](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV.apk).

## Find your way around

All Kotlin source is under [`app/src/main/java/com/attunetv/launcher/`](app/src/main/java/com/attunetv/launcher/).

| Area | Start here |
| --- | --- |
| Home, dock, grid and focus | `HomeActivity.kt`, `AppAdapter.kt`, `AppRepository.kt` |
| Search | `SearchActivity.kt`, `AppSearch.kt` |
| Glass, photos and color | `Wallpaper.kt`, `GlassDrawable.kt`, `Backgrounds.kt`, `Colors.kt` |
| Weather and calendar | `Widgets.kt`, `Weather.kt`, `Ics.kt` |
| Preferences and phone setup | `SettingsActivity.kt`, `Prefs.kt`, `PhoneSetupActivity.kt`, `SetupServer.kt` |
| Home-button behavior | `HomeButtonService.kt`, `HomeRedirectPolicy.kt` |
| Native chat and account security | `NativeChat.kt`, `ChatVault.kt`, `ChatSecurity.kt`, `NativeChatPanel.kt` |
| Playback context and provider gating | `PlaybackContext.kt`, `StreamingSupport.kt` |
| Layout and unit tests | [`app/src/test/`](app/src/test/) |

## Validate the change that you made

- **Logic:** `./gradlew testDebugUnitTest` covers calendar parsing, app search, chat security and layout rendering. Read failures rather than updating expectations to hide them.
- **UI:** use `./gradlew recordPaparazziDebug` to render layouts, inspect the generated images and review the snapshot diff. Test focus, text, logos and scrolling on a real TV as well.
- **Android checks:** run `./gradlew lintRelease` when changing app behavior or configuration.
- **Device behavior:** record model, Fire OS, app version, commit and relevant screenshots. A successful build does not establish playback compatibility.

Paparazzi downloads its rendering dependencies on first use. If a download fails, record that as an environment failure and rerun once available; do not claim the affected tests passed.

### Streaming integrations need real playback tests

`StreamingSupport.verifiedPackages` is the release allowlist. The candidate map is a test queue, not a support claim. Keep unknown and unverified apps hidden.

For each provider, verify a real answer, remote keyboard/focus, continued playback, cancellation, Back/Home/app-switch dismissal and idle timeout. Document metadata availability separately. Never bypass protected-video capture or infer scene awareness from a title and timestamp. See the [acceptance checklist](docs/standalone-chat.md#acceptance-test).

### Preserve the TV

Keep other accessibility entries intact. Do not disable or uninstall Amazon packages, change display density to compensate for layout bugs, or hijack Amazon search/settings/profile screens. Do not interrupt someone else's playback to run tests. Never commit account tokens, calendar URLs, signing keys or private debug dumps.

## Before opening a pull request

Keep the change focused. Explain the problem, the resulting behavior and how you checked it. Include before/after images for visual changes and device evidence for Home or streaming behavior. For a substantial redesign or integration, open an [issue](https://github.com/thellis954/attunetv/issues/new) to describe the approach first.

Good places to contribute:

- **Compatibility reports:** test another Fire TV model or firmware version.
- **Remote UX:** catch lost focus, hard-to-reach controls and unclear selection states.
- **Artwork:** improve missing-banner fallbacks and long app names.
- **Accessibility:** improve legibility, contrast and navigation.
- **Chat:** validate native account connection and provider behavior without broadening access to screen content.

## Distribution notes

Official tagged releases are built with `-Pdistribution=true`, which produces an unsigned, non-debuggable APK. A separate release job signs it using the protected signing identity after tests and lint pass. No release secrets are supplied to Gradle or pull-request builds. Local/ordinary CI builds use a development key and `-dev` suffix.

See [Publishing a release](docs/releasing.md) for the tag workflow, certificate pin, secret configuration, backup requirements and update-compatibility checks. Never commit a private keystore or password.

This repository currently has **no declared license**. Do not assume MIT or another reuse license; ask the maintainer before redistribution or incorporating the code into another product.
