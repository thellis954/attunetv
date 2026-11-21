import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('downloads', ROOT/'scripts/package-downloads.py')
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
VERSION = json.loads((ROOT/'samsung/package.json').read_text())['version']


class DownloadTests(unittest.TestCase):
    def build(self, root, scenario):
        out=root/'out'; dist=root/'dist'; out.mkdir(); dist.mkdir()
        payload=b'fixture APK'; (out/'AttuneTV.apk').write_bytes(payload)
        info={'sha256':hashlib.sha256(payload).hexdigest(),'versionName':'0.5.1','commit':'a'*40}
        (out/'release-info.json').write_text(json.dumps(info))
        if scenario=='corrupt_apk': (out/'AttuneTV.apk').write_bytes(b'changed')
        with zipfile.ZipFile(dist/f'AttuneTV-Samsung-{VERSION}-UNSIGNED.wgt','w') as z:
            z.writestr('config.xml',f'<widget version="{"9.9.9" if scenario=="version" else VERSION}"/>')
            z.writestr('config.js','window.ATTUNE_CONFIG = '+json.dumps({'api':'' if scenario=='offline' else module.ORIGIN,'packaged':True})+';\n')
            if scenario=='signed': z.writestr('author-signature.xml','fixture')
        module.package(out,dist)
        return out

    def test_public_bundle_is_complete_and_checksums_match(self):
        with tempfile.TemporaryDirectory() as temp:
            out=self.build(Path(temp),'valid')
            for line in (out/'SHA256SUMS.txt').read_text().splitlines():
                digest,name=line.split('  ')
                self.assertEqual(digest,hashlib.sha256((out/name).read_bytes()).hexdigest())
            with zipfile.ZipFile(out/'AttuneTV-Setup.zip') as z:
                for name in ['START-HERE.txt','install.sh','Install-Mac.command','Install-Windows.ps1','AttuneTV.apk','AttuneTV-Samsung-UNSIGNED.wgt','docs/install.md','docs/samsung-install.md','docs/ai-setup.md']:
                    self.assertIn(name,z.namelist())
                self.assertEqual(z.read('VERSION.txt'),b'0.5.1\n')
                for line in z.read('SHA256SUMS.txt').decode().splitlines():
                    digest,name=line.split('  ');self.assertEqual(digest,hashlib.sha256(z.read(name)).hexdigest())

    def test_rejects_inconsistent_or_device_signed_packages(self):
        for scenario in ['offline','version','signed','corrupt_apk']:
            with self.subTest(scenario=scenario),tempfile.TemporaryDirectory() as temp:
                with self.assertRaises(ValueError): self.build(Path(temp),scenario)

if __name__=='__main__':unittest.main()
