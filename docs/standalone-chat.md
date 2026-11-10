# TV and phone viewing companion

Version 0.5.0 replaces the unverified Fire TV account-sign-in preview with the
same guided API-key flow as Samsung. No always-on Mac or personal server is
required. The launcher works independently; optional chat requires the hosted
Vercel/Neon companion and an internet connection.

## Connect once

Open Ask, select Pair phone, scan the QR and approve the matching number using
the remote. On the phone, choose OpenAI, Claude, Grok or Kimi. Follow the billing,
API-key and model-selection guide. An API key is distinct from a consumer chat
subscription. AttuneTV is free; provider API usage and hosting can cost money.

Keys are entered only on the paired HTTPS phone page and stored encrypted on the
service. The TV receives a room credential, never a provider key. Android stores
that credential using authenticated encryption and a non-exportable Keystore key,
excluded from backups. Old account-preview tokens are not reused or transmitted.
Pairing links expire after five minutes. Replacing a phone erases the previous
phone's key and conversation. Disconnect & erase revokes the room's sessions and
deletes its saved key and conversation; it does not revoke the key at the provider.

## Context and conversation

A title, episode, progress and streaming-service name are shared between screens.
Optional authorized Android MediaSession metadata can prefill an editable title;
missing or generic metadata is not presented as a detected movie. The viewer
confirms context before it is sent. No video, audio, captions or screen content are
captured. Answers can be wrong and spoiler avoidance cannot be guaranteed.

Changing viewing context clears history. Revision checks protect edits, and a
question drafted against older context requires explicit review before sending.
Request IDs prevent a network retry charging twice. Stop cancels the shared
answer; closing the TV window lets phone chat continue. Responses are polled over
HTTPS; they appear as completed answers, not token-by-token streaming.

Phone dictation is feature-detected, user-initiated and uses the browser's speech
service. It stops when the page is hidden; transcribed words are reviewed before
Ask. Unsupported browsers can use keyboard dictation. The tested Insignia does
not expose Android's standard speech recognition activity, so no nonworking TV
voice button is shown.

## Playback boundaries

The Android panel uses TYPE_ACCESSIBILITY_OVERLAY. Back/Close, leaving the target
app and two minutes idle dismiss it. Home redirection only observes Amazon Home
unless the viewer enables the supported-app shortcut or a panel is open. Screen
content retrieval and spoken feedback stay disabled.

Hold Menu observes and passes through the player's key sequence. Its interaction
with playback controls, keyboards, Back, Home and app switching must be tested in
each streaming app. A player can pause on focus loss. Candidate packages are not
release support: normal builds retain an empty verified allowlist until actual
provider playback and inference checks pass. Developers can build with
`-PstreamingVerification=true`; this labels the version and setup screen as a test
build, and Gradle rejects combining it with a public distribution build.

Samsung's packaged Tizen app cannot overlay built-in streaming apps. It provides
full-screen conversation and the phone continues when the TV app is suspended.

## Verification

Automated coverage includes room isolation, billing consent, provider request
contracts, encrypted storage, idempotency, stale-context protection, pairing,
remote focus, browser dictation handoff and phone independence of TV lifetime.
Fixture answers are not evidence of live AI. Actual sign-in/key entry belongs to
the viewer, not an agent reading secrets. Hardware and provider checkpoints are
recorded separately in the release verification report.
