#!/usr/bin/env python3
"""Add the configured Samsung package and guided setup bundle to a signed release."""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ORIGIN = 'https://attunetv-samsung.vercel.app'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package(output, samsung_dist):
    info = json.loads((output / 'release-info.json').read_text())
    if digest(output / 'AttuneTV.apk') != info['sha256']:
        raise ValueError('Signed APK differs from release metadata')
    version = json.loads((ROOT / 'samsung/package.json').read_text())['version']
    source = samsung_dist / f'AttuneTV-Samsung-{version}-UNSIGNED.wgt'
    with zipfile.ZipFile(source) as widget:
        xml = ET.fromstring(widget.read('config.xml'))
        if xml.attrib['version'] != version:
            raise ValueError('Samsung package version mismatch')
        config = widget.read('config.js').decode()
        if json.loads(config.split('=', 1)[1].strip().rstrip(';')) != {'api': ORIGIN, 'packaged': True}:
            raise ValueError('Samsung package is not configured for the public service')
        for name in widget.namelist():
            if Path(name).suffix in {'.p12', '.pri', '.jks', '.keystore'} or Path(name).name in {'author-signature.xml', 'signature1.xml', 'device-profile.xml', 'profiles.xml'}:
                raise ValueError('Public Samsung package must be unsigned and contain no private signing files')
    target = output / 'AttuneTV-Samsung-UNSIGNED.wgt'
    shutil.copyfile(source, target)
    samsung = {'version': version, 'applicationId': 'AttuneTV01.Companion', 'package': target.name,
               'sha256': digest(target), 'commit': info['commit'], 'serviceOrigin': ORIGIN,
               'signed': False, 'requires': 'Samsung TV certificate with your device DUID and installation permission',
               'deviceAcceptance': 'Samsung Home 0.2.1 installed on QN65Q65FN; bundled dock marks, tile widths and scrolling checked on Tizen 4. User visual and per-app launch acceptance remain separate'}
    (output / 'samsung-release-info.json').write_text(json.dumps(samsung, indent=2) + '\n')
    # Inside the setup ZIP, the manifest covers bundled payloads, not the ZIP itself.
    files = ['AttuneTV.apk', target.name, 'release-info.json', 'samsung-release-info.json']
    checksums = ''.join(f'{digest(output / name)}  {name}\n' for name in files)
    (output / 'SHA256SUMS.txt').write_text(checksums)
    bundle = output / 'AttuneTV-Setup.zip'
    with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name in files + ['SHA256SUMS.txt']:
            archive.write(output / name, name)
        for path in sorted((ROOT / 'installers').glob('*')):
            if path.is_file(): archive.write(path, path.name)
        archive.writestr('START-HERE.txt', (ROOT / 'installers/START-HERE.md').read_text())
        archive.writestr('VERSION.txt', info['versionName'] + '\n')
        for path in [ROOT / 'README.md', ROOT / 'CONTRIBUTING.md', *sorted((ROOT / 'docs').rglob('*.md'))]:
            archive.write(path, str(path.relative_to(ROOT)))
    (output / 'SHA256SUMS.txt').write_text(checksums + f'{digest(bundle)}  {bundle.name}\n')
    print(f"Prepared Android {info['versionName']}, Samsung {version}, and setup ZIP from {info['commit']}")


if __name__ == '__main__':
    package(ROOT / 'release-output', ROOT / 'samsung/dist')
