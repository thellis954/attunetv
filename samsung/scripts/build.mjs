import { cpSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
const endpoint = process.env.ATTUNE_SERVICE_ORIGIN || '';
if (endpoint) { const url = new URL(endpoint); if (url.protocol !== 'https:' || url.origin !== endpoint || url.username || url.password) throw new Error('ATTUNE_SERVICE_ORIGIN must be an HTTPS origin without a path.'); }
const output = resolve('dist'), bundle = resolve(output, 'tizen');
rmSync(output, { recursive: true, force: true }); mkdirSync(output, { recursive: true }); cpSync('web', bundle, { recursive: true });
writeFileSync(resolve(bundle, 'config.js'), 'window.ATTUNE_CONFIG = ' + JSON.stringify({ api: endpoint, packaged: true }) + ';\n');
const xmlEndpoint = endpoint.replaceAll('&', '&amp;').replaceAll('"', '&quot;');
writeFileSync(resolve(bundle, 'config.xml'), `<?xml version="1.0" encoding="UTF-8"?>
<widget xmlns="http://www.w3.org/ns/widgets" xmlns:tizen="http://tizen.org/ns/widgets" id="https://attunetv.app/samsung" version="0.1.0" viewmodes="maximized">
  <tizen:application id="AttuneTV01.Companion" package="AttuneTV01" required_version="4.0"/>
  <name>AttuneTV</name><content src="index.html"/><icon src="icon.png"/>
  <tizen:profile name="tv-samsung"/>
  <tizen:privilege name="http://tizen.org/privilege/internet"/>
  <tizen:setting screen-orientation="landscape" context-menu="disable" background-support="disable" encryption="disable" install-location="auto" hwkey-event="enable"/>
  ${endpoint ? '<access origin="' + xmlEndpoint + '" subdomains="false"/>' : ''}
  <tizen:content-security-policy>default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' ${xmlEndpoint}; object-src 'none'; base-uri 'none'</tizen:content-security-policy>
  <tizen:metadata key="http://samsung.com/tv/metadata/multitasking.support" value="true"/>
</widget>\n`);
// Small original geometric icon, generated from raw RGBA by Python stdlib.
execFileSync('python3', ['scripts/icon.py', resolve(bundle, 'icon.png')]);
const artifact = resolve(output, 'AttuneTV-Samsung-0.1.0-UNSIGNED.wgt');
execFileSync('python3', ['-c', 'import zipfile,pathlib,sys\nroot=pathlib.Path(sys.argv[1])\nwith zipfile.ZipFile(sys.argv[2],"w",zipfile.ZIP_DEFLATED) as z:\n for p in sorted(root.rglob("*")):\n  if p.is_file(): z.write(p,p.relative_to(root))', bundle, artifact]);
const digest = createHash('sha256').update(readFileSync(artifact)).digest('hex');
writeFileSync(resolve(output, 'SHA256SUMS.txt'), digest + '  AttuneTV-Samsung-0.1.0-UNSIGNED.wgt\n');
writeFileSync(resolve(output, 'BUILD-STATUS.txt'), `Unsigned Tizen development package. Not ready for TV installation.\nService configured: ${!!endpoint}\nSamsung certificate signing and device testing are required.\n${endpoint ? '' : 'This package shows service setup needed. Rebuild with ATTUNE_SERVICE_ORIGIN after HTTPS deployment.\n'}`);
console.log('Built unsigned Samsung package. Service configured: ' + !!endpoint + '. Signing and device verification remain required.');
