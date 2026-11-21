# Get AttuneTV on your screen

[← AttuneTV](../README.md)

**Start with the software your TV runs, not the logo on its bezel.** A Samsung TV with a Fire TV Stick uses the Fire TV guide when you watch through that stick. Its built-in Samsung apps use the Samsung guide instead.

| Your screen | What you get | Start here |
| --- | --- | --- |
| **Android-based Fire TV** — including tested Insignia NS65-UQFL26 | Personal Home screen; optional companion in the newer development build | **[Fire TV installation](install.md)** — prebuilt APK, Mac/Windows/Linux instructions |
| **Samsung Tizen** — install and launch verified on QN65Q65FN / Tizen 4.0 | Full-screen TV companion and paired phone chat while streaming | **[Samsung installation](samsung-install.md)** — developer preview, computer and Samsung signing required |
| **Android TV / Google TV** | Experimental APK installation; Home behavior and device compatibility need testing | **[Android TV testing guide](android-tv-install.md)** — not yet a verified device family |
| **Computer + phone** | Try pairing and the companion before installing anything on a TV | **[Open the hosted preview](https://attunetv-samsung.vercel.app/tv)**, then **[connect AI](ai-setup.md)** |
| **Roku, Apple TV hardware, LG webOS, non-Android Fire TV** | No native AttuneTV package currently available | Use a computer browser plus phone to try the companion, or a compatible external streaming device for the launcher |

**Apple TV the streaming app is different from Apple TV the hardware.** Opening a streaming app from AttuneTV does not mean AI can overlay its player. Samsung's companion cannot overlay built-in streaming apps or replace Smart Hub.

## Pick the experience

- **“I want a better Home screen.”** Start with the [Fire TV APK](install.md). The current public release is **0.4.2**; no AI account is needed.
- **“I want to ask about what I'm watching.”** Try the [hosted companion](https://attunetv-samsung.vercel.app/tv), choose Pair phone, and follow [AI setup](ai-setup.md). Enter the title, episode and progress yourself. This is not automatic scene recognition.
- **“I want it installed on my Samsung.”** Follow the [Samsung guide](samsung-install.md). Version **0.1.0** installed and launched on our test TV; visual, phone-pairing and real-provider acceptance are still being checked. It is not a Samsung Store download.
- **“I want the latest Fire TV chat build.”** The shared API-key companion is in **0.5.0 development source**, not the public 0.4.2 APK. Follow [the build guide](../CONTRIBUTING.md), or use the hosted preview without replacing your launcher.

## What you need—and what you don't

Use a computer on the TV's home network for native installation. You can turn it off afterward: the launcher runs on the TV, and chat uses the hosted service. A phone makes personalization and AI-key entry much easier.

AttuneTV is free to download. **AI providers bill API usage separately**, including when you already pay for ChatGPT or another consumer subscription. [AI setup](ai-setup.md) explains the account, key and spending-limit steps.

## Need help?

[Open a device/setup issue](https://github.com/thellis954/attunetv/issues/new) with your model, OS version, AttuneTV version, the step that failed and the exact error. A photo of the screen helps. Remove pairing codes, API keys and private account details first. No factory reset or removal of manufacturer apps is part of these guides.
