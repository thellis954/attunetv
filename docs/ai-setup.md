# Turn your phone into your viewing companion

[← Choose your screen](setup.md)

**Watch on TV. Ask on your phone.** Pair once to give your phone a shared conversation about the show, episode and progress you enter. Keep asking while your streaming app plays, then return to the same conversation on the TV. The phone companion runs in your browser—no separate phone app to install.

You can try this before installing a TV app: open **[AttuneTV's hosted preview](https://attunetv-samsung.vercel.app/tv)** on a computer, keep that page open and use your phone for setup. The same pairing flow is available in the Samsung companion and the public Fire TV **0.5.2 APK**. [Download the latest setup bundle](https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV-Setup.zip) if an older installation does not have it.

## 1. Pair your phone

1. On the TV companion, select **Pair phone**. On Fire TV, open **Ask → Pair phone**.
2. Scan the QR code with your phone and tap **Pair this phone**.
3. Compare the number on both screens and approve it on the TV. The link expires after five minutes; generate a new one if needed.

This is separate from the Fire TV launcher's **Settings → Set up from your phone**, which changes favorites, weather and appearance.

## 2. Connect a provider

On the phone, open **AI connection** and choose **OpenAI, Claude, Grok or Kimi**. The app provides the provider's account, billing and API-key links so you do not have to find them yourself.

1. Open the provider link, create/sign into its developer account, review prices, and add credit if required.
2. Set the provider's available budget/usage controls. A ChatGPT, Claude or other consumer subscription does not automatically pay for API calls.
3. Create an API key named **AttuneTV** with access to the model list and text generation. Return to phone setup and paste it into the password field.
4. Tap **Check key & load models**, then choose a text model. This check lists models; it does not generate a paid answer or prove that every listed model is compatible.
5. Set your daily question limit, review billing consent, and tap **Connect provider**.

**Keep keys private.** Enter them only in the phone setup form, not on the TV, in GitHub issues, chat messages or screenshots. The hosted service encrypts stored keys and conversations, but this is not end-to-end encryption from the service operator. Questions and supplied viewing context go to your selected AI provider.

## 3. Ask your first question

Enter the title, episode and how far you have watched. Try: “We're halfway through episode two. Explain the relationship between these characters without discussing later events.”

On Samsung, return to Netflix, Prime Video or another streaming app and continue the conversation **on your phone**. Reopening AttuneTV should load the same conversation. The TV app is full-screen; it cannot float over Samsung's built-in players.

The companion does not watch the video, listen to audio or read subtitles. Answers and spoiler avoidance are not guaranteed. Live provider behavior still needs device acceptance testing; automated tests use clearly labeled fixture responses.

## Troubleshooting and control

| What happened? | Next step |
| --- | --- |
| Pairing link expired | Generate a new QR code and approve the new matching number. |
| Key check fails | Confirm provider, key permissions and account status; read the displayed error. Never paste the key into a bug report. |
| Model appears but answering fails | Check model compatibility and provider billing. The app does not silently switch models or retry paid requests. |
| Daily limit reached | Review the limit in AI connection. A question count is not a dollar budget; also use provider controls. |
| You want to stop using AI | Remove the key in AI connection, or use **Disconnect & erase** to delete the room and active sessions. Revoke the key in the provider console if it should stop working everywhere. |

No always-on Mac is required. Hosted service availability and provider billing still apply.
