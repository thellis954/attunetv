#!/usr/bin/env python3
"""Publish a new, fully packaged release; never replace a published APK."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def gh(*args, allow_404=False):
    result = subprocess.run(["gh", *args], text=True, capture_output=True)
    if result.returncode:
        if allow_404 and "HTTP 404" in result.stderr:
            return None
        raise RuntimeError(f"GitHub operation failed: {' '.join(args[:2])}")
    return result.stdout


def main():
    tag = os.environ["RELEASE_TAG"]
    repo = os.environ["GH_REPO"]
    if not re.fullmatch(r"v\d+\.\d+\.\d+", tag):
        raise ValueError("Invalid release tag")
    info = json.loads(Path("release-output/release-info.json").read_text())
    if info["tag"] != tag:
        raise ValueError("Packaged release does not match the tag")
    current = gh("api", f"repos/{repo}/releases/tags/{tag}", allow_404=True)
    if current:
        raise ValueError("A release already exists for this tag. Inspect it; never overwrite a published APK.")
    previous = gh("api", f"repos/{repo}/releases/latest", allow_404=True)
    if previous:
        previous = json.loads(previous)
        with tempfile.TemporaryDirectory() as temp:
            gh("release", "download", previous["tag_name"], "--pattern", "release-info.json", "--dir", temp)
            previous_info = json.loads((Path(temp) / "release-info.json").read_text())
        if info["versionCode"] <= previous_info["versionCode"]:
            raise ValueError("versionCode must increase for an in-place Android update")
        if info["signingCertificateSha256"] != previous_info["signingCertificateSha256"]:
            raise ValueError("Release signer changed; an explicit migration is required")
    assets = ["release-output/AttuneTV.apk", "release-output/SHA256SUMS.txt", "release-output/release-info.json"]
    gh("release", "create", tag, *assets, "--draft", "--verify-tag",
       "--title", f"AttuneTV {info['versionName']} — public preview",
       "--notes-file", f"docs/releases/{tag}.md")
    # Read back uploaded bytes before exposing the release or moving /latest.
    with tempfile.TemporaryDirectory() as temp:
        gh("release", "download", tag, "--dir", temp)
        for asset in assets:
            if (Path(temp) / Path(asset).name).read_bytes() != Path(asset).read_bytes():
                raise ValueError("Uploaded release asset differs from verified local bytes")
    gh("release", "edit", tag, "--draft=false", "--latest")
    published = json.loads(gh("api", f"repos/{repo}/releases/tags/{tag}"))
    if published["draft"] or len(published["assets"]) != len(assets):
        raise ValueError("Release publication could not be confirmed")
    print(published["html_url"])


if __name__ == "__main__":
    main()
