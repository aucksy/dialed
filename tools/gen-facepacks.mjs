// Dialed — facepack + catalog generator.
//
// Reads the fablecollection faces from the `faces/` submodule and, for each,
//   1. copies its preview.png into :app  (so the storefront is self-contained), and
//   2. generates a thin Gradle module under facepacks/<key>/ that REUSES the face's
//      WFF resources + manifest straight from the submodule and only overrides the
//      applicationId to  com.dialed.app.watchfacepush.<series>.<face>  (the WFP rule).
// Finally it emits app/.../catalog/FaceCatalog.kt.
//
// One source of truth: face XML never leaves fablecollection. Re-run any time the
// submodule changes (or via scripts/add_face.mjs). Idempotent.
//
// Usage:  node tools/gen-facepacks.mjs [projectRoot]

import fs from 'node:fs';
import path from 'node:path';

const APP_ID = 'com.dialed.app';
const ROOT = path.resolve(process.argv[2] || path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..'));
const FACES_DIR = path.join(ROOT, 'faces');
const FACEPACKS_DIR = path.join(ROOT, 'facepacks');
const APP_DRAWABLE = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi');
const CATALOG_FILE = path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'dialed', 'app', 'catalog', 'FaceCatalog.kt');

// The faces Dialed bundles, by submodule dir name. Enumerating every Series-Face dir in the
// submodule would silently pull in whatever lands there next, so this list stays authoritative —
// add a dir here (and re-run) to bundle a new face.
//
// v0.21.0 (plan Phase 2C): 18 -> 43. The 25 "Collection 3" faces (Aurum/Halo/Meridian/Terra/Vakt)
// were built long ago and simply never bundled. They are WFF v1 @ 450 canvas with 5 baked themes;
// the older 18 are v2 @ 480 with a colour swatch. Both families ship as-is — see docs/CATALOG-AUDIT.md.
const BUNDLED_FACES = new Set([
  // Family A — the original Fable line (WFF v2 @ 480, colour swatch).
  'Aether-Ember', 'Aether-Horizon',
  'Arclight-Pulsar', 'Arclight-Solstice',
  'Kinetik-Escapement', 'Kinetik-Metronome', 'Kinetik-Odometer', 'Kinetik-Orrery', 'Kinetik-Turbine',
  'Settype-Counterform', 'Settype-Halftone', 'Settype-Marquee', 'Settype-Masthead',
  'Vespera-Aurum', 'Vespera-Meteorite', 'Vespera-Noir', 'Vespera-Opaline', 'Vespera-Salon',
  // Family B — Collection 3 (WFF v1 @ 450, 5 baked themes each). Added in v0.21.0.
  'Aurum-Baguette', 'Aurum-Eclat', 'Aurum-Guilloche', 'Aurum-Soir', 'Aurum-Squelette',
  'Halo-Beacon', 'Halo-Ledger', 'Halo-Orbit', 'Halo-Quadrant', 'Halo-Stack',
  'Meridian-Calendrier', 'Meridian-Classic', 'Meridian-PetiteSeconde', 'Meridian-Roman', 'Meridian-Sector',
  'Terra-Altimeter', 'Terra-Compass', 'Terra-Field24', 'Terra-MeridianLine', 'Terra-Solstice',
  'Vakt-GT', 'Vakt-Meridian', 'Vakt-NightWatch', 'Vakt-One', 'Vakt-Ti',
]);

// Per-series presentation metadata. `blurb` is the fallback for faces with no FACE_META entry;
// `features` is NOT here any more — chips are derived from each face's real ComplicationSlots
// (see deriveFeatures). The old series-level guesses were actively wrong: "Aether = Weather" when
// no Aether face has a weather provider, "Kinetik = Steps" when Kinetik-Orrery has none.
const SERIES_META = {
  Arclight: { tag: 'Arclight · Solar', style: 'Arclight',
    blurb: 'A radiant analog dial with a luminous sweep and a solar-gold hand.' },
  Kinetik: { tag: 'Kinetik · Mechanical', style: 'Kinetik',
    blurb: 'A kinetic mechanism in motion — orreries, escapements and turbines rendered in metal.' },
  Aether: { tag: 'Aether · Atmospheric', style: 'Aether',
    blurb: 'Soft atmospheric gradients and a horizon that tracks the day.' },
  Settype: { tag: 'Settype · Typographic', style: 'Settype',
    blurb: 'Type as time — editorial letterforms and a precise typographic grid.' },
  Vespera: { tag: 'Vespera · Evening', style: 'Vespera',
    blurb: 'An elegant evening dial in gold and shadow, dressed for the hour.' },
  // Collection 3 (added v0.21.0). Series framing per docs/CATALOG-AUDIT.md §3.
  Vakt: { tag: 'Vakt · Instrument', style: 'Vakt',
    blurb: 'A machined tool-watch instrument — registers, lume and honest data.' },
  Meridian: { tag: 'Meridian · Dress', style: 'Meridian',
    blurb: 'Bauhaus restraint — the dial that makes the smartwatch disappear.' },
  Terra: { tag: 'Terra · Field', style: 'Terra',
    blurb: 'A field and expedition instrument for the city and the hill.' },
  Halo: { tag: 'Halo · Data', style: 'Halo',
    blurb: 'Data first — rings, gauges and a day you can read at a glance.' },
  Aurum: { tag: 'Aurum · Haute horlogerie', style: 'Aurum',
    blurb: 'Openwork haute-horlogerie — guilloché, skeleton and jewellery light.' },
};

// Per-face display name + blurb.
//
// Source of truth: the Collection-3 handoff spec (`faces/collection3-tools/spec/cat-*.js`), whose
// `name` / `tagline` fields are what the designs were signed off as. Folder names are ASCII and
// concatenated (Aurum-Eclat, Terra-MeridianLine), so without this the storefront would render
// "Eclat" and "MeridianLine". These are display-only — the package id still comes from the folder
// name, so nothing here re-mints a WFP token or forces a re-install.
//
// ⚠ The 2A audit proposed renaming Terra-Solstice -> "Daylight" and Terra-MeridianLine ->
// "Longitude" to dodge name clashes. Both are SKIPPED: the silkscreen check (audit §6/§11 q6)
// found "TERRA SOLSTICE" and "TERRA / MERIDIAN LINE" painted into the dial art of every theme, so
// renaming them in the storefront would contradict the watch on the wrist. Revisit only if the art
// is ever re-baked from spec.
const FACE_META = {
  'Vakt-One': { name: 'One', blurb: 'The instrument, undiluted.' },
  'Vakt-GT': { name: 'GT', blurb: 'The chronograph goes grand touring.' },
  'Vakt-Meridian': { name: 'Meridian', blurb: 'The tool watch, tailored.' },
  'Vakt-Ti': { name: 'Ti', blurb: 'The instrument, exhaled.' },
  'Vakt-NightWatch': { name: 'Night Watch', blurb: 'Built for the blackout.' },
  'Meridian-Classic': { name: 'Classic', blurb: 'The essential dial.' },
  'Meridian-Sector': { name: 'Sector', blurb: 'The draughtsman’s dial.' },
  'Meridian-PetiteSeconde': { name: 'Petite Seconde', blurb: 'Seconds, set aside.' },
  'Meridian-Calendrier': { name: 'Calendrier', blurb: 'The week at a glance.' },
  'Meridian-Roman': { name: 'Roman', blurb: 'Grand feu, quietly modern.' },
  'Terra-Field24': { name: 'Field 24', blurb: 'The pattern-room field watch.' },
  'Terra-Solstice': { name: 'Solstice', blurb: 'The day, drawn as an arc.' },
  'Terra-Compass': { name: 'Compass', blurb: 'Bearings for the city and the hill.' },
  'Terra-Altimeter': { name: 'Altimeter', blurb: 'Instrument-panel drama, honest data.' },
  'Terra-MeridianLine': { name: 'Meridian Line', blurb: 'A sundial you can trust indoors.' },
  'Halo-Stack': { name: 'Stack', blurb: 'Hours over minutes, like a masthead.' },
  'Halo-Beacon': { name: 'Beacon', blurb: 'One line of time.' },
  'Halo-Quadrant': { name: 'Quadrant', blurb: 'A broadsheet grid for the wrist.' },
  'Halo-Orbit': { name: 'Orbit', blurb: 'Time in the middle of its data.' },
  'Halo-Ledger': { name: 'Ledger', blurb: 'The day, in five rows.' },
  'Aurum-Guilloche': { name: 'Guilloché', blurb: 'Engine-turning, reborn.' },
  'Aurum-Squelette': { name: 'Squelette', blurb: 'The movement, imagined.' },
  'Aurum-Soir': { name: 'Soir', blurb: 'The hour of aperitifs.' },
  'Aurum-Baguette': { name: 'Baguette', blurb: 'Twelve stones of light.' },
  'Aurum-Eclat': { name: 'Éclat', blurb: 'One ray of morning.' },
};

// A slot's default provider -> the chip a buyer understands. Keys are the WFF
// `defaultSystemProvider` values actually present across the 43 faces; anything unmapped is
// surfaced loudly at generate time rather than silently dropped.
const PROVIDER_CHIPS = {
  STEP_COUNT: 'Steps',
  WATCH_BATTERY: 'Battery',
  HEART_RATE: 'Heart rate',
  SUNRISE_SUNSET: 'Sunrise · sunset',
  NEXT_EVENT: 'Calendar',
  DAY_AND_DATE: 'Day & date',
  DATE: 'Date',
  UNREAD_NOTIFICATION_COUNT: 'Notifications',
  WORLD_CLOCK: 'World clock',   // F9 fix: duplicate sunset cells now default to a second time zone
  EMPTY: null,   // a deliberately-empty slot: real, but it names no data of its own.
};

/**
 * M2 fix (plan Phase 2C step 3): derive the detail-screen chips from the face's REAL
 * <ComplicationSlot>s instead of a series-level guess.
 *
 * Chips = each slot's default provider (deduped, in slot order) + a slot count + Always-on.
 * A slot whose default is EMPTY still counts toward "N complications" (the user can fill it) but
 * contributes no data chip, because nothing truthful can be said about what it will hold.
 */
function deriveFeatures(faceDir, dir) {
  const xml = path.join(faceDir, 'app', 'src', 'main', 'res', 'raw', 'watchface.xml');
  if (!fs.existsSync(xml)) return [];
  const src = fs.readFileSync(xml, 'utf8');

  const slots = src.match(/<ComplicationSlot\b[\s\S]*?<\/ComplicationSlot>/g) || [];
  const chips = [];
  for (const slot of slots) {
    const provider = (slot.match(/defaultSystemProvider="([A-Z_]+)"/) || [])[1];
    if (!provider) continue;
    if (!(provider in PROVIDER_CHIPS)) {
      // Hard fail, not a warning: a silently-dropped chip is exactly the "the store lies about the
      // face" bug M2 exists to kill, and it would sail through CI green.
      console.error(`!! ${dir}: unmapped defaultSystemProvider "${provider}" — add it to PROVIDER_CHIPS`);
      process.exit(1);
    }
    const chip = PROVIDER_CHIPS[provider];
    if (chip && !chips.includes(chip)) chips.push(chip);
  }

  if (slots.length) chips.push(slots.length === 1 ? '1 complication' : `${slots.length} complications`);
  // True of all 43 today, so this chip discriminates nothing — it is kept because it is a real
  // property a buyer asks about, and it is tested rather than hardcoded so it would correctly
  // disappear if a face ever shipped without an ambient treatment. Not a differentiator; don't
  // mistake it for one.
  if (/mode="AMBIENT"/.test(src)) chips.push('Always-on');
  return chips;
}

/**
 * `displayName` is what the WATCH's own face-customisation editor shows for a setting, a theme
 * option and a complication slot. It must be a @string reference — a bare name renders as the
 * literal key, so the user sees "theme_label" where "Colour theme" belongs.
 *
 * Family A (the original 18, proven on-wrist) already writes `@string/cfg_theme`. Every Collection-3
 * face writes the BARE name (`displayName="theme_label"`) while defining the proper human strings in
 * values/strings.xml — so bundling them unpatched would ship raw resource keys into the editor on all
 * 25. Same class as the polish skill's hardcoded-label bug, and the same remedy as the `@drawable/`
 * strip above: patch the facepack's COPY, never the submodule.
 *
 * Rewrites only names this face actually defines as a string, so a miss can never invent a dangling
 * @string/ ref (which would fail the build); anything already `@`-prefixed is left alone.
 */
function patchDisplayNames(xml, dstRes, dir) {
  const stringsFile = path.join(dstRes, 'values', 'strings.xml');
  if (!fs.existsSync(stringsFile)) return xml;
  const defined = new Set(
    [...fs.readFileSync(stringsFile, 'utf8').matchAll(/<string\s+name="([^"]+)"/g)].map((m) => m[1]),
  );
  const unresolved = new Set();
  const out = xml.replace(/displayName\s*=\s*"([^"@][^"]*)"/g, (whole, name) => {
    if (defined.has(name)) return `displayName="@string/${name}"`;
    unresolved.add(name);
    return whole;
  });
  if (unresolved.size) {
    console.warn(`  !! ${dir}: displayName(s) with no matching <string>: ${[...unresolved].join(', ')} — left as literals`);
  }
  return out;
}

function findPreview(faceDir) {
  for (const sub of ['drawable-nodpi', 'drawable']) {
    const p = path.join(faceDir, 'app', 'src', 'main', 'res', sub, 'preview.png');
    if (fs.existsSync(p)) return p;
  }
  return null;
}

// Escape for a Kotlin "..." literal. `$` matters: Kotlin interpolates it, so an unescaped
// `$` in any name/blurb would be a compile error rather than a visible typo.
function kesc(s) { return s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$'); }

function facepackGradle(dir, series, face, pkg, key) {
  const manifest = `\$rootDir/faces/${dir}/app/src/main/AndroidManifest.xml`;
  return `// GENERATED by tools/gen-facepacks.mjs — do not edit by hand.
// Resource-only WFP face packaging for ${series} ${face}. WFF resources are copied from
// the fablecollection submodule into ./wff-res with watchface.xml patched to bare resource
// names (the memory-footprint validator can't resolve the non-canonical @drawable/ prefix).
// Only the applicationId changes vs the original face.
plugins { id("com.android.application") }

android {
    namespace = "${pkg}"
    compileSdk = 36

    defaultConfig {
        applicationId = "${pkg}"     // WFP rule: <marketplaceAppId>.watchfacepush.<name>
        minSdk = 33
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    // res = patched copy (bare resource names); manifest reused from the submodule.
    sourceSets["main"].res.setSrcDirs(listOf("\$projectDir/wff-res"))
    sourceSets["main"].manifest.srcFile("${manifest}")

    // Shared throwaway faces key — MUST differ from the :app signing key (WFP requirement).
    signingConfigs {
        create("faces") {
            storeFile = file("\$rootDir/dialed-faces.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    // AGP 9's built-in Kotlin bundles kotlin-stdlib metadata (kotlin/*.kotlin_builtins) into
    // the APK; WFP's strict entry allowlist (AndroidManifest.xml / resources.arsc / res/** /
    // META-INF/** only) rejects the kotlin/ dir. hasCode=false means it's never used — drop it.
    packaging {
        resources {
            excludes += listOf(
                "kotlin/**",
                "**/*.kotlin_builtins",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "DebugProbesKt.bin",
            )
        }
    }

    buildTypes {
        // minify=true strips the (empty) classes.dex that WFP's file allowlist forbids.
        // It shortens res FILE PATHS (res/0I.png) but KEEPS resource NAMES in the arsc, and
        // the validator resolves drawables by name — so this is safe. Mirrors Google's sample.
        release {
            signingConfig = signingConfigs.getByName("faces")
            isMinifyEnabled = true
            isShrinkResources = false
        }
        debug {
            signingConfig = signingConfigs.getByName("faces")
            isMinifyEnabled = true
        }
    }
}
`;
}

function main() {
  if (!fs.existsSync(FACES_DIR)) {
    console.error(`faces/ submodule not found at ${FACES_DIR}. Run: git submodule update --init`);
    process.exit(1);
  }

  const dirs = fs.readdirSync(FACES_DIR)
    .filter((d) => BUNDLED_FACES.has(d))
    .filter((d) => fs.existsSync(path.join(FACES_DIR, d, 'app', 'src', 'main', 'AndroidManifest.xml')))
    .sort();
  // Hard fail. A warning here is invisible: it scrolls past in a wall of ✓ lines, the generator still
  // exits 0, and every downstream gate is self-consistent (CI regenerates the SAME short output, so the
  // drift check is green and the count gate compares facepacks/ against itself). A submodule bump that
  // renames one dir would silently ship a smaller store, green all the way.
  const missing = [...BUNDLED_FACES].filter((d) => !dirs.includes(d));
  if (missing.length) {
    console.error(`!! bundled faces missing from the submodule: ${missing.join(', ')}`);
    console.error('   Either the submodule pin is wrong, or BUNDLED_FACES needs updating.');
    process.exit(1);
  }

  // Clean prior output (idempotent).
  fs.rmSync(FACEPACKS_DIR, { recursive: true, force: true });
  fs.mkdirSync(FACEPACKS_DIR, { recursive: true });
  fs.mkdirSync(APP_DRAWABLE, { recursive: true });
  for (const f of fs.readdirSync(APP_DRAWABLE)) {
    if (f.startsWith('preview_')) fs.rmSync(path.join(APP_DRAWABLE, f));
  }

  const faces = [];
  for (const dir of dirs) {
    const [series, face] = dir.split('-');
    const meta = SERIES_META[series] || { tag: series, style: series, blurb: '' };
    const faceMeta = FACE_META[dir] || {};
    const displayName = faceMeta.name || face;
    const blurb = faceMeta.blurb || meta.blurb;
    const key = `${series}_${face}`.toLowerCase();
    const pkg = `${APP_ID}.watchfacepush.${series.toLowerCase()}.${face.toLowerCase()}`;
    const features = deriveFeatures(path.join(FACES_DIR, dir), dir);

    // Fail rather than skip: a face silently dropped for a missing preview still leaves every other
    // gate self-consistent, so it would ship a smaller store with a green build.
    const preview = findPreview(path.join(FACES_DIR, dir));
    if (!preview) { console.error(`!! no preview.png for ${dir} — cannot bundle it`); process.exit(1); }
    fs.copyFileSync(preview, path.join(APP_DRAWABLE, `preview_${key}.png`));

    const packDir = path.join(FACEPACKS_DIR, key);
    fs.mkdirSync(packDir, { recursive: true });
    fs.writeFileSync(path.join(packDir, 'build.gradle.kts'), facepackGradle(dir, series, face, pkg, key));

    // Copy the face's WFF res into the facepack and patch watchface.xml to BARE resource
    // names. WFP's memory-footprint validator looks up drawables by the literal `resource`
    // attribute; the faces' non-canonical `@drawable/<name>` prefix (runtime-tolerated) makes
    // it report every referenced asset as "not found". Stripping the prefix -> canonical WFF.
    const srcRes = path.join(FACES_DIR, dir, 'app', 'src', 'main', 'res');
    const dstRes = path.join(packDir, 'wff-res');
    fs.rmSync(dstRes, { recursive: true, force: true });
    fs.cpSync(srcRes, dstRes, { recursive: true });
    const wfXml = path.join(dstRes, 'raw', 'watchface.xml');
    if (fs.existsSync(wfXml)) {
      let patched = fs.readFileSync(wfXml, 'utf8').replace(/(resource\s*=\s*")@drawable\//g, '$1');
      patched = patchDisplayNames(patched, dstRes, dir);
      fs.writeFileSync(wfXml, patched);
    }

    faces.push({ key, series, face, displayName, tag: meta.tag, blurb, features, style: meta.style, pkg });
    console.log(`  ✓ ${dir}  ->  facepacks/${key}  (${pkg})  [${features.join(', ')}]`);
  }

  // Emit FaceCatalog.kt
  const entries = faces.map((f) => `        Face(
            id = "${f.key}",
            series = "${f.series}",
            faceName = "${kesc(f.face)}",
            displayName = "${kesc(f.displayName)}",
            tag = "${kesc(f.tag)}",
            description = "${kesc(f.blurb)}",
            previewRes = R.drawable.preview_${f.key},
            packageName = "${f.pkg}",
            apkAsset = "faces/${f.key}.apk",
            tokenAsset = "tokens/${f.key}.token",
            features = listOf(${f.features.map((x) => `"${kesc(x)}"`).join(', ')}),
            styleTags = listOf("${kesc(f.style)}"),
        ),`).join('\n');

  const catalog = `// GENERATED by tools/gen-facepacks.mjs — do not edit by hand.
package com.dialed.app.catalog

import com.dialed.app.R

/** The bundled Dialed collection (${faces.length} faces). Regenerate after adding a face. */
object FaceCatalog {
    val faces: List<Face> = listOf(
${entries}
    )
}
`;
  fs.mkdirSync(path.dirname(CATALOG_FILE), { recursive: true });
  fs.writeFileSync(CATALOG_FILE, catalog);

  // The one non-self-referential assertion in the pipeline. Everything downstream (CI's drift check,
  // its count gate) compares generated output against generated output, so this is the only place
  // that can catch "we emitted fewer faces than we meant to".
  if (faces.length !== BUNDLED_FACES.size) {
    console.error(`\n!! emitted ${faces.length} faces but BUNDLED_FACES lists ${BUNDLED_FACES.size}`);
    process.exit(1);
  }
  console.log(`\nGenerated ${faces.length} facepacks + previews + FaceCatalog.kt`);
}

main();
