#!/usr/bin/env python3
"""Sign and validate a distribution APK. Private key/passwords come only from env."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET


def run(*args):
    result = subprocess.run(args, text=True, capture_output=True)
    if result.returncode:
        # Signing errors can include paths or provider details. Do not echo secrets/logs.
        raise RuntimeError(f"{Path(args[0]).name} failed (exit {result.returncode}).")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--test-results", required=True, type=Path)
    parser.add_argument("--sdk", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=Path("release-output"))
    args = parser.parse_args()
    if not re.fullmatch(r"v\d+\.\d+\.\d+", args.tag):
        raise ValueError("Release tag must be vMAJOR.MINOR.PATCH.")
    if not re.fullmatch(r"[a-f0-9]{40}", args.commit):
        raise ValueError("Use the full source commit SHA.")

    totals = {k: 0 for k in ("tests", "failures", "errors", "skipped")}
    for report in args.test_results.glob("TEST-*.xml"):
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    if not totals["tests"] or any(totals[k] for k in ("failures", "errors", "skipped")):
        raise ValueError("A release needs nonempty, fully passing unit-test results.")

    build_tools = args.sdk / "build-tools/35.0.0"
    aapt = str(build_tools / "aapt")
    signer = str(build_tools / "apksigner")
    badging = run(aapt, "dump", "badging", str(args.apk))
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package or package[1] != "com.attunetv.launcher" or package[3] != args.tag[1:]:
        raise ValueError("APK package/version does not match the release tag; dev/verify builds are not distributable.")
    if "application-debuggable" in badging:
        raise ValueError("A public release must not be debuggable.")

    for variable in ("APK_KEYSTORE_PATH", "APK_KEYSTORE_PASSWORD", "APK_KEY_ALIAS", "APK_KEY_PASSWORD"):
        if not os.environ.get(variable):
            raise ValueError(f"Missing signing configuration: {variable}")
    fingerprint = (Path(__file__).resolve().parents[1] / "release/signing-certificate.sha256").read_text().strip()
    if not re.fullmatch(r"[a-f0-9]{64}", fingerprint):
        raise ValueError("Invalid public certificate fingerprint.")
    args.output.mkdir(parents=True, exist_ok=True)
    if any(args.output.iterdir()):
        raise ValueError("Output directory must be empty; do not overwrite a packaged release.")
    with tempfile.TemporaryDirectory(prefix="attunetv-sign-") as temp:
        apk = Path(temp) / "AttuneTV.apk"
        run(signer, "sign", "--ks", os.environ["APK_KEYSTORE_PATH"],
            "--ks-key-alias", os.environ["APK_KEY_ALIAS"],
            "--ks-pass", "env:APK_KEYSTORE_PASSWORD", "--key-pass", "env:APK_KEY_PASSWORD",
            "--out", str(apk), str(args.apk))
        certificates = run(signer, "verify", "--verbose", "--print-certs", str(apk))
        found = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)", certificates)
        if found != [fingerprint]:
            raise ValueError("APK signing identity does not match the pinned release certificate.")
        if run(aapt, "dump", "badging", str(apk)) != badging:
            raise ValueError("APK metadata changed during signing.")
        payload = apk.read_bytes()
        digest = hashlib.sha256(payload).hexdigest()
        (args.output / "AttuneTV.apk").write_bytes(payload)
    info = {
        "applicationId": package[1], "versionName": package[3], "versionCode": int(package[2]),
        "tag": args.tag, "commit": args.commit, "apk": "AttuneTV.apk", "sha256": digest,
        "signingCertificateSha256": fingerprint, "unitTests": totals,
        "chatStatus": "Native preview; end-to-end account and provider verification pending",
    }
    (args.output / "release-info.json").write_text(json.dumps(info, indent=2) + "\n")
    (args.output / "SHA256SUMS.txt").write_text(f"{digest}  AttuneTV.apk\n")
    print(json.dumps(info, indent=2))


if __name__ == "__main__":
    main()
