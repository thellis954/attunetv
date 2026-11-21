# Screenshot provenance

[← AttuneTV](../../README.md)

Images are unretouched captures of the app. README sizing is presentation only. No app UI, artwork or model answers were painted into screenshots. Streaming app logos belong to their respective owners and do not imply endorsement or verified chat integrations.

| Image | Origin | What it establishes |
| --- | --- | --- |
| `home-fire-tv.png` | Real Insignia NS65-UQFL26 / Fire OS 8.1.6.4 capture, November 9, 2026. Initial installed 0.5.0-verify build, before the final glass/font refinements in source commit `6599f93`. | Launcher Home on a physical TV; no final-build or streaming-overlay claim. Replaces the older 0.4.1 README capture. |
| `apps-fire-tv.png` | Real Insignia capture from the 0.3.3 verification session. | All-apps grid on hardware. Earlier styling/artwork, not a current-build acceptance screenshot. |
| `menu-fire-tv.png` | Real Insignia capture from the same 0.3.3 session. | Remote app-organization menu on hardware. |
| `companion-tv.png` | Chromium test capture, November 20, 2026, generated from `samsung/test/browser/companion.spec.mjs` at app source `6599f93`. | Actual browser UI, shared manual context and fixture conversation. Not captured on a Samsung TV and not a live AI answer. |
| `companion-phone.png` | Same browser test, 390px phone viewport, full-page screenshot. Speech recognition and provider responses are test fixtures. | Phone interface, shared conversation and optional Dictate control. The conversation panel scrolls independently; its top message is partly outside that panel's visible area. |

## Refreshing the browser captures

From `samsung/`, after the contributor prerequisites and Chromium are installed:

```sh
npm run test:browser
```

Review `test-results/screens/tv-chat.png` and `phone-chat.png`, then copy those two files here as `companion-tv.png` and `companion-phone.png`. Keep the visible **Test response** / **fixture-model** labels and the README captions. Do not publish pairing links, QR credentials, API keys or private conversations.

For physical captures, record app version, source commit, TV model and OS. Capture only when the viewer is available; never interrupt playback for marketing screenshots. Keep older images labeled until replaced with verified captures of the new build.

- `home-samsung-browser.png`: Samsung Home 0.2.0 rendered at 1920×1080 in Chromium with a fixture app inventory. Demonstrates layout, not physical Samsung pixels or verified streaming-app launches. Background: Jordan Arnold/Unsplash, credited in `samsung/web/assets/CREDITS.md`.
