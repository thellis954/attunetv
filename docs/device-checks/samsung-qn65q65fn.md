# Samsung QN65Q65FN installation checkpoint

Date: November 20, 2026 (America/New_York). Companion version: **0.1.0**. Platform: **Tizen 4.0**, firmware previously reported as T-KTM2AKUC-1355.1.

## Verified on the physical TV

- SDB lists the target as `device`; its capability output reports `platform_version:4.0` and `profile_name:tv`.
- A Samsung TV Public signing profile was created with the connected device's DUID, with private files outside Git.
- Initial installs failed with `118012`. Transferring the generated `device-profile.xml` into the device-advertised SDK tools directory resolved the rejection.
- Standard Tizen CLI signing (RSA-SHA512) and installation then succeeded. A temporary SHA-256 experiment did not resolve the rejection; no SDK modifications or compatibility patches are required or committed.
- `tizen run` reported a successful launch; the app list contains `AttuneTV01.Companion`.
- Samsung's app-status endpoint returned `name: AttuneTV`, `version: 0.1.0`, `running: true`, `visible: true`.
- The configured hosted service returned `ok: true`, `version: 0.1.0` from `/health`.

The installed, device-bound signed package SHA-256 is `6f0f534fd3b5096816d3dc6b2f03ad457a701119447be7c114bd212ce2bfcdab`. It is a local installation artifact, not a universal public Samsung download. No certificate private keys, passwords or device authorization files are included in the repository.

## Software checks

Hosted-origin package build and ES5 syntax check passed. Companion tests: **21 passed, 1 skipped** (local PostgreSQL integration test lacked its dedicated database). Earlier browser checks used fixtures; they do not establish real AI inference or physical TV rendering.

## Still required before full device acceptance

- Inspect actual TV rendering, focus, input and QR readability.
- Pair a real phone through the physical TV and verify matching-number approval.
- Connect a provider privately and verify a real answer and billing behavior.
- Verify phone chat during streaming, return-to-TV sync, restart persistence and dismissal/navigation.

Installation and visible/running status are confirmed; these remaining checks are not claimed complete. See [the installation guide](../samsung-install.md) and [acceptance checklist](../../samsung/README.md#acceptance-checklist).

## Pairing origin regression

After installation, the user confirmed the actual companion screen and reported an origin error when starting pairing. On-device network inspection reproduced `POST /api/tv` with `Origin: file://` and HTTP 403; the server previously accepted only `null` for packaged clients. The fix allows exactly `file://` and `null` for TV bootstrap/bearer requests, with no credentialed CORS. Phone cookies and provider-key setup remain restricted. Regression tests cover both native origins, rejected cookie/key access and untrusted origins. Production fix `dd5ad57` was deployed successfully. A fresh diagnostic request from the physical TV returned HTTP 201 for bootstrap, HTTP 200 for pairing, and both a pairing link and QR image. The temporary diagnostic room was deleted successfully; no user pairing was changed. The Samsung and Android GitHub workflows passed. Local service tests passed 23 with one PostgreSQL test skipped; both browser flows and ES5 checks passed. Real phone pairing and a live provider answer still need user confirmation.
