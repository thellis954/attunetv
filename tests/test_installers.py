import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class InstallerTests(unittest.TestCase):
    def run_installer(self, kind, scenario='success', address='192.168.1.50', corrupt=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for p in (ROOT / 'installers').iterdir(): shutil.copy(p, root / p.name)
            (root / 'AttuneTV.apk').write_bytes(b'fixture APK')
            (root / 'SHA256SUMS.txt').write_text(hashlib.sha256(b'fixture APK').hexdigest() + '  AttuneTV.apk\n')
            (root / 'VERSION.txt').write_text('0.5.1\n')
            if corrupt: (root / 'AttuneTV.apk').write_bytes(b'corrupted')
            tools = root / 'bin'; tools.mkdir(); adb = tools / 'adb'
            adb.write_text('''#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
args=sys.argv[1:]; mode=os.environ['SCENARIO']
with open(os.environ['CALL_LOG'],'a') as log: log.write(json.dumps(args)+'\\n')
if args[0]=='connect': print('connected'); sys.exit(0)
if args[2]=='get-state':
 print('unauthorized' if mode=='unauthorized' else 'device'); sys.exit(1 if mode=='unauthorized' else 0)
if args[2]=='install':
 print('Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]' if mode=='signature' else 'Success'); sys.exit(1 if mode=='signature' else 0)
if args[2:5]==['shell','dumpsys','package']: print('  versionName='+('0.4.2' if mode=='old' else '0.5.1')); sys.exit(0)
if args[2:5]==['shell','am','start']: print('Starting'); sys.exit(0)
sys.exit(99)
'''); adb.chmod(0o755)
            env = dict(os.environ, PATH=str(tools)+os.pathsep+os.environ['PATH'], SCENARIO=scenario, CALL_LOG=str(root/'calls'))
            command = ['bash', str(root/'install.sh')] if kind=='bash' else ['pwsh','-NoProfile','-File',str(root/'Install-Windows.ps1')]
            result = subprocess.run(command, input=address+'\n\n', text=True, capture_output=True, env=env, timeout=30)
            calls = [json.loads(line) for line in (root/'calls').read_text().splitlines()] if (root/'calls').exists() else []
            return result, calls

    def test_installers_stop_safely_and_verify_versions(self):
        kinds = ['bash'] + (['powershell'] if shutil.which('pwsh') else [])
        for kind in kinds:
            for scenario in ['success', 'unauthorized', 'signature', 'old', 'corrupt', 'bad_address']:
                with self.subTest(kind=kind, scenario=scenario):
                    result, calls = self.run_installer(kind, scenario, address='bad;command' if scenario=='bad_address' else '192.168.1.50', corrupt=scenario=='corrupt')
                    self.assertEqual(result.returncode==0, scenario=='success', result.stdout+result.stderr)
                    self.assertFalse(any('uninstall' in c or 'settings' in c or '-d' in c for c in calls))
                    if scenario in ['unauthorized','corrupt','bad_address']: self.assertFalse(any('install' in c for c in calls))
                    if scenario!='success': self.assertFalse(any('am' in c for c in calls))
                    else:
                        self.assertIn(['-s','192.168.1.50:5555','install','-r','AttuneTV.apk'], calls)
                        self.assertIn('Verified installed version: 0.5.1', result.stdout)


if __name__ == '__main__': unittest.main()
