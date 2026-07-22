/* ============================================================================
   CURRENT BUILD renderer — reads the face's real res/raw/watchface.xml.

   It walks the WHOLE <Scene>: every PartImage, PartDraw (arcs, rectangles),
   PartText, complication slot and clock hand, with the theme / dark / date /
   seconds branches collapsed the way the platform would collapse them.

   ⛔ Nothing is hand-drawn. An earlier harness redrew the registers in JS and
   invented tick marks the design never had; a later one rendered ONLY the slots
   and so hid the native battery needle + arc. Both lied. If an element is not in
   the XML it must not appear here, and if it IS in the XML it must.
   Only [COMPLICATION.*] values are sampled.
   ========================================================================== */

export const SAMPLE = {
  'COMPLICATION.RANGED_VALUE_VALUE': 68, 'COMPLICATION.RANGED_VALUE_MIN': 0, 'COMPLICATION.RANGED_VALUE_MAX': 100,
  'COMPLICATION.GOAL_PROGRESS_VALUE': 8432, 'COMPLICATION.GOAL_PROGRESS_TARGET_VALUE': 10000,
  'COMPLICATION.TEXT': '68%', 'COMPLICATION.TITLE': 'BATT',
  'CONFIGURATION.dark': 0, 'CONFIGURATION.date': 1, 'CONFIGURATION.seconds': 1, 'CONFIGURATION.gauges': 1,
  // ⭐ These are the DESIGN's own simulated values (renderer.jsx `SIM`), not invented
  // ones. A fidelity diff is meaningless if the two sides are drawing different data:
  // a needle at a different angle would read as a design defect when it is only a
  // different number. Keep these in step with SIM if the design pack ever changes.
  BATTERY_PERCENT: 68, STEP_COUNT: 6203, STEP_PERCENT: 62.03, HEART_RATE: 72,
  SECOND: 0, MINUTE: 0, HOUR_0_23: 0, DAY: 19, MONTH: 7,
  DAY_OF_WEEK_S: 'MON', DAY_OF_WEEK_F: 'MONDAY', MONTH_S: 'JUL',
};
const DAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const MONS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
/* Time tokens follow the page's clock — which the diff harness freezes — so both
   sides draw the same instant and the hands can be compared at all. */
function syncClock() {
  const d = new Date();
  SAMPLE.SECOND = d.getSeconds();
  SAMPLE.SECOND_MILLISECOND = d.getSeconds() + d.getMilliseconds() / 1000;
  SAMPLE.MINUTE = d.getMinutes();
  SAMPLE.MINUTE_SECOND = d.getMinutes() + d.getSeconds() / 60;
  SAMPLE.HOUR_0_23 = d.getHours();
  SAMPLE.HOUR_1_12 = ((d.getHours() + 11) % 12) + 1;
  SAMPLE.DAY = d.getDate();
  SAMPLE.MONTH = d.getMonth() + 1;
  SAMPLE.YEAR = d.getFullYear();
  SAMPLE.DAY_OF_WEEK_S = DAYS[d.getDay()];
  SAMPLE.DAY_OF_WEEK_F = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][d.getDay()];
  SAMPLE.MONTH_S = MONS[d.getMonth()];
  PROVIDER_SAMPLE.DATE = { TEXT: String(d.getDate()), TITLE: MONS[d.getMonth()] };
  PROVIDER_SAMPLE.DAY_AND_DATE = { TEXT: `${DAYS[d.getDay()]} ${d.getDate()}`, TITLE: MONS[d.getMonth()] };
}
/* What each system provider actually returns per field, so a dial does not print
   the same sample string twice. */
const PROVIDER_SAMPLE = {
  HEART_RATE: { TEXT: '72', TITLE: 'BPM' },
  WATCH_BATTERY: { TEXT: '68%', TITLE: 'BATT' },
  STEP_COUNT: { TEXT: '8432', TITLE: 'STEPS' },
  DATE: { TEXT: '19', TITLE: 'JUL' },
  DAY_AND_DATE: { TEXT: 'MON 19', TITLE: 'JUL' },
  UNREAD_NOTIFICATION_COUNT: { TEXT: '3', TITLE: 'UNREAD' },
  NEXT_EVENT: { TEXT: 'Standup', TITLE: '09:30' },
  SUNRISE_SUNSET: { TEXT: '19:42', TITLE: 'SET' },
  WORLD_CLOCK: { TEXT: '14:25', TITLE: 'LDN' },
};
let PROVIDER = '';
let CUR = null;                       // the sample feeding the slot being drawn right now

/* ── SAMPLE DATA CATALOGUE ─────────────────────────────────────────────────
   Real providers differ in the two ways that actually stress a register: the RANGE the
   scale has to re-label (0–100% vs 40–200 bpm vs −10–40°) and the LENGTH of the string
   the centre has to hold ("68%" vs "415 kcal"). Each entry is one plausible provider, so
   any dial can be pointed at any of them and the result is the real XML's answer, not a
   guess. `over` exists to prove the goal ring's overflow lap. */
export const SAMPLES = [
  { id: 'batt',  group: 'RANGED_VALUE', label: 'Battery · 0–100%',        min: 0,   max: 100, value: 68,  text: '68%',      title: 'BATT' },
  { id: 'battlo',group: 'RANGED_VALUE', label: 'Battery low · 4%',        min: 0,   max: 100, value: 4,   text: '4%',       title: 'BATT' },
  { id: 'hr',    group: 'RANGED_VALUE', label: 'Heart rate · 40–200 bpm', min: 40,  max: 200, value: 128, text: '128 bpm',  title: 'BPM' },
  { id: 'hrrest',group: 'RANGED_VALUE', label: 'Heart rate · resting 72',  min: 40,  max: 200, value: 72,  text: '72 bpm',   title: 'BPM' },
  { id: 'kcal',  group: 'RANGED_VALUE', label: 'Calories · 0–600 kcal',   min: 0,   max: 600, value: 415, text: '415 kcal', title: 'KCAL' },
  { id: 'dist',  group: 'RANGED_VALUE', label: 'Distance · 0–10 km',      min: 0,   max: 10,  value: 5.2, text: '5.2 km',   title: 'DIST' },
  { id: 'temp',  group: 'RANGED_VALUE', label: 'Temperature · −10–40°',   min: -10, max: 40,  value: 24,  text: '24°',      title: 'TEMP' },
  { id: 'steps', group: 'GOAL_PROGRESS', label: 'Steps · 6,203 of 10,000',    target: 10000, value: 6203,  text: '6203',  title: 'STEPS' },
  { id: 'over',  group: 'GOAL_PROGRESS', label: 'Steps OVER · 11,240 of 10,000', target: 10000, value: 11240, text: '11240', title: 'STEPS' },
  { id: 'active',group: 'GOAL_PROGRESS', label: 'Active min · 22 of 30',      target: 30,    value: 22,    text: '22',    title: 'ACTIVE' },
  { id: 'gkcal', group: 'GOAL_PROGRESS', label: 'Calories · 540 of 800',      target: 800,   value: 540,   text: '540',   title: 'KCAL' },
  { id: 'unread',group: 'TEXT', label: 'Unread · 3',            text: '3',            title: 'UNREAD' },
  { id: 'event', group: 'TEXT', label: 'Next event · Standup',  text: 'Standup 09:30', title: '09:30' },
  { id: 'world', group: 'TEXT', label: 'World clock · LDN',     text: '14:25',        title: 'LDN' },
  { id: 'date',  group: 'TEXT', label: 'Date · 19',             text: '19',           title: 'JUL' },
];
export const SAMPLE_BY_ID = Object.fromEntries(SAMPLES.map(s => [s.id, s]));

/* Feed one sample into the expression environment. Ranged and goal fields are BOTH set
   every time: a slot's type decides which of them the XML actually reads, and leaving the
   other stale is how a dial ends up showing last week's numbers. */
function applySample(s) {
  CUR = s || null;
  if (!s) return;
  SAMPLE['COMPLICATION.RANGED_VALUE_MIN'] = s.min != null ? s.min : 0;
  SAMPLE['COMPLICATION.RANGED_VALUE_MAX'] = s.max != null ? s.max : (s.target != null ? s.target : 100);
  SAMPLE['COMPLICATION.RANGED_VALUE_VALUE'] = s.value != null ? s.value : 0;
  SAMPLE['COMPLICATION.GOAL_PROGRESS_TARGET_VALUE'] = s.target != null ? s.target : 100;
  SAMPLE['COMPLICATION.GOAL_PROGRESS_VALUE'] = s.value != null ? s.value : 0;
  SAMPLE['COMPLICATION.TEXT'] = s.text != null ? s.text : '';
  SAMPLE['COMPLICATION.TITLE'] = s.title != null ? s.title : '';
}

/* Which sample a dial shows by default: the one its own default provider would supply,
   so the page opens on the real first-install state rather than an arbitrary pick. */
export const DEFAULT_SAMPLE_FOR = {
  WATCH_BATTERY: 'batt', HEART_RATE: 'hrrest', STEP_COUNT: 'steps', DATE: 'date',
  DAY_AND_DATE: 'date', UNREAD_NOTIFICATION_COUNT: 'unread', NEXT_EVENT: 'event',
  WORLD_CLOCK: 'world', SUNRISE_SUNSET: 'world', EMPTY: 'batt',
};

const NS = 'http://www.w3.org/2000/svg';
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const mk = (tag, attrs) => { const e = document.createElementNS(NS, tag); for (const k in attrs) if (attrs[k] != null) e.setAttribute(k, attrs[k]); return e; };

function evalExpr(expr) {
  if (expr == null) return null;
  let s = String(expr);
  if (/^-?[\d.]+$/.test(s.trim())) return parseFloat(s);
  // NOTE the lowercase: configuration keys are `[CONFIGURATION.dark]`, `[CONFIGURATION.date]`.
  // An uppercase-only class silently failed to substitute them, JS then read `[CONFIGURATION.dark]`
  // as an array literal, threw, and every gated group fell through to "draw" — so the pure-black
  // dark-mode twin painted straight over the themed dial.
  s = s.replace(/\[([A-Za-z0-9_.]+)\]/g, (_, k) => {
    const v = SAMPLE[k];
    if (v == null) throw new Error('no sample for [' + k + ']');
    return typeof v === 'string' ? JSON.stringify(v) : String(v);
  });
  s = s.replace(/\bclamp\(/g, '__clamp(').replace(/\bfloor\(/g, 'Math.floor(').replace(/\bround\(/g, 'Math.round(');
  return Function('__clamp', '"use strict";return (' + s + ');')(clamp);
}

const col = (c) => (!c ? null : /^#[0-9a-f]{8}$/i.test(c) ? '#' + c.slice(3) : c);
const alphaOf = (c) => (/^#[0-9a-f]{8}$/i.test(c) ? parseInt(c.slice(1, 3), 16) / 255 : 1);
const fontFamily = (f) => ((/saira/i.test(f || '') ? 'Saira SemiCondensed' : /barlow/i.test(f || '') ? 'Barlow SemiCondensed'
  : /jost/i.test(f || '') ? 'Jost' : /marcellus/i.test(f || '') ? 'Marcellus'
  : /bigshoulders/i.test(f || '') ? 'Big Shoulders Display' : /spacegrotesk/i.test(f || '') ? 'Space Grotesk' : 'Archivo')
  + ', sans-serif'); // fall to sans, never the browser's serif default (frame.html loads the real TTFs)
const fontWeight = (f) => (/(\d{3})$/.exec(f || '') || [, '400'])[1];

function arcPath(cx, cy, r, a0, a1) {
  const p = (a) => [cx + r * Math.sin(a * Math.PI / 180), cy - r * Math.cos(a * Math.PI / 180)];
  let span = a1 - a0;
  if (Math.abs(span) < 0.05) return '';
  if (Math.abs(span) >= 360) span = 359.99 * Math.sign(span);
  const [x0, y0] = p(a0), [x1, y1] = p(a0 + span);
  return `M ${x0.toFixed(2)} ${y0.toFixed(2)} A ${r} ${r} 0 ${Math.abs(span) > 180 ? 1 : 0} ${span > 0 ? 1 : 0} ${x1.toFixed(2)} ${y1.toFixed(2)}`;
}

/* a provider supplies its own icon at runtime; stand in with a neutral glyph so the
   slot is drawn where the real icon lands, never inventing its shape */
/* A provider supplies its own icon at runtime, so the face only ever says "put the
   provider's mark here". The page knows which pretend provider is selected, so it draws
   THAT metric's mark — a heart for a pulse, a bolt for battery, a flame for calories.
   This is simulated DATA, exactly like the "68%" beside it, not invented face artwork:
   the position, the size and the tint all come from the XML. On the watch the real
   provider's own glyph lands in this same box. */
const GLYPHS = {
  batt:   'M3 5.5h11a1 1 0 011 1v5a1 1 0 01-1 1H3a1 1 0 01-1-1v-5a1 1 0 011-1zm14 2v3',
  battlo: 'M3 5.5h11a1 1 0 011 1v5a1 1 0 01-1 1H3a1 1 0 01-1-1v-5a1 1 0 011-1zm14 2v3',
  hr:     'M10 15.5S3.5 11.3 3.5 7.4A3.4 3.4 0 0110 5.6a3.4 3.4 0 016.5 1.8c0 3.9-6.5 8.1-6.5 8.1z',
  hrrest: 'M10 15.5S3.5 11.3 3.5 7.4A3.4 3.4 0 0110 5.6a3.4 3.4 0 016.5 1.8c0 3.9-6.5 8.1-6.5 8.1z',
  kcal:   'M10 16c2.8 0 4.6-1.9 4.6-4.3 0-3.2-3.2-4-2.6-7.7-2 .9-4.3 3-4.3 5.3 0 1-.6 1.6-1.2 1.6-.7 0-1.1-.6-1.1-1.6-1 1.1-1 2.3-1 2.4C4.4 14.1 6.6 16 10 16z',
  gkcal:  'M10 16c2.8 0 4.6-1.9 4.6-4.3 0-3.2-3.2-4-2.6-7.7-2 .9-4.3 3-4.3 5.3 0 1-.6 1.6-1.2 1.6-.7 0-1.1-.6-1.1-1.6-1 1.1-1 2.3-1 2.4C4.4 14.1 6.6 16 10 16z',
  dist:   'M10 3.2a4 4 0 00-4 4c0 3 4 9.6 4 9.6s4-6.6 4-9.6a4 4 0 00-4-4zm0 2.6a1.5 1.5 0 110 3 1.5 1.5 0 010-3z',
  temp:   'M10 3.4a2 2 0 012 2v5.3a3.4 3.4 0 11-4 0V5.4a2 2 0 012-2z',
  steps:  'M6.4 3.6a2.5 3.6 0 1 0 .1 0zM13.6 8.2a2.5 3.6 0 1 0 .1 0z',
  over:   'M6.4 3.6a2.5 3.6 0 1 0 .1 0zM13.6 8.2a2.5 3.6 0 1 0 .1 0z',
  active:  'M10 3.6a6.4 6.4 0 110 12.8 6.4 6.4 0 010-12.8zm0 2.9v3.7l2.6 1.6',
};
function providerIcon(w, h, color) {
  const c = color || '#888';
  const d = CUR && GLYPHS[CUR.id];
  if (d) {
    // The glyph paths are drawn inside roughly x/y 2..18 of a 20-grid, so scaling by w/20
    // left them noticeably smaller than the engraved mark they stand in for. Normalise on
    // the INKED extent (16 units) and re-centre, so the stand-in fills the same box the XML
    // hands the provider.
    const k = w / 16.5;
    const g = mk('g', { transform: `translate(${(-2 * k).toFixed(3)},${(-2 * k).toFixed(3)}) scale(${k.toFixed(4)})` });
    const filled = CUR.id === 'steps' || CUR.id === 'over' || CUR.id === 'kcal' || CUR.id === 'gkcal';
    g.appendChild(mk('path', {
      d, fill: filled ? c : 'none', stroke: c, 'stroke-width': filled ? 0 : 1.7,
      'stroke-linecap': 'round', 'stroke-linejoin': 'round',
    }));
    return g;
  }
  const g = mk('g', { opacity: 0.9 });
  g.appendChild(mk('circle', { cx: w / 2, cy: h / 2, r: Math.min(w, h) / 2 - 1, fill: 'none', stroke: c, 'stroke-width': 1.6 }));
  g.appendChild(mk('circle', { cx: w / 2, cy: h / 2, r: Math.min(w, h) / 6, fill: c }));
  return g;
}

function templateText(fontEl) {
  const tpl = fontEl.querySelector('Template');
  if (!tpl) return fontEl.textContent.trim();
  const params = [...tpl.querySelectorAll('Parameter')].map(p => p.getAttribute('expression'));
  const fmt = [...tpl.childNodes].filter(n => n.nodeType === 3).map(n => n.nodeValue).join('');
  let i = 0;
  return fmt.replace(/%(0\d)?(\.\d+)?[a-z]/g, (spec) => {
    const e = params[i++];
    let v; try { v = evalExpr(e); } catch { v = '—'; }
    if (typeof v === 'string') {
      const field = /COMPLICATION\.(TITLE|TEXT)/.exec(e || '');
      // A sample chosen for this dial wins; otherwise fall back to what the slot's own
      // default system provider would return, so an untouched dial reads truthfully.
      if (field && CUR) return CUR[field[1].toLowerCase()] ?? v;
      const ps = PROVIDER_SAMPLE[PROVIDER];
      return (field && ps && ps[field[1]]) || v;
    }
    const pad = /^%0(\d)d$/.exec(spec);
    if (pad) return String(Math.round(v)).padStart(+pad[1], '0');
    return /f$/.test(spec) ? Math.round(v) : v;
  }).trim();
}

/* ---- <PartDraw> shapes ---------------------------------------------------- */
function drawShape(sh, g) {
  const paint = (el, node) => {
    const fill = node.querySelector('Fill'), stroke = node.querySelector('Stroke');
    el.setAttribute('fill', fill ? col(fill.getAttribute('color')) : 'none');
    if (fill) el.setAttribute('fill-opacity', alphaOf(fill.getAttribute('color')));
    if (stroke) {
      el.setAttribute('stroke', col(stroke.getAttribute('color')));
      el.setAttribute('stroke-opacity', alphaOf(stroke.getAttribute('color')));
      el.setAttribute('stroke-width', stroke.getAttribute('thickness'));
    }
    return el;
  };
  switch (sh.tagName) {
    case 'Arc': {
      const cx = +sh.getAttribute('centerX'), cy = +sh.getAttribute('centerY'), r = +sh.getAttribute('width') / 2;
      const a0 = +sh.getAttribute('startAngle');
      let a1 = +sh.getAttribute('endAngle');
      const te = [...sh.children].find(k => k.tagName === 'Transform' && k.getAttribute('target') === 'endAngle');
      if (te) { try { a1 = evalExpr(te.getAttribute('value')); } catch { /* keep */ } }
      const st = sh.querySelector('Stroke');
      const d = arcPath(cx, cy, r, a0, a1);
      if (!st || !d) return;
      const c = st.getAttribute('color');
      g.appendChild(mk('path', {
        d, fill: 'none', stroke: col(c), 'stroke-opacity': alphaOf(c), 'stroke-width': st.getAttribute('thickness'),
        'stroke-linecap': (st.getAttribute('cap') || 'ROUND').toLowerCase() === 'butt' ? 'butt' : 'round',
      }));
      break;
    }
    case 'Rectangle': case 'RoundRectangle':
      g.appendChild(paint(mk('rect', {
        x: sh.getAttribute('x'), y: sh.getAttribute('y'), width: sh.getAttribute('width'), height: sh.getAttribute('height'),
        rx: sh.getAttribute('cornerRadiusX') || sh.getAttribute('cornerRadius'),
        ry: sh.getAttribute('cornerRadiusY') || sh.getAttribute('cornerRadius'),
      }), sh));
      break;
    case 'Ellipse':
      g.appendChild(paint(mk('ellipse', {
        cx: +sh.getAttribute('x') + +sh.getAttribute('width') / 2, cy: +sh.getAttribute('y') + +sh.getAttribute('height') / 2,
        rx: +sh.getAttribute('width') / 2, ry: +sh.getAttribute('height') / 2,
      }), sh));
      break;
    case 'Line':
      g.appendChild(paint(mk('line', {
        x1: sh.getAttribute('startX'), y1: sh.getAttribute('startY'),
        x2: sh.getAttribute('endX'), y2: sh.getAttribute('endY'),
      }), sh));
      break;
  }
}

/* ---- the scene walk ------------------------------------------------------- */
function renderNode(n, out, ctx) {
  if (n.nodeType !== 1) return;
  switch (n.tagName) {
    case 'Variant': case 'Transform': case 'Metadata': case 'UserConfigurations':
      return;

    case 'ListConfiguration': {
      const opt = [...n.children].find(o => o.getAttribute('id') === ctx.theme) || n.children[0];
      if (opt) [...opt.children].forEach(k => renderNode(k, out, ctx));
      return;
    }
    case 'BooleanConfiguration': {
      const want = n.getAttribute('id') === 'dark' ? (ctx.dark ? 'TRUE' : 'FALSE')
        : n.getAttribute('id') === 'seconds' ? (ctx.seconds ? 'TRUE' : 'FALSE') : 'TRUE';
      const opt = [...n.children].find(o => o.getAttribute('id') === want) || n.children[0];
      if (opt) [...opt.children].forEach(k => renderNode(k, out, ctx));
      return;
    }

    case 'Group': case 'Scene': case 'Condition': case 'Default': case 'Compare': {
      if (n.getAttribute('alpha') === '0') return;                 // the AOD twin
      const a = [...n.children].find(k => k.tagName === 'Transform' && k.getAttribute('target') === 'alpha');
      if (a) { try { if (!evalExpr(a.getAttribute('value'))) return; } catch { /* draw */ } }
      const g = mk('g', { transform: `translate(${+(n.getAttribute('x') || 0)},${+(n.getAttribute('y') || 0)})` });
      [...n.children].forEach(k => renderNode(k, g, ctx));
      if (g.childNodes.length) out.appendChild(g);
      return;
    }

    case 'PartDraw': {
      const g = mk('g', { transform: `translate(${+(n.getAttribute('x') || 0)},${+(n.getAttribute('y') || 0)})` });
      [...n.children].forEach(sh => drawShape(sh, g));
      if (g.childNodes.length) out.appendChild(g);
      return;
    }

    case 'PartImage': {
      const x = +(n.getAttribute('x') || 0), y = +(n.getAttribute('y') || 0);
      const w = +n.getAttribute('width'), h = +n.getAttribute('height');
      const im = n.querySelector('Image');
      if (!im) return;
      let ang = +(n.getAttribute('angle') || 0);
      const tr = [...n.children].find(k => k.tagName === 'Transform' && k.getAttribute('target') === 'angle');
      if (tr) { try { ang = evalExpr(tr.getAttribute('value')); } catch { /* keep */ } }
      const px = x + (+(n.getAttribute('pivotX') ?? 0.5)) * w, py = y + (+(n.getAttribute('pivotY') ?? 0.5)) * h;
      const rot = ang ? `rotate(${ang.toFixed(2)} ${px.toFixed(2)} ${py.toFixed(2)})` : null;
      const res = (im.getAttribute('resource') || '').replace('@drawable/', '');
      if (!res) return;
      if (res.startsWith('[')) {
        const g = mk('g', { transform: `translate(${x},${y})` });
        g.appendChild(providerIcon(w, h, col(n.getAttribute('tintColor'))));
        if (rot) { const r2 = mk('g', { transform: rot }); r2.appendChild(g); out.appendChild(r2); } else out.appendChild(g);
      } else {
        out.appendChild(mk('image', { href: ctx.drw(res), x, y, width: w, height: h, transform: rot }));
      }
      return;
    }

    case 'PartText': {
      const x = +(n.getAttribute('x') || 0), y = +(n.getAttribute('y') || 0);
      const w = +n.getAttribute('width'), h = +n.getAttribute('height');
      const a = [...n.children].find(k => k.tagName === 'Transform' && k.getAttribute('target') === 'alpha');
      if (a) { try { if (!evalExpr(a.getAttribute('value'))) return; } catch { /* draw */ } }
      const t = n.querySelector('Text'), f = n.querySelector('Font');
      if (!t || !f) return;
      const align = (t.getAttribute('align') || 'CENTER').toUpperCase();
      const anchor = align === 'START' ? 'start' : align === 'END' ? 'end' : 'middle';
      const size = +f.getAttribute('size');
      const tx = anchor === 'start' ? x : anchor === 'end' ? x + w : x + w / 2;
      const el = mk('text', {
        x: tx, y: y + h / 2 + size * 0.36, 'font-family': fontFamily(f.getAttribute('family')),
        'font-weight': fontWeight(f.getAttribute('family')), 'font-size': size,
        fill: col(f.getAttribute('color')), 'text-anchor': anchor,
      });
      // WFF clips a PartText to its own box and, with ellipsis="TRUE", shortens the string
      // to fit. SVG <text> does neither, so without this the preview showed a long provider
      // value ("128 bpm", "Standup 09:30") sprawling clean across the dial — a picture of
      // something the watch would never draw. The real string is kept so `fitText` can
      // shorten it once it is in the DOM and can actually be measured.
      el.textContent = templateText(f);
      el.dataset.w = w;
      el.dataset.full = el.textContent;
      el.dataset.ell = (t.getAttribute('ellipsis') || '').toUpperCase() === 'TRUE' ? '1' : '0';
      out.appendChild(el);
      return;
    }

    case 'ComplicationSlot': {
      const id = n.getAttribute('slotId');
      const pol = n.querySelector('DefaultProviderPolicy');
      PROVIDER = pol ? pol.getAttribute('defaultSystemProvider') : 'EMPTY';
      const want = ctx.types[id] || (pol ? pol.getAttribute('defaultSystemProviderType') : 'EMPTY');
      const block = [...n.children].find(k => k.tagName === 'Complication' && k.getAttribute('type') === want)
                 || [...n.children].find(k => k.tagName === 'Complication' && k.getAttribute('type') === 'EMPTY');
      if (!block) { PROVIDER = ''; return; }
      // Each dial can be pointed at its own provider, so the sample is applied around
      // THIS slot only and cleared afterwards — otherwise the last dial drawn would set
      // the numbers for every dial before it.
      applySample(ctx.data ? SAMPLE_BY_ID[ctx.data[id]] : null);
      const g = mk('g', { transform: `translate(${n.getAttribute('x')},${n.getAttribute('y')})` });
      [...block.children].forEach(k => renderNode(k, g, ctx));
      if (g.childNodes.length) out.appendChild(g);
      applySample(null);
      PROVIDER = '';
      return;
    }

    case 'AnalogClock': {
      if (!ctx.hands) return;
      const g = mk('g', { transform: `translate(${+(n.getAttribute('x') || 0)},${+(n.getAttribute('y') || 0)})` });
      for (const tag of ['HourHand', 'MinuteHand', 'SecondHand']) {
        for (const h of n.querySelectorAll(tag)) {
          const res = (h.getAttribute('resource') || '').replace('@drawable/', '');
          if (!ctx.themeAsset(res)) continue;
          const x = +h.getAttribute('x'), y = +h.getAttribute('y'), w = +h.getAttribute('width'), ht = +h.getAttribute('height');
          const px = x + (+(h.getAttribute('pivotX') ?? 0.5)) * w, py = y + (+(h.getAttribute('pivotY') ?? 0.5)) * ht;
          // <Sweep> = continuous motion; a bare hand steps once per second.
          const sweep = !!h.querySelector('Sweep');
          g.appendChild(mk('image', {
            href: ctx.drw(res), x, y, width: w, height: ht,
            class: 'hand ' + tag, 'data-px': px, 'data-py': py, 'data-sweep': sweep ? 1 : 0,
          }));
        }
      }
      if (g.childNodes.length) out.appendChild(g);
      return;
    }

    default:
      [...n.children].forEach(k => renderNode(k, out, ctx));
  }
}

export function loadFace(base) {
  return fetch(base + '/raw/watchface.xml').then(r => r.text()).then(txt => {
    const doc = new DOMParser().parseFromString(txt, 'text/xml');
    const slots = [...doc.querySelectorAll('ComplicationSlot')].map(s => ({
      id: s.getAttribute('slotId'),
      name: s.getAttribute('displayName'),
      provider: s.querySelector('DefaultProviderPolicy')?.getAttribute('defaultSystemProvider') || 'EMPTY',
      defaultType: s.querySelector('DefaultProviderPolicy')?.getAttribute('defaultSystemProviderType') || 'EMPTY',
      types: (s.getAttribute('supportedTypes') || '').split(/\s+/).filter(Boolean),
    }));
    const sweep = !!doc.querySelector('SecondHand Sweep');
    return { doc, slots, base, sweep };
  });
}

export function renderFace(face, { theme = 't0', hands = true, seconds = true, types = {}, data = null, size = 450 }) {
  syncClock();
  const dark = theme === 'dark';
  SAMPLE['CONFIGURATION.dark'] = dark ? 1 : 0;
  SAMPLE['CONFIGURATION.seconds'] = seconds ? 1 : 0;
  const suffix = dark ? 'dark' : theme;
  const ctx = {
    theme: dark ? 't0' : theme, dark, seconds, hands, types, data,
    drw: (n) => `${face.base}/drawable-nodpi/${n}.png`,
    // hand sprites are emitted once per theme; keep only this theme's set
    themeAsset: (n) => (/_(t\d|dark)$/.test(n) ? n.endsWith('_' + suffix) : true),
  };
  const svg = mk('svg', { xmlns: NS, viewBox: '0 0 450 450', width: size, height: size });
  const defs = mk('defs');
  const clip = mk('clipPath', { id: 'dialclip' });
  clip.appendChild(mk('circle', { cx: 225, cy: 225, r: 225 }));
  defs.appendChild(clip); svg.appendChild(defs);
  const root = mk('g', { 'clip-path': 'url(#dialclip)' });
  svg.appendChild(root);
  const scene = face.doc.querySelector('Scene');
  if (scene) [...scene.children].forEach(k => renderNode(k, root, ctx));
  tickHands(svg);
  return svg;
}

/* Shorten every PartText to the box the XML gives it, exactly as the platform would.
   Must run AFTER the SVG is in the document — text cannot be measured before layout. */
export function fitText(scope) {
  scope.querySelectorAll('text[data-w]').forEach(el => {
    const max = +el.dataset.w;
    const full = el.dataset.full || '';
    el.textContent = full;
    if (!max || !full || el.getComputedTextLength() <= max) return;
    if (el.dataset.ell !== '1') { el.textContent = ''; return; }   // no ellipsis ⇒ hard clip
    let s = full;
    while (s.length > 1) {
      s = s.slice(0, -1);
      el.textContent = s + '…';
      if (el.getComputedTextLength() <= max) return;
    }
    el.textContent = '…';
  });
}

/* Hand angles. A hand carrying <Sweep> moves continuously (sub-second); a bare
   hand steps once per second — exactly the difference the XML declares. */
export function tickHands(scope) {
  syncClock();
  const now = new Date();
  const ms = now.getMilliseconds();
  scope.querySelectorAll('.hand').forEach(h => {
    const s = now.getSeconds() + (h.dataset.sweep === '1' ? ms / 1000 : 0);
    const a = {
      HourHand: ((now.getHours() % 12) + now.getMinutes() / 60 + s / 3600) * 30,
      MinuteHand: (now.getMinutes() + s / 60) * 6,
      SecondHand: s * 6,
    }[[...h.classList].find(c => /Hand$/.test(c))];
    h.setAttribute('transform', `rotate(${a} ${h.dataset.px} ${h.dataset.py})`);
  });
}
