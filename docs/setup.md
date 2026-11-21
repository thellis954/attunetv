# Get AttuneTV on your screen

[← AttuneTV](../README.md)

**Start with the software your TV runs, not the logo on its bezel.** A Samsung TV with a Fire TV Stick uses the Fire TV guide when you watch through that stick. Its built-in Samsung apps use the Samsung guide instead.

| Your screen | What you get | Start here |
| --- | --- | --- |
| **Android-based Fire TV** — including tested Insignia NS65-UQFL26 | Personal Home screen and shared phone/TV companion in the public APK | **[Fire TV installation](install.md)** — prebuilt APK, Mac/Windows/Linux instructions |
| **Samsung Tizen** — install and launch verified on QN65Q65FN / Tizen 4.0 | Full-screen TV companion and paired phone chat while streaming | **[Samsung installation](samsung-install.md)** — developer preview, computer and Samsung signing required |
| **Android TV / Google TV** | Experimental APK installation; Home behavior and device compatibility need testing | **[Android TV testing guide](android-tv-install.md)** — not yet a verified device family |
| **Computer + phone** | Try pairing and the companion before installing anything on a TV | **[Open the hosted preview](https://attunetv-samsung.vercel.app/tv)**, then **[connect AI](ai-setup.md)** |
| **Roku, Apple TV hardware, LG webOS, non-Android Fire TV** | No native AttuneTV package currently available | Use a computer browser plus phone to try the companion, or a compatible external streaming device for the launcher |

**Apple TV the streaming app is different from Apple TV the hardware.** Opening a streaming app from AttuneTV does not mean AI can overlay its player. Samsung's companion cannot overlay built-in streaming apps or replace Smart Hub.

## Current downloads

[Complete setup ZIP](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV-Setup.zip) · [Android APK](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV.apk) · [Samsung unsigned WGT](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV-Samsung-UNSIGNED.wgt) · [Release notes and checksums](https://github.com/thellis954/attunetv/releases/latest)

The Android installer verifies the bundled checksum and installed version, preserves app data and stops on authorization/signature errors. It changes no TV settings. The ZIP is a versioned download, not an automatic updater: download it again for each update.

## Pick the experience

- **“I want a better Home screen.”** Start with the [Fire TV APK](install.md). The current public release is **0.5.1**; no AI account is needed.
- **“I want to ask about what I'm watching.”** Try the [hosted companion](https://attunetv-samsung.vercel.app/tv), choose Pair phone, and follow [AI setup](ai-setup.md). Enter the title, episode and progress yourself. This is not automatic scene recognition.
- **“I want it installed on my Samsung.”** Follow the [Samsung guide](samsung-install.md). Version **0.1.0** installed and launched on our test TV; the UI is visible and on-TV pairing-link/QR generation is verified; phone approval and real-provider acceptance are still being checked. It is not a Samsung Store download.
- **“I want to install or update the phone companion.”** [Download the setup ZIP](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV-Setup.zip) and open START-HERE.txt. It includes the signed Android **0.5.1 APK**, configured Samsung **0.1.1 WGT** (unsigned), guided Android installers and all setup guides. No GitHub sign-in or source build is required. Samsung still requires your own certificate.

## What you need—and what you don't

Use a computer on the TV's home network for native installation. You can turn it off afterward: the launcher runs on the TV, and chat uses the hosted service. A phone makes personalization and AI-key entry much easier.

AttuneTV is free to download. **AI providers bill API usage separately**, including when you already pay for ChatGPT or another consumer subscription. [AI setup](ai-setup.md) explains the account, key and spending-limit steps.

## Need help?

[Open a device/setup issue](https://github.com/thellis954/attunetv/issues/new) with your model, OS version, AttuneTV version, the step that failed and the exact error. A photo of the screen helps. Remove pairing codes, API keys and private account details first. No factory reset or removal of manufacturer apps is part of these guides.
