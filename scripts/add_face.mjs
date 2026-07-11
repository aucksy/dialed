// Dialed — the "add a face" routine (build-guide §3A).
//
// Because every face is reused from the fablecollection submodule, adding faces is:
//   1. pull the latest faces,   2. regenerate facepacks + catalog + previews.
// CI then builds → validates → tokenises → bundles them on the next `dialed-v*` tag.
//
// Usage:
//   node scripts/add_face.mjs            # sync submodule to latest + regenerate
//   node scripts/add_face.mjs --no-sync  # regenerate only (submodule already at desired commit)
//
// After running: rebuild/verify, then commit the submodule bump + facepacks + catalog and
// push a `dialed-v*` tag so CI produces the updated bundle.

import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sync = !process.argv.includes('--no-sync');

function run(cmd) {
  console.log(`$ ${cmd}`);
  execSync(cmd, { cwd: ROOT, stdio: 'inherit' });
}

if (sync) {
  console.log('→ Syncing faces/ submodule to remote latest…');
  run('git submodule update --remote --init faces');
}

console.log('→ Regenerating facepacks + catalog + previews…');
run(`node "${path.join('tools', 'gen-facepacks.mjs')}" "${ROOT}"`);

console.log(`
Done. Next:
  1. Review the diff (submodule bump, facepacks/, app previews, FaceCatalog.kt).
  2. Commit:   git add -A && git commit -m "faces: sync + regenerate facepacks"
  3. Tag:      git tag dialed-vX.Y.Z && git push origin main --follow-tags
     CI then builds → validates → tokenises → bundles the collection.
`);
