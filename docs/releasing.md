# Publishing an AttuneTV release

[← Contributor guide](../CONTRIBUTING.md)

Users download **AttuneTV.apk** from [GitHub Releases](https://github.com/thellis954/attunetv/releases). The permanent entry point is:

```text
https://github.com/thellis954/attunetv/releases/latest/download/AttuneTV.apk
```

Downloads do not require a GitHub account. This is a sideloaded preview release, not an Amazon Appstore listing or an automatic updater.

## Signing identity

The public certificate fingerprint is pinned in [`release/signing-certificate.sha256`](../release/signing-certificate.sha256). Every public APK must match it. The key retains the signing identity of the original local 0.4.1 installation, allowing that installation to update without clearing data. Its certificate subject still says `Android Debug`; that historical subject does not make the APK debuggable. The packaging check rejects debuggable APKs.

The private key has a separately encrypted PKCS#12 release copy. It is **not in Git**. Keep an encrypted backup of the keystore and its password in the maintainer's secure storage; GitHub secret values cannot be retrieved as a backup. Losing the key can prevent updates for existing users.

The GitHub **`release` environment** holds:

| Secret | Value |
| --- | --- |
| `APK_KEYSTORE_B64` | Base64 of the encrypted PKCS#12 keystore, alias `attunetv`. |
| `APK_KEYSTORE_PASSWORD` | Keystore/key password. |

Configure that environment to allow release **tags matching `v*` only**. Never put the key/password in repository files, workflow YAML, issue bodies or chat. Use standard input when provisioning GitHub secrets. The certificate fingerprint is public and safe to commit.

## Publish the next version

1. Increase **both** `versionCode` and `versionName` in `app/build.gradle.kts`. Keep versionName in `MAJOR.MINOR.PATCH` form.
2. Write `docs/releases/v<VERSION>.md` with changes, compatibility, validation and any known limits. Keep the README's feature status accurate.
3. Run `./gradlew clean assembleRelease testDebugUnitTest lintRelease -Pdistribution=true`. A public build cannot enable `streamingVerification`.
4. Commit and push to `claude/blissful-hypatia-4dfwls`. Confirm the intended commit and passing branch checks.
5. Tag that commit and push the tag. For example, **for a future 0.4.3 release**:

```bash
git tag -a v0.4.3 -m "AttuneTV 0.4.3"
git push origin v0.4.3
```

6. Watch **Actions → Publish APK**. The tag must point to a commit included in the project branch.
7. Download the public APK anonymously and check its version, checksum and signing certificate. Test the release on a real TV and record any runtime verification separately.

The workflow builds from the tag, runs unit tests and lint, then transfers the unsigned APK and test results to a separate signing job. Release secrets exist only in that job's signing step. The packaging script checks the package, exact tag/version match, non-debuggable flag, passing tests and pinned certificate. The publication script requires a higher versionCode and unchanged certificate compared with the previous public release.

Assets are uploaded to a **draft**, downloaded again and compared byte-for-byte before publication. Only then does the workflow publish and update the `latest` link. Assets include the APK, `SHA256SUMS.txt` and `release-info.json` with source commit and test totals.

## Failed or interrupted publication

Never retag a published version or replace its APK. Publish a new version with a higher versionCode instead. If a run fails after creating a draft, inspect that draft and the workflow logs; the script intentionally refuses to overwrite an existing release. A never-published draft can be reviewed and removed before retrying the same unchanged tag. Do not delete a public release to work around a signing or version error.

Developer APKs use separate keys and `-dev` (or `-verify`) suffixes. Old CI artifacts may also use different keys. A signature mismatch cannot be fixed by `install -r`; do not silently uninstall and erase the user's settings. Explain the migration before any reset.
