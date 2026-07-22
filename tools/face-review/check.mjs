/* ============================================================================
   check.mjs — the self-verification step, made mechanical.

   Renders the DESIGN and the BUILD into identical 450x450 rasters with the clock
   frozen and animation off, then scores the difference per dial region.
   "Does it match?" stops being my opinion and becomes a number.

     node check.mjs [--face WF-A2] [--dir Vakt-GT] [--spec cat-a2] [--theme t0]

   Requires the review server on :8099 (node serve.mjs).
   ========================================================================== */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(HERE, '.check');
const PORT = 8099;

const arg = (k, d) => {
  const i = process.argv.indexOf('--' + k);
  return i > 0 ? process.argv[i + 1] : d;
};
const FACE = arg('face', 'WF-A2');
const DIR = arg('dir', 'Vakt-GT');
const SPEC = arg('spec', 'cat-a2');
const THEME = arg('theme', 't0');
const TYPES = arg('types', '');      // e.g. --types 0:EMPTY,1:EMPTY,3:EMPTY  (the cleared-dial test)

const CHROME = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
].find(p => fs.existsSync(p));
if (!CHROME) { console.error('Chrome not found'); process.exit(2); }

fs.mkdirSync(OUT, { recursive: true });

function shoot(side, file) {
  const url = `http://localhost:${PORT}/frame.html?side=${side}&face=${FACE}&dir=${DIR}&spec=${SPEC}&theme=${THEME}`
    + (TYPES ? `&types=${encodeURIComponent(TYPES)}` : '');
  const r = spawnSync(CHROME, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars',
    '--virtual-time-budget=6000', '--window-size=450,450',
    `--screenshot=${file}`, url,
  ], { encoding: 'utf8' });
  if (!fs.existsSync(file)) { console.error(r.stderr); throw new Error('screenshot failed: ' + side); }
}

/* Regions come from the DESIGN's own geometry, not from guesses: every register
   plate in the source spec becomes a scored region, so a broken sub-dial can never
   hide inside a good whole-dial average. */
async function regionsFor() {
  const specPath = path.resolve(
    'D:/Apps/WearOS Apps/WatchFaces/WatchFaces Collection 3/Updated Designs with Complications Support',
    'Premium Smartwatch Face Collection-handoff/premium-smartwatch-face-collection/project/faces', SPEC + '.js');
  const mod = await import(pathToFileURL(specPath).href);
  const face = mod.category.faces.find(f => f.id === FACE);
  const regs = [];
  let n = 0;
  for (const L of face.layers) {
    if (L.t === 'plate') {
      n++;
      const label = L.cy < 200 ? 'Top register' : L.cx < 180 ? 'Left register' : 'Hero register';
      regs.push({ name: `${label} (r${L.r})`, cx: L.cx, cy: L.cy, r: L.r + (L.rim || 3) + 9 });
    }
    if (L.t === 'rect' && L.w <= 60) regs.push({ name: 'Date window', x: L.x - 6, y: L.y - 6, w: L.w + 12, h: L.h + 34 });
  }
  regs.push({ name: 'Flange + indices', cx: 225, cy: 225, r: 225 });
  regs.push({ name: 'Dial centre + hands', cx: 225, cy: 225, r: 120 });
  if (!n) regs.push({ name: 'Whole face', cx: 225, cy: 225, r: 225 });
  return regs;
}


/* ── NOISE FLOOR ────────────────────────────────────────────────────────────
   The design is drawn by Chrome's vector rasteriser; our dial art is baked by
   resvg. Two rasterisers never agree to the pixel on antialiased glyph edges, so
   a diff can never reach 0.00% however correct the build is. This measures that
   floor: take the DESIGN's own SVG, rasterise it with resvg, and diff it against
   Chrome's render of the very same SVG. Whatever that scores is noise — the build
   is "identical to the design" once it lands at the floor, and chasing lower is
   chasing the rasteriser, not the design. */
async function floorFor(origPng) {
  const url = `http://localhost:${PORT}/frame.html?side=orig&face=${FACE}&dir=${DIR}&spec=${SPEC}&theme=${THEME}`;
  const r = spawnSync(CHROME, ['--headless=new', '--disable-gpu', '--hide-scrollbars',
    '--virtual-time-budget=6000', '--window-size=450,450', '--dump-dom', url], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  const m = /<svg[\s\S]*<\/svg>/.exec(r.stdout || '');
  if (!m) return null;
  const mod = await import(pathToFileURL(path.resolve(
    'D:/Apps/WearOS Apps/WatchFaces/Dialed App/faces/collection3-tools/gen/node_modules/@resvg/resvg-js/index.js')).href);
  const Resvg = mod.Resvg || mod.default?.Resvg;
  const FONTS = path.resolve('D:/Apps/WearOS Apps/WatchFaces/Dialed App/faces/collection3-tools/fonts');
  // the DOM serialisation has no xmlns (HTML parsing implies it); resvg needs it
  let svgStr = m[0];
  if (!/xmlns=/.test(svgStr)) svgStr = svgStr.replace('<svg', '<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"');
  const png = new Resvg(svgStr, {
    fitTo: { mode: 'width', value: 450 },
    font: { fontFiles: fs.readdirSync(FONTS).map(f => path.join(FONTS, f)), loadSystemFonts: false, defaultFontFamily: 'Archivo' },
  }).render().asPng();
  const file = path.join(OUT, `${DIR}_${THEME}_design_resvg.png`);
  fs.writeFileSync(file, png);
  const out = spawnSync('python', [path.join(HERE, 'fidelity.py'), origPng, file,
    path.join(OUT, `${DIR}_${THEME}_floor.png`), regJson], { encoding: 'utf8' });
  const hit = /WHOLE DIAL\s+([\d.]+)%/.exec(out.stdout || '');
  return hit ? parseFloat(hit[1]) : null;
}

const origPng = path.join(OUT, `${DIR}_${THEME}_design.png`);
const curPng = path.join(OUT, `${DIR}_${THEME}_build.png`);
const diffPng = path.join(OUT, `${DIR}_${THEME}_diff.png`);
const regJson = path.join(OUT, `${DIR}_regions.json`);

shoot('orig', origPng);
shoot('current', curPng);
fs.writeFileSync(regJson, JSON.stringify(await regionsFor(), null, 2));

// Measure the floor FIRST so the comparison can flag against it instead of a guess.
const floor = await floorFor(origPng);
const floorJson = path.join(OUT, `${DIR}_${THEME}_floor.json`);
const py = spawnSync('python', [path.join(HERE, 'fidelity.py'), origPng, curPng, diffPng, regJson,
  fs.existsSync(floorJson) ? floorJson : String(floor ?? 0)], { encoding: 'utf8', stdio: 'inherit' });

/* ⭐ FIT CHECK — run it here so it CANNOT be skipped. The pixel diff above compares the
   build to the design, but it is blind to a slot's own content colliding with the dial art
   around it: an oversized provider icon over a register's numerals barely moves a
   whole-region percentage, and it is invisible at page scale. That defect shipped to the
   owner once. Arithmetic catches it; eyes did not. */
{
  const fc = spawnSync('node', [path.resolve(
    'D:/Apps/WearOS Apps/WatchFaces/Dialed App/faces/collection3-tools/gen/fit-check.mjs'), FACE],
    { encoding: 'utf8', cwd: path.resolve('D:/Apps/WearOS Apps/WatchFaces/Dialed App/faces/collection3-tools') });
  console.log('');
  console.log('  SLOT CONTENT vs DIAL ART');
  console.log((fc.stdout || fc.stderr || '  (fit check did not run)').trimEnd());
}

{
  const f = floor;
  // fold the floor into the JSON the review page reads, so the score is always
  // shown next to the best score physically achievable
  const jf = diffPng.replace(/\.png$/, '.json');
  if (fs.existsSync(jf) && f != null) {
    const j = JSON.parse(fs.readFileSync(jf, 'utf8'));
    j.floor = f;
    j.face = `${DIR} · ${THEME}`;
    fs.writeFileSync(jf, JSON.stringify(j, null, 2));
    fs.writeFileSync(path.join(OUT, 'fidelity.json'), JSON.stringify(j, null, 2));
    fs.copyFileSync(origPng, path.join(OUT, 'design.png'));
    fs.copyFileSync(curPng, path.join(OUT, 'build.png'));
    fs.copyFileSync(diffPng, path.join(OUT, 'diff.png'));
  }
  console.log(f == null ? '  (noise floor: could not measure)'
    : `  NOISE FLOOR (same SVG, two rasterisers): ${f.toFixed(2)}%  <- a perfect build cannot score below this
`);
}
process.exit(py.status ?? 0);
