/* Static server for the ORIGINAL-vs-CURRENT review page.
   The handoff showroom cannot be opened as file:// (its runtime fetches
   support.js + faces/*.js and the browser blocks it), so everything is
   mounted under one HTTP origin. */
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

import { fileURLToPath } from 'node:url';
const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = 'D:/Apps/WearOS Apps/WatchFaces';
const MOUNTS = {
  '/orig': `${ROOT}/WatchFaces Collection 3/Updated Designs with Complications Support/Premium Smartwatch Face Collection-handoff/premium-smartwatch-face-collection/project`,
  '/faces': `${ROOT}/Dialed App/faces`,
  '/fonts': `${ROOT}/Dialed App/faces/collection3-tools/fonts`,
  '/vendor': path.join(HERE, 'vendor'),
  '': HERE,
};
const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.jsx': 'text/javascript', '.mjs': 'text/javascript',
  '.json': 'application/json', '.png': 'image/png', '.xml': 'text/xml', '.ttf': 'font/ttf', '.svg': 'image/svg+xml',
};

/* "Am I looking at the latest?" — the page must answer that itself. Compare when
   the face was last BUILT with when it was last MEASURED, so a stale check can
   never be mistaken for a passing one. */
function status(query) {
  const dir = new URLSearchParams(query).get('dir') || 'Vakt-GT';
  const stat = (p) => { try { return fs.statSync(p).mtimeMs; } catch { return null; } };
  const built = stat(`${MOUNTS['/faces']}/${dir}/app/src/main/res/raw/watchface.xml`);
  const measured = stat(path.join(HERE, '.check', 'fidelity.json'));
  return { dir, built, measured, stale: !!(built && measured && built > measured + 1000), now: Date.now() };
}

http.createServer((req, res) => {
  const [rawPath, query = ''] = req.url.split('?');
  const url = decodeURIComponent(rawPath);
  if (url === '/status.json') {
    res.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(status(query)));
    return;
  }
  const mount = Object.keys(MOUNTS).find(m => m && url.startsWith(m + '/'));
  const file = mount ? path.join(MOUNTS[mount], url.slice(mount.length)) : path.join(MOUNTS[''], url === '/' ? 'compare.html' : url);
  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404); res.end('not found: ' + file); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    res.end(buf);
  });
}).listen(8099, () => console.log('http://localhost:8099'));
