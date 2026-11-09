# Standalone TV chat — 0.4.1 native preview

**Product requirement:** install AttuneTV on the television, connect an eligible account, and use it without an always-on Mac, PC, phone, or personal server. The 0.4.0 Mac architecture did not meet that requirement and has been removed. Its historical source remains in Git commit c446bb7.

## Architecture

```text
TV remote → native AttuneTV chat window → HTTPS → OpenAI Responses API
                         │
                         ├─ encrypted account credentials on this TV
                         ├─ optional Android MediaSession metadata
                         └─ viewer-entered title / episode / scene fallback
```

There is no inference server to host and no developer API key in the APK. Each user's authorized ChatGPT account supplies eligible plan usage. The model runs at OpenAI, not on the television. Initial sideloading and enabling the launcher's Home service still follow the existing setup procedure; these are separate from normal chat operation.

## Account connection

The published [local/open-source Sign in with ChatGPT flow](https://developers.openai.com/siwc/token-sharing-open-source/sign-in) requires an external browser and an HTTP callback on `127.0.0.1`. It does not mandate a desktop OS. The implementation places both endpoints on the TV:

1. Open **Ask → Continue with ChatGPT** in AttuneTV.
2. AttuneTV starts a temporary loopback listener bound only to the TV's `127.0.0.1` interface, then opens the external browser (Silk on the inspected TV).
3. The viewer signs in and reviews OpenAI's account/plan consent in that browser. This preview does not promise phone/QR authorization: phone loopback would point to the phone, not the TV. It does not reuse Codex's device flow or an unrelated application's client ID.
4. OpenAI redirects the TV browser to the TV's callback. AttuneTV validates state, nonce, PKCE, the issued client registration, RS256 signature, issuer, audience, expiration, account identity and plan scopes before accepting tokens.
5. Tokens are encrypted with AES-GCM using an Android Keystore key, in app-private `noBackupFilesDir` storage. Refreshes and vault writes are serialized. Credentials stay on this TV and are not copied from the Mac prototype.
6. Model discovery and streaming Responses requests go directly from the TV to OpenAI. Requests use `store:false`, `stream:true`, bounded in-memory history, and require `response.completed`. Sign-out clears local credentials and attempts server revocation.

Official scope: [OpenAI's overview](https://developers.openai.com/siwc/token-sharing-open-source) documents this for open-source and locally hosted apps. Paid or remotely hosted distribution has a separate interest/approval route. The native implementation is not proof that every account or Fire TV browser is eligible; end-to-end account consent still needs device validation.

## Playback and input

The native panel is a `TYPE_ACCESSIBILITY_OVERLAY` window, not a new fullscreen Activity. Remote navigation, the TV keyboard, Ask, Stop and Back operate locally. Back or Close dismisses it, as does leaving the streaming app or two minutes without remote activity. Model output does not reset the idle timeout. Pending generation is cancelled when the panel closes, and callbacks cannot resurrect an old panel.

The **hold Menu** shortcut is opt-in and only registered when a verified streaming app exists in `StreamingSupport.verifiedPackages`. A short Menu press passes through unchanged; the complete long-press sequence also passes through before the panel opens. Provider reactions to long Menu and focus/keyboard changes must be tested. Some players may pause on losing focus even though AttuneTV does not pause them. This is an explicit acceptance test, not a promise of uninterrupted playback based on window type alone.

Unknown or unverified packages do not get the shortcut or panel. The setup screen's explicit panel preview works only over AttuneTV itself, so the UI and account connection can be evaluated before enabling a provider.

Automatic metadata is optional. Android [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName)) requires an enabled notification listener or privileged media-control permission. The app offers the system permission screen only if it exists. No permission is silently granted. Metadata is matched to the foreground provider and generic provider names are rejected as movie titles. Viewer edits change the provenance label to manual. Position is sent only when fresh metadata still matches the displayed title.

The assistant never receives video frames, protected screenshots, audio, subtitles, or arbitrary screen text. A title and timestamp do not establish scene knowledge. It asks for a description when needed. Answers can be wrong and spoiler avoidance is not guaranteed.

## Current evidence and release gates

Read-only checks on Tom's Fire OS 8.1.6.4 television found:

- HTTPS browser intents resolve to Silk (`com.amazon.cloud9/...IntentDispatcher`). This verifies a browser is installed, not that the full OpenAI login succeeds in Silk.
- `android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` has no resolving Activity. Manual context must therefore work without a permission setup screen.
- Prime Video's active MediaSession exposes `PrimeVideo` rather than a usable title in the observed playback. The native module rejects that generic value.

**Not yet verified on the TV:** loopback callback through Silk, actual account grant/model response, encrypted account persistence across reboot, remote keyboard/Back behavior, provider playback continuity, app-switch dismissal, and provider-specific metadata. The television remains on 0.3.4 until installation/testing is authorized while playback is in use.

`StreamingSupport.verifiedPackages` is deliberately empty. Candidates are Prime Video, Netflix, Apple TV, Disney+, Hulu, HBO Max, Peacock and Paramount+. Enable a provider only after testing native focus/keyboard, answer rendering, cancellation, Back/Home/app-switch dismissal, idle timeout and actual playback. The discarded Mac prototype's tests do not satisfy these native release gates.

## Acceptance test

Install the native APK, complete account sign-in on the TV, then disconnect the development computer from the network. A real question and answer must work on the TV with remote input. Repeat while playing content in each target provider, and document metadata availability separately from overlay compatibility. Share TV screenshots and an honest per-provider report; protected-video capture may be unavailable.
