import { readFileSync } from 'node:fs';
import { parse } from 'acorn';
for (const path of ['web/app.js', 'web/config.js']) parse(readFileSync(path, 'utf8'), { ecmaVersion: 5 });
console.log('Client JavaScript parses as ES5 for Tizen 4. Physical TV validation is still required.');
