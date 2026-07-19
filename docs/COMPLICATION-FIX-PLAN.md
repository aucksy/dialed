# Complication Fix Plan — execution spec for the implementing session

**Input:** `docs/COMPLICATION-AUDIT.md` (findings F1–F13, platform facts §0). Read it first, plus:
`CLAUDE.md` (ship loop, validator loop, gotchas) · `faces/collection3-tools/VAKT-COMPLICATIONS-PLAN.md` §7 (reference pattern + traps) · WFF-Design-Guidelines.md §5–§6 · both WFF skills (run polish §7 on every touched face).
**Hard rules:** Collection-3 faces are GENERATED — edit `faces/collection3-tools/spec/*` / `gen/*` and re-bake (`node gen/build-all.mjs <Face>` from `faces/collection3-tools/`); never hand-edit their XML; byte-compare untouched faces after every regen. Family A = direct XML edits. No local Android builds. Adversarial review BEFORE tagging. Git author `simpleapps108@gmail.com`. No `gh` CLI. Tag numbers are claimed at ship time (`git tag -l 'dialed-v*'` → next free), never reserved.

---

## Wave 1 — the four photographed bugs + no more black holes (one Dialed release)

### W1.1 VAKT register defaults (F1) — spec `cat-a2.js`, regen all 5 VAKT faces
Replace `EMPTY/EMPTY` defaults on the machined registers with **safe** providers styled by existing blocks. Constraints: safe-list only (audit §0.2); the chosen `defaultSystemProviderType` must be in `supportedTypes` AND have a designed block (all 8 do); **no duplicate source per face** — count the baked battery instrument every VAKT face already has (so no `WATCH_BATTERY` defaults on VAKT) and the small-slot defaults that stay.

Recommended (verify per face before applying; deviate only with a stated reason):
| Face | Slot | Now | → Default |
|---|---|---|---|
| Vakt-GT | s0 (top register) | EMPTY | STEP_COUNT / SHORT_TEXT |
| Vakt-GT | s1 (large lower) | EMPTY | UNREAD_NOTIFICATION_COUNT / SHORT_TEXT |
| Vakt-One | s0 | EMPTY | STEP_COUNT / SHORT_TEXT |
| Vakt-One | s1 (date window; spec intended "Date") | EMPTY | DATE / SHORT_TEXT |
| Vakt-Meridian | s0 | EMPTY | STEP_COUNT / SHORT_TEXT |
| Vakt-NightWatch | s0 | EMPTY | STEP_COUNT / SHORT_TEXT |
| Vakt-NightWatch | s2 (dup SUNRISE_SUNSET, F9) | SUNRISE_SUNSET | WORLD_CLOCK / SHORT_TEXT |
| Vakt-Ti | s0 | EMPTY | UNREAD_NOTIFICATION_COUNT / SHORT_TEXT (or DAY_OF_WEEK) |
| Vakt-Ti | s1 (spec's "Steps register") | EMPTY | STEP_COUNT / SHORT_TEXT |

EMPTY blocks stay exactly as they are (they now serve the user-selected-Empty state, which is their real job).

### W1.2 Aurum slot restyle + defaults (F2, part of F13) — spec/template, regen 5 Aurum faces
1. Find where the generator emits the `patch_t*` rectangle + 16-pt text (in `gen/` — likely `wff.mjs`). For the **Aurum series**, replace the alien patch with an integrated date-window: per-theme keyline frame + fill drawn from that theme's dial palette, value-only row, font ≥18 (window is 24 tall — no title row; enforce fit arithmetically: x≥0, y≥0, x+w≤slot w, y+h≤slot h). Consider growing the window to ~64×28 only if it stays inside the dial's engraved ring on all 5 faces.
2. Defaults: Aurum-Guilloche s0 `EMPTY` → `DATE/SHORT_TEXT`. Leave the other Aurum defaults (already DATE/DAY_AND_DATE/SUNRISE).
3. The `[CONFIGURATION.date]` gate (hides the slot when the Date toggle is off) is KEPT as coherent "date window" semantics — owner has a veto note in the hand-back.
4. Scope guard: do NOT restyle the other 14 patch-using faces this wave (their patches may match their dials); W2.6 sweeps them with per-face judgement.

### W1.3 Settype-Halftone digits (F3) — hand-edit `faces/Settype-Halftone/app/src/main/res/raw/watchface.xml`
Diagnose before editing: check the actual pixel dimensions of every `ht_coarse_*` / `ht_fine_*` PNG against the declared 121×170 `Character` cells (top suspect). Then fix the mismatch (correct the declared cells to the PNGs' true size and re-lay-out, or re-export the art at 121×170). While in there: make `size` integer (170), and verify `format="hh"` + `hourFormat="SYNC_TO_DEVICE"` against the XSD (drop `hourFormat` if not in the schema — verify against the cloned google/watchface XSDs, never memory). Mirror nothing in AOD (AOD uses vector outlines, already fine).

### W1.4 Settype-Counterform hour (F4) — hand-edit
Make the hero hour readable while keeping the bleed aesthetic: render the hour **unpadded** (verify the unpadded 12-h source name in the XSD; if none exists, gate twin PartTexts on hour<10 to drop the pad zero) and/or shift/scale the `hh` group so at minimum the full trailing digit + half the leading digit are on-canvas at every hour (arithmetic check at 480 canvas with `clipShape="CIRCLE"`). Apply identical geometry to the AOD twins (`hh_aod`). Keep the minute-outline collision — it's the design.

### Wave-1 verification & ship
1. fablecollection: regen targets → `git -C faces status` + byte-compare every face you did NOT target (any diff = generator bleed → stop). Run the WFF validator (CI wff-validator gate; locally per CLAUDE.md's portable-JRE17 + `validator-push-cli-1.0.0-alpha09.jar` loop against CI-built face APKs). Polish-skill §7 pass on all 12 touched faces. Commit + push fablecollection main.
2. Dialed: bump `faces/` submodule → `node tools/gen-facepacks.mjs "D:\Apps\WearOS Apps\WatchFaces\Dialed App"` (absolute root — the space breaks the default) → bump versionCode/versionName in BOTH `app/` and `wear/` → push main first (free compile+validate gate) → **adversarial logic review** → claim next free `dialed-v*` tag → push the tag explicitly → poll Actions via API + `git credential fill` (never print the token) → paste BOTH direct APK links.
3. Hand the owner the Wave-1 on-wrist checklist (template §below) and STOP for on-wrist results before Wave 2.
4. After the owner confirms VAKT registers now fill on first install: fold audit §2 lessons L1–L4 into the WFF skill's runtime-gotchas AND WFF-Design-Guidelines.md (skill edit = extract/edit/Python-zipfile-repack; keep it organised per the maintenance contract).

## Wave 2 — registers behave like instruments + fleet hygiene (second release)

### W2.1 Meridian-PetiteSeconde (F5) — RECOMMENDED: de-slot the register
Remove ComplicationSlot 0 entirely (generator/spec change): the ticking small-seconds IS the face's signature and already lives in the scene; a data slot there can only cover it with foreign content (and the platform gives no way to show EMPTY art on first install — audit §0.1). Keep s1 (date). ALTERNATIVE (owner call): full VAKT-ification (art into EMPTY block + safe default + instrument-styled RV) — costs the small-seconds look out of the box. Do not ship the disc patch under any option.

### W2.2 Terra-Altimeter (F6) — RECOMMENDED: de-slot both registers
The baked needles ARE live steps/battery instruments (better than any provider could render). Remove slots 0+1, keep s2 (date). Kill the disc patches with them. ALTERNATIVE: VAKT-ify (same trade-offs as W2.1; note a provider-driven RV can't replicate the mod-10k steps needle).

### W2.3 Terra-Compass s1 (F7) — inspect on the machined register; de-slot or restyle to register-style content + safe default; same decision rule.

### W2.4 Gauge hardening (F10)
- Generator RV-arc template: emit `clamp(fraction, 0, 1) * sweep` with a guarded denominator (`(MAX - MIN > 0 ? MAX - MIN : 1)` — WFF has no max()); regen affected faces (Halo-Orbit at minimum; any register slots that survive W2.1–2.3).
- Vespera-Aurum (hand-edit): guard both RV denominator and GP target (VAKT pattern), add the GOAL_PROGRESS overflow lap gated on `VALUE > TARGET` (fall its colour back to `muted` in dark mode — role-collapse trap), and swap s2's default `HEART_RATE` → `WATCH_BATTERY / RANGED_VALUE` (no baked battery on this face — verify first).
- Halo-Orbit: default s0 (outer ring) → `WATCH_BATTERY / RANGED_VALUE` (the only safe RV source); s1 stays user-assigned (v1 face — steps can't safely drive RV; note for a possible future v2 bump + GOAL_PROGRESS block).

### W2.5 Fleet defaults + duplicates + EMPTY compliance (F8, F9, F11)
- Swap `HEART_RATE` defaults → safe (suggest WATCH_BATTERY/RV where the slot has a gauge, else STEP_COUNT or DAY_OF_WEEK): Aether-Ember s1, Kinetik-Escapement s1, Kinetik-Metronome s1, Vespera-Meteorite s1 (Vespera-Aurum handled in W2.4). Owner may veto per face (HR is attractive *after* permission).
- Swap `NEXT_EVENT` defaults → safe on every face where it's the face's only/primary slot (Meridian-Calendrier s0 → DAY_AND_DATE/ST at minimum); judge the rest per face (list in audit F8).
- Break the SUNRISE_SUNSET duplicate pairs (F9): change one of each pair (Aether-Horizon s3, Terra-Field24 s2, Terra-MeridianLine s1, Terra-Solstice s1) → WORLD_CLOCK or WATCH_BATTERY per dial logic. If the owner's Wave-1 test shows SUNRISE_SUNSET itself blank on install, swap the remaining singles too.
- Add self-closing `<Complication type="EMPTY" />` to Arclight-Pulsar (4 slots) + Arclight-Solstice (5) (Family A hand-edit, visual no-op); generator: emit an EMPTY block for every generated slot (art optional; presence mandatory).
- Meridian-Classic s0+s1 `EMPTY/EMPTY` → DATE + DAY_OF_WEEK (or per dial), so the face isn't dataless out of the box; Halo-Quadrant s0 → judge (its ST-only 116×116 panel).

### W2.6 Patch-template sweep (rest of F2/F13)
Preview all 14 remaining patch-using faces (regen previews); restyle only where the patch visibly clashes or text <18 hurts; raise the template's font floor to 18–20 where the row height allows (fit check).

### Wave-2 ship = same mechanics as Wave 1. Then fold any NEW on-wrist lessons into skill + guidelines.

## Wave 3 (optional, owner-gated) — z-order scene split (F12)
Generator-wide split for the 20 non-VAKT C3 faces (interactive under-layers → slots → hands-on-top → AOD; both halves get their own AMBIENT variants — VAKT's generated structure is the in-repo reference) + hand-reorder Kinetik-Orrery. Big regen blast radius: full-fleet validation + byte-compare + on-watch spot check. Keep as its own release.

---

## Owner decision points (ask before the relevant wave; defaults in bold)
1. PetiteSeconde / Altimeter / Compass registers: **pure instruments (de-slot)** vs assignable-with-trade-offs (W2.1–2.3).
2. Aurum Date-toggle: **toggle hides the window (incl. assigned data)** vs assigned data ignores the toggle.
3. HEART_RATE defaults: **swap to safe** vs keep (blank until permission).
4. Counterform: **tame the crop (unpadded hour + reposition)** vs leave as-designed.

## On-wrist checklist template (emit per release with the APK links)
- **Every VAKT face, fresh install:** no black holes — every register shows live data immediately; assign heart rate to a big register → instrument styling, no flat disc, hands pass OVER the content; pick "Empty" on a register → machined plate + ticking needle appears; date panel unchanged.
- **Aurum-Guilloche:** date window looks part of the dial (no floating box), shows the date on install; assign steps → still integrated; Date-toggle off → window hides (expected).
- **Halftone:** digits fully-formed dot patterns, readable at e.g. 07:08 and 12:34.
- **Counterform:** hour readable at 1-digit hours (07:xx) and 2-digit (12:xx); bleed still stylish; small time + stacked readouts intact.
- **Sunrise/sunset windows (Terra trio, NightWatch, Horizon, Opaline, Orrery, Soir):** report any window still blank on fresh install → we swap that provider in the next wave.
- **After Wave 2:** PetiteSeconde/Altimeter registers tick/point as instruments, never a grey disc; Halo-Orbit outer ring shows battery on install and never over-wraps; Vespera-Aurum right dial shows battery gauge on install; goal overshoot draws the overflow lap.

## Kickoff prompt for the implementing session (copy verbatim)
> Execute Wave 1 of `docs/COMPLICATION-FIX-PLAN.md` in D:\Apps\WearOS Apps\WatchFaces\Dialed App (read `docs/COMPLICATION-AUDIT.md` first, then the plan, then CLAUDE.md + faces/collection3-tools/VAKT-COMPLICATIONS-PLAN.md §7 + both WFF skills). Collection-3 faces are generated — spec/generator edits only, byte-compare untouched faces after regen. Family A = direct XML. Validate every changed face with the WFP validator before shipping; polish-skill §7 pass per face; adversarial review BEFORE tagging; claim the next free dialed-v* tag at ship time; push fablecollection first, then Dialed submodule bump + gen-facepacks (absolute root path) + version bump in app/ AND wear/. End with both direct APK links + the Wave-1 on-wrist checklist, in plain English. Wave 2 only after the owner reports on-wrist results.
