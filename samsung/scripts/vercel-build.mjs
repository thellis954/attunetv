import { cpSync, mkdirSync, rmSync } from 'node:fs';
rmSync('public', { recursive: true, force: true });
mkdirSync('public');
for (const name of ['app.js', 'style.css', 'config.js']) cpSync('web/' + name, 'public/' + name);
console.log('Prepared Vercel static assets. Credentials and conversation storage remain server-side.');
