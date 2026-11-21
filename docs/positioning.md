# AttuneTV positioning

[← README](../README.md) · Research checked November 20, 2026

## The promise

**A calmer home. A conversation about what’s on.**

AttuneTV combines a personal Fire TV home screen with an optional viewing companion whose conversation carries between TV and phone. Provider choice, shared context and a phone path during playback are the product direction. The launcher is usable independently while companion integrations mature.

The first audience is people who want their existing TV to feel more personal and enjoy talking about what they watch. The developer audience is people interested in remote-first interfaces, older TV hardware and accountable AI integrations.

## The landscape

These are different categories of alternatives, not a claim that every product competes feature-for-feature. Descriptions below come from each project's own documentation; the positioning implications are our interpretation. This is not a hands-on performance comparison.

| Alternative | Its documented focus | Implication for AttuneTV |
| --- | --- | --- |
| [Projectivy Launcher](https://github.com/spocky/miproja1#projectivy-launcher) | An ad-free, customizable Android TV launcher with personalization and device features. | Beautiful, ad-free Home is an expectation in this category. Lead with the combination of Home and a viewing companion rather than claiming visual customization is unique. |
| [Clean Your TV](https://github.com/Gathin23/cleanyourtv#the-launcher) | Debloating tools and a small native launcher, with themes, optional app library and module cards. Its launcher deliberately has no network permission. | Make AttuneTV's cinematic design, phone setup and optional connected companion clear. Preserve Amazon packages in our install path. Do not position it as smaller, faster or more private without comparative evidence. |
| [Gemini for Google TV](https://support.google.com/googletv/answer/16522213?hl=en) | Conversational help, discovery and connected services on eligible Google TV devices; availability depends on device, region and language. | AI on TV is already a category. Our opportunity is provider choice and the shared TV/phone workflow, starting with Fire TV and a Samsung companion. Hardware support must be earned, not implied. |
| [Prime Video X-Ray](https://www.aboutamazon.com/news/entertainment/what-is-x-ray-on-prime-video) and [X-Ray Recaps](https://www.aboutamazon.com/news/entertainment/amazon-prime-video-x-ray-recaps) | Information tied to Prime Video viewing, including cast/background material and AI-generated recaps. | Service-owned features have access to content and playback context that we do not. Offer an ongoing viewer-directed conversation; don't claim equivalent scene awareness or guaranteed spoiler safety. |

## What we can own

- **A coherent living-room experience.** Personal Home gets viewers to their apps; the companion gives them a place to explore questions afterward.
- **Choice of AI provider.** Guided API-key setup offers OpenAI, Claude, Grok and Kimi. The promise is a clear connection flow, not free API access or universal model compatibility.
- **Continuity between screens.** QR pairing, TV approval, shared context and history. Phone chat continues after the TV companion closes. This matters on Samsung, where a normal third-party app cannot overlay another streaming app.
- **Viewer-supplied context.** Explicit title, episode and progress make the basis of the conversation visible. We do not imply that the app watches or hears protected playback.
- **Reversible launcher setup.** Keep Amazon packages installed and preserve other accessibility services. This is a product principle, not a claim that alternatives cannot be reversed.

These are differentiators of the combination. We have not established that every individual feature is exclusive to AttuneTV.

## Match the invitation to the build

| Visitor | Primary invitation | What to explain before they start |
| --- | --- | --- |
| Wants a better Fire TV Home | Install the public APK using the beginner guide | Public release is 0.4.2; companion API-key changes are in 0.5.0 development source. ADB and a computer are needed for setup. |
| Wants to explore the companion | Open the hosted preview and pair a phone | API key and separate provider billing are required for real answers; live inference validation remains open. |
| Owns a Samsung TV | Follow the Tizen developer-preview guide | Signing and Developer Mode are required. Device installation is pending; it is full-screen chat plus a phone companion, not a replacement Smart Hub or cross-app overlay. |
| Wants to contribute | Build, run fixture tests, submit a device report or focused change | No API credit purchase required for automated tests. Real playback evidence is needed before enabling an overlay integration. |

## Claims to hold until verified

Do not call this the official ChatGPT TV app, a universal streaming assistant, scene-aware, spoiler-proof, compatible with every TV, or an unlimited free AI service. Streaming candidates are not a support list. The repository has no declared license, so public source should not be advertised as licensed open source.

Screenshots should identify their version and origin. Browser fixtures demonstrate UI and synchronization, not real model quality or Samsung hardware behavior. See [image provenance](images/README.md).

## What would strengthen the next launch

1. Verify live answers and error recovery with user-controlled API credentials.
2. Finish Samsung signing/install and capture real hardware evidence.
3. Verify Fire TV playback, remote focus, Back/Home and idle dismissal per streaming app.
4. Publish a signed release containing the shared companion flow, then align the download and README version claims.
5. Choose an explicit software license before promoting reuse or redistribution.

These are release gates and proposed priorities, not completed features or dated commitments.
