# AttuneTV viewing companion (preview)

A local companion for AttuneTV 0.4.0. Ask questions on your phone and read answers in a passive panel over a supported Fire TV app. The streaming app keeps focus and receives all remote keys. This is independent software, not an official ChatGPT TV application.

## Start

Requires Node 22+, Android platform-tools (`adb`), and the TV already paired with this computer. Install the matching AttuneTV APK and enable its existing Home-button service. No extra TV settings or permissions are required.

```sh
cd companion
npm ci
adb connect 192.168.86.42:5555
ATTUNETV_DEVICE=192.168.86.42:5555 npm start
```

Open **http://127.0.0.1:8766** on this Mac. Choose **Continue with ChatGPT**, review OpenAI's consent, and authorize the plan usage you want. Account selection, reconnect and sign-out stay on the Mac. Available models come from your account's current catalog.

On your phone, open the local Wi-Fi URL shown on that page and enter its six-digit pairing code (single-use, ten-minute expiry). Use your trusted home network: the phone connection is HTTP. Questions, titles and answers travel locally in plaintext; OpenAI credentials never go to the phone or TV. Use **Disconnect all phones** to revoke paired phone access. Pairing is lost on companion restart.

1. Open a supported streaming app on your TV.
2. Check the title on your phone. If unavailable or incorrect, choose **Set or correct the movie / show**. Add the episode or describe the scene as needed.
3. Type a question or dictate with your phone keyboard, then tap **Ask**. The answer appears on both screens.
4. **Hide from TV** immediately removes the panel and stops any current answer. **Stop answer** cancels inference. **New conversation** clears local context and conversation.

The panel closes on an app switch, after 90 seconds, and within nine seconds of losing the companion connection. Home also leaves the streaming app and removes it. Back stays with the streaming app; it does not control this passive panel. Long answers are truncated on TV and remain readable in full on the phone.

**The Mac must stay awake, connected to the same network, and running this process.** This preview uses the existing authorized ADB connection as the TV transport. It does not run a TV HTTP server or expose ADB beyond the user's existing network setup. A standalone TV implementation is future work.

## What it knows

Only the foreground app and the metadata/position that app exposes through Android MediaSession. No video frames, audio, subtitles or screen text are captured. Prime Video on the tested Insignia exposes the generic title `PrimeVideo`; that is deliberately treated as unknown. A timestamp does not provide scene understanding. The assistant asks for a scene description instead of claiming to see it.

Manually entered context is labelled **supplied by you**, and is cleared when switching apps, starting a new conversation, or when an exposed title changes. If an app exposes no titles, the companion cannot detect a title change inside that app: correct the title whenever you change movies/shows. Conversation history exists only in memory, capped to six turns. It is sent to OpenAI only when you ask a question. `store:false` is used on Responses requests; this is not a claim of zero provider retention. Spoiler avoidance is a prompt preference, not guaranteed.

## Support gates

`support.json` is the release allowlist. Candidate packages alone do not enable chat. Unsupported apps hide all chat controls; an open TV panel is removed. The report distinguishes overlay compatibility, title detection, and actual movie playback testing.

Developers can temporarily test exactly one candidate using `ATTUNETV_VERIFY_PACKAGE=com.amazon.firebat`. This is explicitly a verification mode, not a support claim. Do not ship it as a substitute for testing. Never enable providers based solely on an app-store availability list.

## Account storage and security

Uses the documented [Sign in with ChatGPT local/open-source flow](https://developers.openai.com/siwc/token-sharing-open-source): dynamic client registration, stable per-host UUID, PKCE/state/nonce, verified RS256 ID tokens, issued-client audience and subject validation, granted-plan scope checks, serialized token refresh, and sign-out/revocation. Account credentials are stored under `~/.config/attunetv-companion` with owner-only directory/file permissions (0700/0600), separately by issued client ID. They are not read from Codex or another application's credentials.

The account callback and account-management routes are loopback-only. LAN phone routes use random session tokens after rate-limited pairing, reject foreign origins/hosts, and cannot initiate OAuth or read account credentials. The TV receiver requires the system/shell `android.permission.DUMP` permission. Its only command is bounded text rendering for known streaming package identities.

Defaults: phone port 8765, local account/callback port 8766. Override `ATTUNETV_PORT`, `ATTUNETV_AUTH_PORT`, `ATTUNETV_DEVICE`, `ADB` (path), or `ATTUNETV_DATA_DIR` as needed. No API key is required; account eligibility and ChatGPT plan limits apply. Manage access at [ChatGPT Settings → Usage](https://chatgpt.com/settings/usage).

## Verify

`npm test` exercises context provenance, stale metadata, provider allowlisting, stream completion/failure, OAuth state/client validation, HTTP pairing, CSRF/Host checks, account-route isolation, and phone revocation. Android policy tests cover exact package identities and app-switch dismissal. A real sign-in, model answer, and device playback tests are still required for end-to-end verification; unit tests alone do not establish those.
