# Complication-Fix Program — live PROGRESS log

---

## ✅ PROGRAM EXECUTED END-TO-END (2026-07-22, owner-authorized ship)

**All three waves ran on all 43 faces and BOTH apps shipped** (fablecollection `8736d4a`;
Dialed release tagged the same day — see CLAUDE.md ship log for the tag). Everything that was
"uncommitted on disk" is now committed and pushed: the Vakt-GT re-bake + generator tools went
to fablecollection main (`9e62b0c`), the onboarding redesign + face-review harness + these docs
went to Dialed main (`48f8b43`), and the wave work followed (`8736d4a`).
**▶ NEXT = the PER-WATCH-FACE REFINEMENT program** (owner reviews each face on the interactive
harness, one at a time) — kickoff prompt at the bottom of this file.

### What each wave shipped (2026-07-22)

**Wave 1**
- **VAKT One / Meridian / Ti / NightWatch = the settled GT instrument design** (register map
  followed exactly): plate + tick sets + engraved numerals bake as permanent hardware; the three
  chronos are `bare` slots (`RANGED_VALUE GOAL_PROGRESS EMPTY`) driving only the design's own
  needle/arc/icon; defaults HR→`HEART_RATE/RANGED_VALUE`, battery→`WATCH_BATTERY/RANGED_VALUE`,
  steps→`STEP_COUNT/RANGED_VALUE`; `rvFracFor` reads the engraved numerals. Date windows →
  **locked native** (design's frame + dnum [+ day3], Date-toggle gated, never blank; GT keeps its
  panel slot as settled). Kept: One's unread chip, Meridian's event line, NightWatch's
  sunrise/sunset (sunset default → WORLD_CLOCK, F9). **Ti's battery stays the baked flange arc**
  (already a live native instrument; Ti has 2 chrono dials).
  Gates: cleared-dial at the noise floor on all four (One 0.06/0.08 · Meridian 0.19/0.12 with the
  event-line region explained · Ti 0.04/0.07 · NightWatch 0.24/0.19 with the date-region ink flag
  measured as metric noise); fit-check clean everywhere; GT regression clean (0.11/0.10, known SUN
  flag only); dark + t1 reviewed. First-install needle positions ≠ design = the requested change.
- **Aurum ×5**: patch → integrated recessed window (theme fill + keyline, value-only ≥18);
  Guilloché EMPTY → DATE. Date-toggle still hides the window (owner default #2).
- **Halftone**: bitmap-font cells declared at the PNGs' TRUE 150×212 (were 121×170 → the renderer
  cropped digits to fragments). `size` → integer 170.
- **Counterform**: hour UNPADDED (owner default #4) via one-gate-per-node nested groups
  (12/24h × single/double); single digits shift +22 fully on-canvas; AOD twins identical.

**Wave 2**
- **De-slotted pure instruments (owner default #1):** PetiteSeconde seconds register,
  Altimeter both registers, Compass north gauge. Gates: PetiteSeconde 0.01%, Altimeter 0.10%
  (placard flag = harness sample "19" vs design "SUN 19"), Compass 0.12% — all at/below floor.
- **Gauge hardening (F10):** every generated RV expression now clamps 0..1 with a guarded
  denominator (incl. `RV_FRAC` used by VAKT); Vespera-Aurum hand-hardened + gained the
  GOAL_PROGRESS overflow lap (metal.1, gated `VALUE > TARGET`), s2 HR → WATCH_BATTERY/RV.
- **HR defaults → safe (owner default #3):** Aether-Ember, Kinetik-Escapement, Kinetik-Metronome,
  Vespera-Meteorite s1 → WATCH_BATTERY/RANGED_VALUE (verified: none had battery anywhere).
- **F9 duplicate sunset cells → WORLD_CLOCK:** Field24, Solstice, MeridianLine, Aether-Horizon,
  NightWatch. ⚠ CAVEAT logged: Field24 + Solstice have BAKED moon glyphs beside the swapped cell
  (a world-clock time now sits next to a moon) — per-face-refinement item; NightWatch's moon is
  slot content, so its swap is clean.
- **Other defaults:** Meridian-Classic date → DATE. **Judged, with reasons:** Calendrier KEEPS
  NEXT_EVENT (the engraved 'NEXT' is baked art and its calendar row is already native);
  Halo-Orbit KEEPS EMPTY defaults (XML-verified: its rings are live native battery/steps
  instruments out of the box — the audit's "rings empty" premise was wrong); Halo-Quadrant keeps
  native tokens; secondary NEXT_EVENT slots kept fleet-wide (every such face has live data).
- **EMPTY compliance (F11):** every generated slot + Arclight-Pulsar/Solstice now declare
  `<Complication type="EMPTY" />`.
- **Legibility (F13/W2.6):** value-text floor 18 wherever the row is ≥25 tall (patch boxes sized
  to match). Patch sweep of the remaining 12 faces: no Aurum-class clashes (patches blend on the
  paper dials) — no further restyles.

**Wave 3 (F12):** every face's scene now splits dial → slots → hands-on-top (the VAKT structure,
fleet-wide via the generator); Kinetik-Orrery's planet clock hand-moved after its slots.
Full-fleet regen: **zero dial-art drift** outside the nine intentionally-rebaked faces; all 25
generated + all touched Family-A faces validator PASS.

### Harness fixes made this session (they changed the numbers)
- ⭐ **`frame.html` now loads the faces' REAL TTFs for BOTH sides** (`/fonts` mount) + waits on
  `document.fonts`. Before this the design side fell back to Arial and the build side to Times —
  the gate was comparing two fallbacks (Vakt-One whole-dial went 0.13% → 0.06% on the same build).
  `current.js` font stacks gained a `sans-serif` fallback.
- `dateLockText` renders at the design's own size (no MIN_LIVE_TEXT floor) — same class as the
  adaptive-scale size fix.
- `scaffold.mjs` emits the committed manifest comment (kills the recurring cosmetic drift).

### Still open for the wrist (unchanged facts)
- `HEART_RATE/RANGED_VALUE` defaults on all 5 VAKT top dials: an OEM that serves text/shortcut
  leaves that dial blank until the wearer assigns a ranged pulse (fallback = one-line spec change
  per face: drop the slot, restore the seconds sub-hand).
- All `[COMPLICATION.*]` bindings and the Counterform `< 10` gates are validator-unverifiable.
- GT's date window still shows "19" without "SUN" (−37% ink) — owner call pending (the other four
  VAKT faces now show day3 natively where their design has one).
- Unread chip renders its count at 16 (design 12.5) — the deliberate MIN_SLOT_TEXT legibility
  floor on provider content; flag for per-face refinement if the owner minds.

---

## ⏸ superseded header (2026-07-20) — kept for history

**VAKT GT was DONE and parked; the rest below is the running record that led here.**

### What is DONE (VAKT GT only — WF-A2)
Settled design: three chronos = **battery · steps · heart rate**, each a pure instrument. A slot may
move only the **needle**, the **arc**, and the **icon**. No text, ever. `supportedTypes` trimmed to
`RANGED_VALUE GOAL_PROGRESS EMPTY` so the watch's editor cannot offer anything else. Gate:
**0.26% cleared / 0.27% first-install vs a 0.23% floor**, fit-check clean, WFF v2 PASS.

### What is PENDING
1. **⚠ On-wrist test of GT before anything else** — nothing here has touched a watch. Two items only
   a wrist can settle: (a) does `HEART_RATE` return a *ranged* value on the owner's watch, or text /
   an app shortcut (then the top dial is blank until the wearer assigns one — fallback is to drop
   that slot and restore the seconds sub-hand, a one-line spec change); (b) every `[COMPLICATION.*]`
   name is validator-unverifiable, so only the wrist proves the needles actually move.
2. **The other 4 VAKT faces** (Vakt-One, Meridian, NightWatch, Ti) — they share the generator, so a
   re-bake will pick up the new behaviour. ⚠ **Expect drift** and re-measure each: they were last
   baked before `bare`, `rvFracFor`, `iconBox`/`iconClearY` and the GOAL_PROGRESS readout removal.
3. **Wave 1 remainder** (plan §W1.2–W1.4): Aurum date-window restyle, Settype-Halftone digits,
   Settype-Counterform hour. Untouched.
4. **Waves 2–3** — untouched.
5. **The date window's missing "SUN"** (−37% ink) — the one open flag on GT. Owner has not ruled:
   lock the window to the native date (no longer assignable) vs accept the number alone.

### The three things that cost the most time — read before touching another face
- ⭐ **Measure the design, don't read its source geometry.** Icon sizing was wrong twice in opposite
  directions (2× too big, then far too small) because I derived it from path coordinates.
  `gen/measure-icons.mjs` renders through the same rasteriser that bakes the dial and reports the
  real ink box. Use it.
- ⭐ **A stale `node serve.mjs` on :8099 will silently serve an OLD harness.** Mine died on
  `EADDRINUSE` into an unread log and every measurement went through a previous session's copy.
  Always `curl -s localhost:8099/ | grep <string unique to the current file>` before trusting a run.
- ⭐ **The pixel gate is blind to slot-content collisions** (a few px in a big region stays under the
  floor) and so is a page-scale screenshot. That is why `fit-check.mjs` now runs inside `check.mjs`.

---

**Companion to** `docs/COMPLICATION-FIX-PLAN.md` (the plan) + `docs/COMPLICATION-AUDIT.md` (the findings
F1–F13). **This file is the running record of execution decisions + new findings**, so the thread
survives chat changes. The plan/audit are the stable source; this logs what actually happened and what
the owner decided, face by face.

## ✅ VAKT-GT COMPLICATION DESIGN — SETTLED (owner, 2026-07-20). Built, measured, not committed.

Owner: *"lets just support the first 3 — defaults/empty can be battery, steps and heart rate, and
whichever complication we are supporting should just [have] a relevant icon visible inside the chrono
when set."*

| Dial | Slot | Default | Engraved scale | Supported |
|---|---|---|---|---|
| Top · 1 o'clock | `SLOT-A2-1` (slotId 0) | **`HEART_RATE` / `RANGED_VALUE`** | 0–250, 30°→330° | RANGED_VALUE · GOAL_PROGRESS · EMPTY |
| Hero · 6 o'clock | `SLOT-A2-2` (1) | `STEP_COUNT` / `RANGED_VALUE` | 0–8 ×1000, full turn + external r71 arc | same |
| Battery · 9 o'clock | `SLOT-A2-4` (3) | `WATCH_BATTERY` / `RANGED_VALUE` | 0–100, 40°→320° + lume arc | same |
| Date window · 3 | `SLOT-A2-3` (2) | `DATE` / `SHORT_TEXT` | — | unchanged (not a chrono) |

**A chrono slot may touch exactly three things: the needle, the arc (where the design has one), and
the small icon.** No value, no title, ever. Plate, both tick sets and every engraved numeral are
permanent dial art. `bare: true` in the spec short-circuits `taggedContent` to that.

**The icon is the slot's content** (`s.iconLayer`): `[COMPLICATION.MONOCHROMATIC_IMAGE]` drawn in the
EXACT box the designer's engraved glyph occupies — `(s*0.7+2)*2` px, tinted with the glyph's own
colour role. Clear the dial and the engraved heart/bolt/footprints returns from the EMPTY sprite.

### ⭐ THE NEEDLE MUST READ THE ENGRAVED NUMBERS, NOT THE PROVIDER'S RANGE (`rvFracFor`)
Caught by zooming the prototype to 2×: a resting pulse of **72 was pointing at the engraved "50"**,
because `RV_FRAC` places the needle by the provider's own `MIN..MAX` (40–200 ⇒ 72 is one fifth of the
way). On a dial with 0–250 milled into it that is the dial lying about the one number it was drawn
to show. Now the expression asks first:

```
(MIN >= engLo && MAX <= engHi) ? clamp((VALUE - engLo)/(engHi - engLo), 0, 1)   ← absolute
                              : RV_FRAC                                          ← "how full"
```
`engLo/engHi` come from the register's own engraved numerals (`s.engravedScale`, found on the face by
register centre — they are untagged dial art, so they are NOT in `s.artLayers`). Verified by dumping
the rendered angle rather than by eye: **72 bpm → 116.4°, 128 bpm → 183.6°** (both land on their own
number), **415 kcal on a 0–600 range → 237.5°** (does not fit 0–250, so it correctly falls back to
proportional). ⭐ XSD-verified that `&&`, `>=`, `?`, `:` are all in `_operatorType`; the generator's
attribute writer escapes `&` to `&amp;` on its own.

### ⭐ TWO FIDELITY STATES, because first-install is now DELIBERATELY not the design
The dials now follow battery/steps/heart-rate on install, which the design (a seconds sub-dial, native
battery, native steps) does not. Measuring that against the design would be measuring a requested
change. So `check.mjs` gained `--types 0:EMPTY,1:EMPTY,3:EMPTY`:
- **Cleared-dial test = the artwork test.** Every dial cleared ⇒ the build must BE the design.
  **t0 = 0.26% vs a 0.23% floor**, every region at/below its limit. This is the one that proves
  nothing was disturbed.
- **First-install test: 0.27%** — also every region at/below its limit.
Only flag in either: the known date-window weekday. WFF v2 PASS; only `Vakt-GT/` + `collection3-tools/`
differ.

### Preview honesty
`providerIcon` now draws the SELECTED metric's mark (heart / battery / flame / pin / thermometer /
footprints / clock) instead of an anonymous ring-and-dot. That is simulated **data**, exactly like the
"68%" it replaces — position, size and tint all come from the XML; only the glyph is stood in, because
only the wearer's watch knows what the real provider sends.

### ⛔ THE ICON-SIZE MISS — and the process fix (owner caught it, 2026-07-20)
Owner: *"the battery and heart icons are not placed properly.. did you not have it in workflow to
verify these UI issues?"* **He was right, and the check existed.** Polish skill §7 says verify
content fits its container **arithmetically, not by eye**. I applied that to the needle (dumped the
rendered angles) and then sized the icon from **`layerExtent`**, which returns `(s * 0.7 + 2)` — a
deliberately **padded** box for *overlap tests*. As a **drawing** size that is ~2× the engraved
glyph, so the battery icon printed across its own engraved "50" (measured after the fact: 17 px wide
where the design's glyph is 5).

**Why nothing caught it:** the pixel diff can't — a handful of px inside a big region stays under the
floor (`Left register` read 0.39% both before and after). Three rounds of screenshots can't — at page
scale it's a smudge. Only measuring the box against the numerals catches it.

**Fixed, in one place:** `svglib.mjs` → `ICON_REACH` (per-glyph half-width/half-height in the 12-unit
authoring grid) + `iconBox()` = the largest square that fits **inside** the glyph's own footprint,
because a provider `MONOCHROMATIC_IMAGE` **fills** the box it is given. Boxes went 15/16/16 → 7/7/6.
Verified empirically per dial (design vs build pixel bbox at 10×): top heart lands on the identical
pixels, hero footprints within 1 px, battery bolt now clearly above the "50".

**⭐ PROCESS FIX so it cannot recur:** new `gen/fit-check.mjs`, and **`check.mjs` now runs it on every
gate run** and prints `SLOT CONTENT vs DIAL ART`. Non-zero exit on any overlap. Also folded into
`COMPLICATION-FIX-PLAN.md`'s fidelity-gate section. **Standing rule: a screenshot is not verification
for slot content sitting near dial art — the percentage and the eye both miss it.**

#### ⛔⛔ THEN I OVER-CORRECTED — owner: *"you have made it way smaller… why the hell are you not analysing the actual design"*
Both failures share ONE root cause: **I kept deriving the icon size from the path COORDINATES
instead of from what the design actually renders.** First from `layerExtent` (≈2× too big), then
from `min(|coord|)` (way too small). The coordinates are a bad proxy — the paths are asymmetric,
their extreme vertices are thin tips that antialias away, and stroked glyphs gain `1.6 * s/12` of
stroke that the coordinates know nothing about.

**⭐ THE FIX IS A MEASURER, NOT A FORMULA.** `gen/measure-icons.mjs` renders every glyph exactly as
`svglib` draws it, through **resvg — the same rasteriser that bakes the dial** — and reads the ink
box. That table (`ICON_INK`, at the 12-unit authoring size) is now the single source of truth:

| glyph | measured ink @12 | at its dial size | provider box |
|---|---|---|---|
| `hr` | 12 × 11.25 | 8.0 × 7.5 (s=8) | **8** |
| `steps` | 9 × 10 | 6.4 × 7.1 (s=8.5) | **7** |
| `bolt` | 8 × 12 | 5.7 × 8.5 (s=8.5) | **9** |

`iconBox()` = the glyph's **larger** dimension (a provider image is square and FILLS its box, so
anything less reads as a shrunken design). Note `hr` is **wider than tall** — the coordinate
approach never would have found that.

**Where the box then collides, MOVE it, don't shrink it** (`iconClearY`, in `svglib.mjs` so the
generator and the checker import the SAME function and cannot drift): the engraved bolt tapers to a
thin tip and tucks above the "50", but a solid 9-unit square cannot, so on that register only the
icon slides **1.9 units toward the hub** — into empty plate. The engraved glyph itself never moves.
Verified against the design's real pixels (10× crops, design beside build) and by the gate:
`hr 8×8 clear by 4.2 · steps 7×7 clear by 10.2 · bolt 9×9 (nudged 1.9 up) clear by 0.5`.

⭐ **Lesson for every future face: when a question is "how big is this bit of the design?", RENDER IT
AND MEASURE IT.** Reading the source geometry is a guess dressed up as arithmetic — it was wrong
twice in opposite directions, and the owner caught both.

### Still open
- Date window shows "19" but not "SUN" (−37% ink). Untouched.
- ⚠ **Wrist test:** `HEART_RATE` only *guarantees* SHORT_TEXT. On an OEM that returns text or a
  health-app shortcut the top dial is **blank until the wearer assigns a ranged pulse**. If that is
  what the owner's watch does, the fallback is one line: drop the slot and restore the seconds
  sub-hand. Nothing else on the face is affected.
- All `[COMPLICATION.*]` names remain validator-unverifiable — only the wrist proves the needles move.

---

## ⛔ DIRECTION CHANGE (owner, 2026-07-20) — NO TEXT INSIDE THE CHRONOS
> "I do not want text inside the chronos — only the original design. We only need to support
> complications which can work with existing design… without changing design of needle and all and
> without adding extra text. And your html previews are very confusing — keep it simple."

**This supersedes the universal-gauge readout (research §7), the adaptive scale, the provider icon,
and every per-type text treatment on a machined register.** A chrono is now a pure instrument:

| | |
|---|---|
| **Permanent dial art (untagged, baked)** | plate · both tick sets · **the engraved numerals** · the engraved icon (heart / bolt / footprints) |
| **The slot may only touch** | the design's own **needle**, and the design's own **arc** where it has one |
| **`supportedTypes`** | `RANGED_VALUE GOAL_PROGRESS EMPTY` — *nothing else* |

**Why exactly those two types** — they are the only ones carrying a number with a range
(`value/min/max`, `value/target`), i.e. the only ones a needle can be driven from. The other six are
text or pictures and cannot be drawn without adding something the design has not got. Trimming
`supportedTypes` means the **watch's own editor never offers them**, so a user cannot break the dial.
✅ Schema-verified: `supportedTypes` is a free list of `complicationType`
(`2/complication/complicationSlotElement.xsd`) and a trimmed list passes the validator.

**Mechanism:** one spec flag, `bare: true`, short-circuits `taggedContent`'s plate branch to
arc + needle and returns. All the text/icon/adaptive-scale machinery still exists for non-bare slots,
so no other face moves.

⭐ **XSD CORRECTION to `COMPLICATION-DATA-SOURCES-RESEARCH.md` §3:** the doc says GOAL_PROGRESS "is
NOT a legal *default* type". **Wrong.** `defaultSystemProviderType` is typed `complicationType` — the
full enum, GOAL_PROGRESS and WEIGHTED_ELEMENTS included — and a `STEP_COUNT`/`GOAL_PROGRESS` default
**passes the validator** (probed, 2026-07-20). Runtime-unverified, so the hero still ships the
guaranteed `STEP_COUNT`/`RANGED_VALUE`; the option is open.

**Where the four slots landed:**

| Slot | Reads as | Default | Note |
|---|---|---|---|
| 0 · top (0–250, heart) | a heart-rate scale | ⚠ **`EMPTY` — OWNER DECISION PENDING** | see below |
| 1 · hero (full turn, footprints) | one turn = the whole goal + external r71 arc | `STEP_COUNT` / `RANGED_VALUE` | never blank |
| 3 · battery (0–100, bolt) | ⭐ 0–100 = "percent of that provider's own range" — true for ANY metric | `WATCH_BATTERY` / `RANGED_VALUE` | never blank; the genuinely universal dial |
| 2 · date window | a panel, the one place text belongs | `DATE` / `SHORT_TEXT` | unchanged, not a chrono |

### ⚠ THE ONE OPEN DECISION — the top dial's default
Only **two** system sources are guaranteed to give a needle-shaped number: `WATCH_BATTERY` and
`STEP_COUNT` — and both are taken by the other two dials. The top dial's own identity is heart rate
(heart icon + 0–250), but `HEART_RATE` only guarantees SHORT_TEXT and some OEMs return an app
shortcut. So: **A** = leave it as the design's running seconds sub-hand, not assignable (always alive,
2 of 3 dials assignable); **B** = heart-rate dial, needle on the 0–250 scale it was drawn for, blank on
first install on watches that only send HR as text. Currently built as A's artwork with B's slot
(assignable, `EMPTY` default ⇒ blank until the user picks). **Do not proceed without the owner's A/B.**

### Fidelity after the direction change
**t0 = 0.26% vs a 0.23% floor — byte-for-byte the score this face had BEFORE any of this work**, with
all three chronos now assignable. The engraved numerals are baked again, so the earlier
adaptive-numeral pixel debate is moot and reverted. WFF v2 PASS; only `Vakt-GT/` + `collection3-tools/`
differ.

⭐ **Gate fix that was needed and is now permanent:** `frame.html` feeds **each slot the metric its own
default provider supplies** (`DEFAULT_SAMPLE_FOR`). It used to push one global battery value into
every slot, so the moment the hero defaulted to `STEP_COUNT` its needle was measured against a battery
number and read as displaced — **a data difference reported as a design defect (0.75% vs a 0.24%
floor)**. Same principle as freezing the clock: both sides must draw the same data before a pixel diff
means anything.

### The review page, simplified (owner: "very confusing")
`compare.html` is now one screen: two watches, **one dropdown per dial in wearer's language**
("Heart rate", "Steps — goal beaten", "Nothing — cleared") that sets type *and* data together, then
two plain tables — *which of the eight shapes a chrono can take and why*, and *what each dial is for*.
The onion-skin/diff viewer, the per-type jargon, the two annotation columns and the preset row are
gone; the measurement is one line, with the region table behind a collapsed `<details>`.
State stays URL-addressable (`?type1=GOAL_PROGRESS&data1=over&theme=dark`) for screenshot checks.

---

## ▶ SESSION 2 working notes (2026-07-20) — mostly SUPERSEDED by the direction change above

**All three chronos are now assignable + a complication test bench exists.** Nothing committed.
Gate: **t0 0.25% vs a 0.23% floor**, every region at/below its own limit (was 0.26%). WFF v2 **PASS**.
`git -C faces status` = only `Vakt-GT/` + `collection3-tools/`.

### The blocker, decided: the battery register IS a slot now (SLOT-A2-4 → slotId 3)
Ownership split exactly like its two siblings, **plus one deliberate exception**:
- **UNTAGGED (permanent hardware, baked):** plate · tick set · **the engraved bolt icon**
- **TAGGED (slot content):** numerals (adaptive) · lume arc · battery needle
- **Default = `WATCH_BATTERY` / `RANGED_VALUE`**, *not* `EMPTY`. An EMPTY default renders nothing on
  first install (audit L1, on-wrist-proven), which would have turned the one register that always
  works today into a black hole. So the default state IS an assigned-provider state.
- ⭐ **Why the bolt stays engraved (`icon: false` on the slot):** because the default is a real
  provider, a provider icon would replace the design's bolt with a different glyph in a different
  place *on first install* — measured at **0.81% on the Left register vs a 0.533% limit, i.e. the
  gate fails**. Baking it keeps the fresh install pixel-identical. **Cost: assign a non-battery
  source and the bolt is still there.** Owner to rule; the swap is a one-line spec change.
- Adaptive scale confirmed working: calories → 0–600, HR → 40–200, temperature → −10–40 (negatives fine).

### Second-order fixes the same work forced (all measured, none move the default state)
1. **`pushScale` now emits the design's own font size** (`size` is `xs:float` — verified against
   `2/group/part/text/fontElement.xsd`), not a 14 px legibility floor. The floor made live numerals
   BIGGER than the baked ones on the same register.
2. **The numeral box is now EVEN** (`2*round(size*0.8)`), so `centre − box/2` is a whole number.
   The odd box threw every numeral half a pixel up. **This alone took the Left register from 0.81%
   → 0.31%, below its pre-change 0.39%.**
3. **Per-slot `gauge` sweeps** — needles were falling back to a generic −150..150, so a gauge on the
   top register (scale 30→330) pointed **180° out**. Now top `[30,330]`, battery `[40,320]`,
   hero `[0,360]`.
4. **Per-slot `arc` restored** (lost in the previous session's revert): top `arc:false` (design has
   none), hero the external `r71 −150..150` ring, battery its `lume r39 w2.5 40→320`. `arc` gained
   optional `from`/`to` because the hero's needle turns 360° while its ring opens only 300°.
5. **GOAL_PROGRESS no longer prints a readout on a numbered register** — "540 / of 800" was stamped
   straight over the engraved 600 and 400 on all three dials, every theme. Same rule RANGED_VALUE
   already had. GP also now drives the needle on any register with a scale + needle sprite
   (was `needle:true` only), so the top register's goal is readable at all. ⚠ **This will land on
   the other 4 VAKT faces when they are re-baked** — expected drift, same as the existing note.

### The test bench — `tools/face-review/` (the sanctioned harness, extended, NOT a new one)
- `compare.html` is now **design ↔ build + a complication test bench**: one card per real
  `<ComplicationSlot>` (built from the XML, so a slot added/removed appears automatically) with a
  **type** dropdown (only the types that slot advertises) and a **fed by** dropdown (14 sample
  providers spanning battery/HR/calories/distance/temperature + goals incl. an over-target one).
  Presets for the states worth checking. **State is URL-addressable** —
  `?type3=GOAL_PROGRESS&data3=over&theme=dark&hands=0` — so any combination is reproducible and
  screenshot-able instead of "click these six things".
- `current.js`: per-slot sample data (applied around each slot and cleared, so one dial cannot set
  another's numbers) + ⭐ **`fitText()`** — WFF clips a `PartText` to its box and ellipsises it;
  SVG `<text>` does neither, so the preview had long provider values sprawling across the dial,
  a picture of something the watch would never draw. Now it trims exactly as the platform does.

### ⚠ TRAP THAT ALMOST INVALIDATED THIS SESSION — a STALE `serve.mjs` on :8099
A `node serve.mjs` left running by an earlier session was still serving an **old copy of the
harness** from the scratchpad. My own `node serve.mjs` died on `EADDRINUSE` (into a log I did not
read) and every measurement went through the old copy. Caught only because the screenshot showed
the OLD page. **Always check `curl -s localhost:8099/ | grep <a string unique to the current file>`
before trusting a run**, and read the server log. The numbers happened to reproduce identically
afterwards, but they could just as easily not have.

### Still open (flagged on the page, NOT fixed — owner's call)
- Date window "19" but no "SUN" (−37% ink). Unchanged. Visible on the page: pick **Empty** on that
  slot and "MON" appears, which is the whole diagnosis in one click.
- No digital readout on a numbered register (unchanged trade).
- **NEW — text providers are sized at 28% of the dial**, so "128 bpm" trims to "128 b…" and
  "Standup 09:30" to "Standup …". Not changed: it affects every VAKT register, and shrinking vs
  trimming is a design call. Now honestly previewed rather than sprawling.
- `HEART_RATE` only guarantees SHORT_TEXT (unchanged).
- `WEIGHTED_ELEMENTS` cannot be previewed (provider supplies the colours/weights).
- t1 reports flange "EXTRA ink" and t4 "over floor". **Traced, not assumed:** t1 is the ink metric
  measuring each image against its OWN median (the build's dial is ~13 luma darker, so the flange
  band crosses the ±18 threshold in one and not the other — mapped it, it is the rim only); t4 is
  the red-line arc's acid-yellow anti-aliasing (483 of 795 flagged px sit at r200–225). Neither is
  content; neither is near the dials touched. Left untouched — tuning `fidelity.py` to make a flag
  disappear is the one thing the gate must never do.

---

## ▶ STATE OF VAKT-GT (2026-07-20, session 1 — superseded above)

**The chrono art was RESTORED to the design verbatim** (owner: "you have ruined it… match the chronos
exactly with source of truth, complications later"). The earlier "instrument" redesign
(`registerHR`/`registerStepsGoal`/`registerBatterySlot`/`dateWindowLocked`, clean scales, HR 40–200)
is **reverted and superseded** — those helpers still exist in `spec/cat-a2.js` but WF-A2 no longer
uses them. Do not reinstate them without the owner asking.

**Built this session, all measured, nothing committed:**
1. **Registers verbatim from the handoff** — `registerScaleGT` / `registerStepsGT` draw exactly the
   design's `registerScale` / `registerSteps` (both tick sets, numerals, icon, needle) + the original
   `registerBattery`, external r71 steps arc, framed `dateWindow`. **Fidelity 0.26% vs a 0.23% floor.**
2. **Ownership fix (the thing that makes complications possible at all):** the design tags EVERY
   register layer with `withSlot`, so assigning any complication used to erase the ticks and numerals
   and leave a bare plate. Now **plate + tick sets are untagged (permanent hardware)**; only
   **numerals + icon + needle** belong to the slot. Nothing visible changed — the gate proves it.
3. **Adaptive scale numerals** (`pushScale` in `taggedContent`): on RANGED_VALUE the numerals are
   redrawn from `[COMPLICATION.RANGED_VALUE_MIN/MAX]`, on GOAL_PROGRESS from `GOAL_PROGRESS_TARGET`
   (÷1000 once the target reaches 10k, matching the hero's 0–8 convention). Same positions, font,
   size, colour. Empty/text/image types keep the design's own engraved numbers.
4. **No digital readout on a numbered register** — six numerals + icon + needle inside a 116px dial
   leaves no room; tried below the hub and in the scale gap, both collided. Registers with a *clean*
   tick scale still print the value. ⚠ Owner has NOT ruled on this trade (read to the nearest tick vs
   an exact number on a clean scale).
5. **Seconds sweep everywhere** — `<Sweep frequency="15"/>` on every SecondHand incl. sub-dials (was
   `<Tick>`); the in-complication sub-hand uses `[SECOND_MILLISECOND] * 6`.
6. **Per-slot arc config** (`arc: false` / `{r,w,color,track,trackOpacity,cap}`) so a register's gauge
   arc follows its own design instead of one generic gauge.

**⚠ Open / not done:**
- **The battery register is NOT a complication slot** — in the design it is fixed native art
  (`registerBattery(128,230,44)` with no slotId). GT therefore has **2** assignable round registers,
  not 3, plus the date window. Making it a slot is an owner decision (they have hinted at "all 3
  chronos").
- **Date window shows "19" but not "SUN"** — measured as `CONTENT MISSING −37% ink`. Its default
  DATE provider supplies a value, not the native day row. A complications-step fix.
- HR default type: research §5 says `HEART_RATE` only guarantees SHORT_TEXT.
- ⭐ **`textLength()` / `subText()` / `numberFormat()` DO exist** in the WFF function enum — the
  research doc's "no string functions" claim was wrong and is now corrected there. Reopens the
  "provider text else numeric" fallback and possibly thousands separators. Runtime-unverified.

## ⛔ HAND-OFF FORMAT IS NOT OPTIONAL (owner, 2026-07-20 — asked 4×)
Every face is handed back as an **interactive Chrome page with the ORIGINAL DESIGN beside the CURRENT
BUILD**. Never PNGs, never prose. Harness + exact commands: `docs/COMPLICATION-FIX-PLAN.md` →
"MANDATORY WORKFLOW"; code lives in `tools/face-review/`. Read the plan → fix → self-verify by
rendering and looking → open the comparison in Chrome.

## ✅ SETTLED — the showroom's glass reflection is NOT a design element (owner, 2026-07-20)
Owner spotted an "egg shaped area" shading the upper-left of the design that "aligns almost perfectly
with that small arc at the edge". Both are one object: the shared showroom renderer's **simulated
glass reflection** — `renderer.jsx` lines 824–826, right after its own `/* crystal edge shadow + glass
reflections */`: a white ellipse (cx182 cy145 rx145 ry88, rotate −28°, opacity .05), a bright rim
streak (arc r236 −78°→−18°, opacity .07) and a crystal edge shadow. Proof it is presentation, not
dial art: drawn OUTSIDE the face's clip group, over the steel case and rim as well; `aod ? null :` so
it disappears in always-on; present in NO face spec (`cat-a2.js` has no such layer); inherited
identically by all 25 Collection-3 designs.
**DECISION: leave it out** — a real crystal reflects real light and moves with the wrist; a baked one
would sit on top of that and never move. **Applies to every face in every collection; do not "fix"
it, and do not flag it as a difference again.** The fidelity harness strips it before diffing for the
same reason (`frame.html`; `?glass=1&box=520` reproduces the evidence).

## Working method (owner-set, 2026-07-19)

- **One face (or collection) at a time.** For every face touched, build an **interactive HTML demo the
  owner opens in Chrome**, get explicit approval, *then* bake the real spec/generator/XML change.
- Owner is reviewing **each face in detail** — expect new design questions beyond F1–F13; log them under
  "New findings" below and treat them as part of the program.
- **Fresh chats as context grows** — always update this file + memory before handing off (no context loss).
- **Every lesson → `wff-watchface.skill`** (the project's living-source rule) — plus audit §2's L1–L4 after
  on-wrist confirmation.
- Demos live in the session scratchpad (not the repo); the *decisions* live here.

## Generator mechanism (how the VAKT default fix is applied)

- The generator derives a slot's `defaultSystemProvider` from the spec's prose `default:` string
  (`gen/wff.mjs` ~ line 256–268 regex). VAKT registers say "Empty…" / "…(native drawing)" → mapped to
  `EMPTY` → the black hole.
- **Fix = add an explicit `defaultProvider` (and optional `defaultProviderType`) field** to the slot's
  object in `spec/cat-a2.js`, and make `wff.mjs` prefer it over the prose regex. Faces without the field
  are byte-identical (no regression). Net XML change per fixed slot = **one `<DefaultProviderPolicy>` line**.

## Wave 1 — status board

| # | Face | Fix | Demo | Owner decision | Baked | Validated |
|---|------|-----|------|----------------|-------|-----------|
| 1 | **Vakt-GT** (WF-A2) | **INSTRUMENT DESIGN BUILT FOR REAL** (2026-07-19) | ✅ approved (`vakt-gt-instruments-approved.png`) | 3 live-instrument dials: HR(top)+steps(hero)+battery(left) slots, date→native | ✅ regen WF-A2 only; 20 non-VAKT byte-identical (git-checked); orphan `needle_1` pruned | ✅ WFF v2 PASS · self-reviewed (real baked art + composites) · **awaiting final owner eyeball + on-wrist** |
| 2 | Vakt-One (WF-A1) | register defaults (F1) | — | — | — | — |
| 3 | Vakt-Meridian (WF-A3) | register defaults (F1) | — | — | — | — |
| 4 | Vakt-NightWatch (WF-A5) | register + dup-sunset (F1/F9) | — | — | — | — |
| 5 | Vakt-Ti (WF-A4) | register defaults (F1) | — | — | — | — |
| 6 | Aurum-Guilloche | slot restyle + default (F2/F13) | — | — | — | — |
| 7–10 | Aurum ×4 | restyle sweep | — | — | — | — |
| 11 | Settype-Halftone | clipped hero digits (F3) | — | — | — | — |
| 12 | Settype-Counterform | hero hour crop (F4) | — | — | — | — |

Ship (after all 12 approved+baked): push fablecollection → Dialed submodule bump →
`gen-facepacks.mjs "<ABS ROOT>"` → version bump **app/ AND wear/** → tag next free `dialed-v*` →
both direct APK links → on-wrist checklist. Wave 2 only after owner reports on-wrist.

## New findings (raised during the owner's detailed review — beyond F1–F13)

### NF-1 · VAKT edge arcs are unlabelled / ambiguous (raised 2026-07-19, Vakt-GT)
**Owner:** "there is a circular progress bar at the edges… as a user I have no clue what that bar does."
**Facts (from `spec/cat-a2.js`):**
- **Vakt-GT red-line arc** (`arc r221 −32°→−3°, color accent, value:1`): **static decoration**, tracks
  nothing — a racing-tachometer "redline" styling cue. Looks like a gauge; isn't one. *The confusing one.*
- **Vakt-GT steps arc** (`arc cx210 cy318 r71 −150→150 data:steps`, tagged to the bottom hero register):
  a **real step-progress gauge**, but unlabelled and only visible in the register's Empty state.
- **Vakt-Ti battery arc** (`arc r212 −58°→58° data:battery` + bolt icon at 225,52): a **real battery
  gauge** across the top; has a bolt icon but still easy to miss.
- **Vakt-One / Meridian / Night Watch:** *no* perimeter progress arc — the outer ring is minute
  ticks + hour indices (decoration + minute reading), not a gauge.
**Problem:** arcs that either look like gauges but do nothing (GT redline) or are real gauges with no
label (steps, battery) leave the user guessing.
**Options:** (a) drop the purely-decorative redline; (b) label every functional arc with a small
icon/units so its meaning is obvious; (c) leave as designed + explain in the store description.
**DECISION (owner 2026-07-19): Option A — make every arc honest, series-wide.** Drop purely-decorative
gauge-looking arcs; every real gauge keeps a clear icon/label.
**Concrete edits:** Vakt-GT red-line arc REMOVED (spec `WF-A2.layers`, re-bakes GT dials). Real gauges
already carry icons (battery bolt, steps register, Ti top-arc bolt); One/Meridian/NightWatch have no
fake arc. Rule now applies to any future VAKT touch.
**Status:** RESOLVED for VAKT (verify visually on the rebuilt GT demo).

### NF-2 · VAKT dials should be LIVE INSTRUMENTS, not number-boxes (raised 2026-07-19)
**Owner:** the battery/steps/HR sub-dials should render as instruments whose **needle physically tracks
the real value** (battery %, heart rate, steps), with a themed icon inside — "same behaviour for all
default chronos across the VAKT faces." Specifically: assigning battery should keep the needle+ring
(not drop to a flat number); the heart-icon dial's needle should sweep to real HR.
**Platform facts that shape this:**
- A slot's native instrument (needle bound to the real sensor) lives in the EMPTY block → only shows
  when cleared, never on install (F1). So "live needle on install" needs either a *dedicated* dial
  (de-slot → native `[BATTERY_PERCENT]`/`[STEP_COUNT]`/`[HEART_RATE]` binding) OR a gauge-capable
  default complication (only WATCH_BATTERY gives a safe RANGED_VALUE; STEP_COUNT is a count, not a
  gauge → no complication needle; HR complication is v2-legal but permission-gated).
- **Heart rate needs a one-time BODY_SENSORS permission** before any face shows live HR (Android rule)
  → HR dial is blank/at-rest on a fresh watch until granted. Battery + steps are live immediately.
- Register currently: top `registerScale` needle is a running-SECONDS hand on a 0–250 scale + heart
  icon (odd hybrid); lower `registerSteps` needle binds native `[STEP_COUNT]` (exact) in EMPTY only.
- Needle-as-`PartImage` + `Transform target="angle"` bound to a value is legal inside a Complication
  (same mechanism as the EMPTY sub-hand); [COMPLICATION.*] bindings are on-wrist-unverified by the
  validator.
**HR-PERMISSION FACT CORRECTED (owner challenged; verified 2026-07-19):** a code-free WFF face reads
`[HEART_RATE]`/HEART_RATE-complication as a **v2 platform data source with NO per-face permission
prompt** — on a set-up watch it just reads live (can only read "unavailable" if HR was never enabled).
The earlier "body-sensors prompt / blank on new watch" claim was WRONG. (Google WFF features page;
skill's "may require permission" hedge was misleading — fold correction into `expressions-and-data.md`.)

**DESIGN (owner 2026-07-19):** VAKT gets 3 **swappable instrument dials** — battery, heart-rate, steps —
each rendering its home data as a live **needle + gauge + small provider icon** by default, and
swapping to any other complication is fine (instrument design "goes away", other type-blocks render).
**Consistency requirement:** manually re-selecting the home complication must look identical to the
default → achieved by making the default = the home system complication rendered by the same
instrument type-block. Icon = `[COMPLICATION.MONOCHROMATIC_IMAGE]` (auto-correct per provider, swaps
cleanly). Implementation: enhance `taggedContent` RANGED_VALUE + GOAL_PROGRESS to add a needle
`PartImage` bound to the fraction; add `defaultProviderType` support to the generator; defaults
battery→WATCH_BATTERY/RANGED_VALUE, HR→HEART_RATE/RANGED_VALUE. Baked battery registers may need to
become slots to be swappable (per-face structural). Date/Day window → locked native date+day.
**OPEN — steps-only fork:** STEP_COUNT complication is a count (no gauge). To keep steps SWAPPABLE +
show a needle, default it to STEP_COUNT/**GOAL_PROGRESS** (v2) → a "progress to daily step goal" fill
(swappable), NOT the classic mod-10k sweeping needle. Classic sweeping needle = dial must be dedicated
(not swappable). Awaiting owner pick for steps. **[COMPLICATION.*] needle bindings are on-wrist-unverified.**
**Status:** building battery+HR as swappable instruments now; steps = GOAL_PROGRESS (owner picked option 1); verify on-wrist.

### NF-3 · Self-review the rendered UI before every hand-off (owner 2026-07-19)
Owner: use the screenshot-and-analyse ability to catch obvious UI issues myself — don't make them
point out the obvious. **Standing rule: run polish-skill §7 on the actual high-DPI screenshot (crop to
the watch, hands on AND off, ≥1 alt theme) BEFORE showing any demo.** First instrument mock shipped with
the live value colliding with the scale numerals on the gauge dials — caught only after the owner
prompted. Fix applied: gauge dials cleaned to ticks + needle + icon + one value (no numeric scale
labels). ⚠ **The battery register bakes its scale INTO the dial art (it isn't a slot)** — code can't
strip it; the real build must re-bake that register clean (or slot it) to match the HR/steps dials.
Demo patches the battery floor for visualisation only.

---

## VAKT INSTRUMENT DESIGN — APPROVED (build spec for the next chat)

Owner approved the GT instrument design 2026-07-19. **Reference:** `docs/design-demos/vakt-gt-instruments-approved.png`
(the visual) + `docs/design-demos/vakt-gt-instruments.html` (the reusable demo harness — copy to a
scratchpad, re-copy the face's `drawable-nodpi/*.png` + `font/*.ttf` into `./assets`, retarget the
`FACE` config block per face; open with `chrome.exe --new-window`, self-review with
`--headless --screenshot --force-device-scale-factor=2`).

**The design (applies to every VAKT face, adapted per layout):**
- **Three live-instrument dials — battery, heart-rate, steps — each a clean machined register:** tick
  marks (NO numeric scale labels — they collide with the value, NF-3) + needle/ring + **small provider
  icon** (`[COMPLICATION.MONOCHROMATIC_IMAGE]`, auto-correct per provider) + ONE value.
  - **Battery** = RANGED_VALUE instrument: needle→%, fill arc, bolt icon, "NN%". Value text scales with
    dial radius (small dial → smaller text: `r<52 ? 0.30·r : 0.42·r`).
  - **Heart-rate** = RANGED_VALUE instrument on a **40–200 scale** (was a generic 0–250): needle→bpm,
    fill arc, heart icon, "NN" + "bpm". **No permission blocker** (NF-2 correction).
  - **Steps** = GOAL_PROGRESS instrument (owner chose this over the mod-10k sweeping needle): goal ring
    fills toward daily goal, **ring thickness ≈ 5.6 at r64** (owner cut 20% from 7), footprints icon,
    "N,NNN" + "of NN,NNN".
- **Swappable + consistent:** every dial stays user-assignable; a non-home complication renders the
  clean icon+value style; the home complication renders the instrument (same type-block → identical
  whether default or manually chosen). Gauge fill ≈ 3–3.5 thick for the needle dials.
- **Date/Day window → locked native** `[DAY]` + `[DAY_OF_WEEK]` (de-slotted, non-customisable, gated by
  the Date toggle; never blank).

**Implementation (generator — `faces/collection3-tools/`):**
1. Enhance `taggedContent` RANGED_VALUE + GOAL_PROGRESS (plate branch): add a **needle `PartImage`**
   bound to the clamped fraction (`clamp((val-min)/(max-min),0,1)` for RV; `clamp(val/target,0,1)` for
   GP) sweeping the register scale + pivot at centre; keep fill arc + provider icon + value; **drop the
   numeric scale numerals** from the register art (re-bake `slotart`/scale clean). Reuse the register's
   needle sprite.
2. Add **`defaultProviderType`** support (already have `defaultProvider`): battery→`WATCH_BATTERY`/
   `RANGED_VALUE`, HR→`HEART_RATE`/`RANGED_VALUE`, steps→`STEP_COUNT`/`GOAL_PROGRESS`.
3. **Battery register is baked (not a slot) on One/GT/Meridian/NightWatch** → convert to a slot + re-bake
   its scale clean so it's a swappable instrument. **Ti's battery is a flange ARC, not a sub-dial** →
   adapt (leave as the arc, or reconsider) — Ti has only 2 register dials (HR + steps).
4. **De-slot the date window** → native tokens. Keep the OTHER slots per face (One's unread chip,
   Meridian's event line, NightWatch's sunrise/sunset) as they are (own fixes later).
5. HR scale: re-bake the top register from 0–250 → **40–200**.
6. `[COMPLICATION.*]` needle/gauge bindings are **on-wrist-unverified** (validator can't check them) —
   flag for the owner's wrist test.

**Per-face register map (for "intelligently apply"):**
| Face | Battery | Heart-rate (top) | Steps | Also has |
|---|---|---|---|---|
| GT (A2) | baked→slot | slot | slot | date→native |
| One (A1) | baked→slot | slot | baked→slot | date→native, **unread chip** (keep) |
| Meridian (A3) | baked→slot | slot | baked→slot | date→native, **event line** (keep) |
| Ti (A4) | flange **arc** (adapt) | slot | slot | date→native |
| NightWatch (A5) | baked→slot | slot | baked→slot | date→native, **sunrise/sunset** (keep) |

**Current working-tree state of GT (uncommitted, on disk, carries into the next session):** red-line
REMOVED + `defaultProvider` mechanism added to `wff.mjs` + SLOT-A2-1 default UNREAD / SLOT-A2-2 default
STEP_COUNT in `cat-a2.js` + Vakt-GT regenerated. **The UNREAD/STEP_COUNT defaults are now SUPERSEDED by
the instrument design** — the next chat replaces them (HR + steps-goal + battery slot). Redline removal
and the defaultProvider mechanism are KEPT foundations. Nothing committed/pushed yet (VAKT not done).

## ⛔ STOP — READ THIS BEFORE TOUCHING ANY VAKT REGISTER (owner, 2026-07-19)

**The original design files are the ONLY source of truth for register geometry.** They live OUTSIDE this
repo at:
`D:\Apps\WearOS Apps\WatchFaces\WatchFaces Collection 3\Updated Designs with Complications Support\Premium Smartwatch Face Collection-handoff\premium-smartwatch-face-collection\project\`
→ `faces/cat-a2.js` (spec) · `Watch Collection Showcase v2.dc.html` (visual showroom).

**Viewing the showroom:** it CANNOT be opened as a `file://` — its runtime fetches `support.js` +
`faces/*.js`, which the browser blocks. Serve it over HTTP and open `http://localhost:8099`
(a working static server is in the scratchpad; ~30 lines of `node:http`). Opening the "(standalone)"
dist file directly renders the header and then silently stops — that is the symptom.

**⭐ WORKFLOW RULE (owner-imposed after repeated rework):** before changing ANY register, **diff the
current spec against the original `faces/cat-a2.js` helper** and change only what the owner named.
Do NOT design from memory, from the approved PNG, or by eye. Do NOT chain speculative edits — make ONE
named change, render it, self-review the screenshot, then show it. Every round of "tune it by eye" costs
the owner real money.

### The 3 confirmed deviations still OPEN on Vakt-GT (owner-identified 2026-07-19)
Root cause: `taggedContent` imposes ONE generic gauge (track+fill+needle+icon+value) on every plate
register, overwriting each register's own personality. The original gives each register its own arc
colour, arc radius, thickness, needle and tick character. **The fix must make the instrument respect the
register's original art, not replace it.**

| # | Register (GT) | ORIGINAL (verbatim from handoff `cat-a2.js`) | Current build (WRONG) | Fix |
|---|---|---|---|---|
| 1 | **Top / upper-right** `registerScale(260,150,58,'SLOT-A2-1')` | ticks `r-6 count25 len5 w1.1 muted 30→330` + ticks `r-6 count5 len10 w2 ink` + numerals `0,50,100,150,200,250 @r-21` + icon `hr @cy+r*0.5` (BELOW) + hand `second len r-10 tail13 w1.8 needle accent hub4`. **NO ARC.** | RANGED_VALUE block draws a track+fill arc | **Remove the arc on this register.** Owner: "rest we can leave as is." |
| 2 | **Hero / lower-middle** `registerSteps(210,318,64,'SLOT-A2-2')` | ticks `r-5 count50 len4 w0.9 muted` + ticks `r-5 count10 len9 w1.9 ink` + numerals `0,_,2,_,4,_,6,_,8,_ @r-17` + icon `steps @cy-r*0.45` (ABOVE) + **hand `data:'stepsDial' len r-9 tail11 w1.6 needle LUME hub3.4`** + **EXTERNAL arc `cx210 cy318 r71 w3.5 −150→150 accent data:steps track muted 0.22 cap round`** (r71 is OUTSIDE the r64 plate) | needle DELETED, external arc DELETED, replaced by a fat internal goal ring `r-8 w5.5` | **Restore the needle AND the thin external r71 arc.** |
| 3 | **Left / battery** `registerBattery(128,230,44)` | ticks `r-5 count20 len4 w1 muted 40→320` — **ONE set, NO large markers** + numerals `0,25,50,75,100 @r-16` + arc `r-5 w2.5 40→320 **color:'lume'** track muted 0.22` + icon `bolt @cy+r*0.48` (BELOW) + hand `data:battery len r-10 tail10 w1.6 needle LUME hub3.4` | arc renders **accent (red)**, not lume; the review demo also draws large/major ticks that do not exist | **Arc colour → `lume`. No large markers.** |

⚠ Also note: the review harness (`scratchpad/gt-review/universal.html`) draws every 5th tick as a MAJOR
tick — that is a DEMO artifact the real spec does not have. Fix the harness too, or it keeps lying.

### ✅ ALL THREE FIXED + the harness replaced (2026-07-20) — awaiting owner eyeball
Built, WFF v2 **PASS**, byte-compare clean (only `Vakt-GT/` + `collection3-tools/` differ). **Not committed.**

**Root cause fixed properly, not papered over.** `taggedContent`'s one generic gauge is now driven by
**per-slot arc config in the spec** — `arc: false` (no arc), or `arc: {r,w,color,track,trackOpacity,cap}`
with `r` as an ABSOLUTE design radius so an arc may sit outside its plate. A slot with no `arc` field
gets the previous generic gauge, so nothing else in the fleet moves.

| # | Register | What changed | Source |
|---|---|---|---|
| 1 | Top / HR `SLOT-A2-1` | `arc: false` → the imposed track+fill gauge is gone; ticks + needle + icon + value remain | original `registerScale` has no arc |
| 2 | Hero / steps `SLOT-A2-2` | needle restored (`stepsDial`, lume, len r−9, tail 11, w1.6, hub 3.4) **and** the external `r71 w3.5 −150→150 accent, track muted 0.22, round` arc restored as a slot-tagged layer; the GOAL_PROGRESS block re-draws that same geometry, so empty and assigned states are identical. Fat internal r−8 w5.5 ring gone | original `registerSteps` + face layer line 230 |
| 3 | Left / battery `SLOT-A2-3` | arc colour `accent` → **`lume`**, w2.5 at r−5, 40→320, track muted 0.22. Ticks were already ONE set (the major markers were only ever the harness lying) | original `registerBattery` |

**Two things self-review caught before hand-off** (neither was in the brief; both are consequences of the
named fixes, not tuning): (a) restoring the needle put it straight through the centred goal readout, so
the value + "of TARGET" pair moved below the hub — and the first attempt at that pushed "of 10000" OUT of
the plate, fixed by sizing the target line off the dial instead of the 20px plate-title size; (b) the new
GOAL_PROGRESS needle initially leaked into Vakt-One/Meridian/NightWatch/Ti (any goal slot with an
empty-state hand), so it is now gated behind an explicit spec `needle: true`. RANGED_VALUE needles stay
automatic — a ranged register always has a scale; a goal ring does not imply one.

⚠ **Those 4 VAKT faces have UNAPPLIED drift waiting for them.** They were never re-baked after the
previous session's instrument work, so `build-all.mjs` (no arg) rewrites their RANGED_VALUE blocks
(fill 3→3.5, needle added, TEXT readout replacing value+title). Reverted here to keep the diff to GT.
That drift lands the moment each face is approved and re-baked — expected, not a regression.

### ⭐ THE HARNESS NOW RENDERS THE REAL XML — `collection3-tools/gen/review.mjs`
The old `design-demos/*.html` harness **redrew each register in JS**, which is why it invented major ticks
and patched over baked art: it was a picture of what we believed, not of what we shipped. Replaced by a
node renderer that parses the face's actual `res/raw/watchface.xml` — real slot rects, real arcs including
their `Transform endAngle` expressions, real needle sprites and angle expressions, real text templates —
composites them over the baked dial PNG and rasterises with resvg. Only the `[COMPLICATION.*]` values are
simulated. It cannot show a register the build does not contain.

```
node gen/review.mjs ../Vakt-GT <outDir> [t0|t1|…|dark] [--hands]
SLOT_TYPES='{"0":"RANGED_VALUE","1":"EMPTY"}' node gen/review.mjs …   # force types
```
Reviewed this way: first-install default, hands on/off, `t1` (light), `dark`, all-EMPTY, and HR forced to
RANGED_VALUE (the only state where fix 1 is visible, since HR defaults to SHORT_TEXT).

---

## GT BUILD LOG — what actually shipped into the generator (2026-07-19, this session)

**GT is BUILT + validated + self-reviewed. Not committed (ship only after all 5 approved).** The
instrument design now exists for real in the generator; the four other VAKT faces reuse the SAME
generator machinery — they only need per-face spec restructuring + a demo + owner approval, then a re-bake.

**Generator changes (`gen/wff.mjs` + `gen/bake.mjs`) — all VAKT-gated, 20 non-VAKT faces byte-identical:**
1. `slotXml`: honours a spec `defaultProviderType` → emits it as `defaultSystemProviderType` (so a
   default renders as its instrument type, not a flat SHORT_TEXT). Slots without the field unchanged.
2. `analyzeFace`: for each tagged **plate** slot records `s.needleSprite` (its empty-state needle base:
   `subsec_N` for a seconds sub-hand, `needle_N` for a data hand) so the content block can reuse it.
3. `taggedContent` **RANGED_VALUE (plate)**: now draws a **live needle** (`s.needleSprite`, pivot on the
   register axis, angle `gf + RV_FRAC*span`) + accent fill + provider icon + value, all across the
   register's own **`s.gauge=[from,to]` sweep** (battery 40..320, HR −150..150). Was arc+text only.
4. `taggedContent` **GOAL_PROGRESS (plate)**: goal ring on `s.gauge` sweep (thick 5.5) + footprints icon +
   value + **"of TARGET"** (was "NN%"); overflow lap kept (dark-mode falls to `muted`).
5. **`dateLock`** mechanism: a spec layer tagged `dateLock:true` becomes a native day/date token (or its
   frame) gated on `[CONFIGURATION.date]`, **no slot, non-customisable, never baked** (new kinds
   `dateLockText`/`dateLockStatic` in `analyzeFace`+`emitLiveLayer`+bake `LIVE_KINDS`).

**GT spec (`spec/cat-a2.js`) — GT-isolated helpers so the other 24 faces stay byte-identical:**
- New helpers `registerHR` (40..200, gap at 6, heart on top, seconds sub-hand in EMPTY),
  `registerBatterySlot` (0..100, gap at top, bolt, battery needle), `registerStepsGoal` (plate + footprints
  only; the ring is live GP content), `dateWindowLocked` (frameless native `dnum`+`day3`, date-gated).
- WF-A2 slots reassigned: **SLOT-A2-1 = HR** (`HEART_RATE`/`RANGED_VALUE`, gauge −150..150), **SLOT-A2-2 =
  Steps** (`STEP_COUNT`/`GOAL_PROGRESS`, gauge −150..150), **SLOT-A2-3 = Battery** (`WATCH_BATTERY`/
  `RANGED_VALUE`, gauge 40..320). Date de-slotted → native. External r71 steps arc + red-line already
  removed. All CIRC_TYPES still supported per slot → fully swappable.

**Verified:** `node gen/build-all.mjs WF-A2` clean · WFF v2 PASS (portable JRE17 + `wff-validator.jar 1.7.0`,
`java -jar wff-validator.jar 2 <xml>`) · 20 non-VAKT regenerated = **0 content diff** (only a pre-existing
cosmetic AndroidManifest comment drift from scaffold.mjs, reverted) · orphaned `needle_1` sprites removed ·
baked `preview.png` shows clean registers + hands-over-content · demo composites over the REAL baked art
(themed + dark) match the approved PNG, HR register shows **no numeral bleed-through** (scale truly clean).

**⚠ ON-WRIST-UNVERIFIED (must confirm on the watch):** (a) `[COMPLICATION.*]` needle/gauge angle bindings
— the validator can't check expression names, so only the wrist proves the needle sweeps to live HR/battery
and the goal ring fills; (b) `HEART_RATE` default reads live with no permission prompt (NF-2 correction);
(c) WFF `%.0f` has **no thousands separator** → the real face shows "8432"/"of 10000", not the demo's
"8,432"/"of 10,000" (cosmetic; flag to owner).

**Reusable review harness:** `scratchpad/gt-review/` — the approved demo HTML over the real baked art +
fonts; render with installed Chrome `--headless --screenshot --force-device-scale-factor=2`. Copy per face.

**NEXT (needs owner):** final GT eyeball + build the 4 adapted demos (One/Meridian/Ti/NightWatch).
**Ti demo BUILT + self-reviewed 2026-07-19** (`scratchpad/ti-review/`): battery = live top ARC (owner's
recommended layout), HR + steps = the 2 round instrument dials, date native. Shown to owner, awaiting verdict
(open Qs: keep the "68%" under the bolt? nudge the 2 dials apart?). One/Meridian/NightWatch still to demo:
mirror GT's 3 instruments (steps baked→goal-ring slot) + KEEP their extra slots (unread chip / event line /
sunrise-sunset).

### ⚠ RESEARCH FINDING (2026-07-19) — read `docs/COMPLICATION-DATA-SOURCES-RESEARCH.md` before finalising
Owner asked: must the instrument dials be limited to battery + steps? **Answer: NO — every VAKT register
already advertises all 8 types incl. RANGED_VALUE + GOAL_PROGRESS, so the USER can assign ANY health-app gauge
(calories, distance, active minutes, floors, water, HR…) and our needle/ring renders it, auto-scaling to the
provider's own min/max. "Support all" is already true.** Only the *first-install default* is limited to
guaranteed system sources.
**⭐ The research surfaced a real bug in GT — NOW FIXED:** the built-in **`HEART_RATE` system source is only
guaranteed `SHORT_TEXT`** (a number), NOT `RANGED_VALUE` — and some OEMs return an app-shortcut image. So GT's
`HEART_RATE / RANGED_VALUE` default would **not** have reliably rendered the HR needle on first install. This
CORRECTS NF-2 (the issue is the TYPE, not permission). **DONE 2026-07-19: GT's HR default → `HEART_RATE /
SHORT_TEXT`** (heart icon + live "72"; the full needle instrument still renders the moment the user assigns any
ranged HR provider). Apply the same to every other VAKT face's HR register when they're built. Verify on-wrist
what Pixel/Galaxy actually return.

### ✅ UNIVERSAL-GAUGE READOUT — BUILT 2026-07-19 (research §7)
Owner: *"support all RANGED_VALUE / GOAL_PROGRESS without changing the needle design; make the numbers relevant
to the complication."* The needle/ring needed **no change** (clean ticks ⇒ metric-agnostic; gauge auto-scales
via `RANGED_VALUE_MIN/MAX`). Only the centre readout changed, split by where it helps:
- **RANGED_VALUE → `[COMPLICATION.TEXT]`** (provider's own formatted string, with units/decimals): raw `%.0f`
  printed "68" not "68%", and truncated "5.2 km" → "5". Optional field, but needle+fill still carry the
  proportion if omitted ⇒ degrades to a working gauge, never breaks.
- **GOAL_PROGRESS → UNCHANGED** (numeric value + "of TARGET"): both fields are definitional (always present)
  and goal metrics are whole numbers ⇒ already correct, zero blank-risk. Deliberately NOT switched.
⭐ **XSD facts (from `wff-validator.jar`, which BUNDLES `docs.zip` = all 467 XSDs v1–v5 — no need to clone
google/watchface!):** `<Condition>` IS legal inside `<Complication>`, **but `Condition`/`Expression` are
`arithmeticExpressionType` — arithmetic ONLY, no string functions anywhere in the schema ⇒ you CANNOT test
whether an optional complication field is present and fall back.** That's why the split above, not a hybrid.
GT re-baked + **WFF v2 PASS** + byte-compare still clean. ⚠ On-wrist: confirm ranged providers populate `text`;
if any centre is blank, flip that readout back to `%.0f RANGED_VALUE_VALUE` (one-line change).

**Four fixes found by self-review while building the universal demo (all now in GT):**
1. ⭐ **The engraved tick scale was slot CONTENT, so it vanished the instant any provider was assigned** —
   the machined scale only appeared if the user picked "Empty". **FRAME vs CONTENT rule: the plate AND its
   tick scale are permanent hardware → leave them UNTAGGED so they bake into the dial; only the baked icon +
   empty-state needle carry the slot tag.** (`registerHR`/`registerBatterySlot` restructured.) This is a
   general VAKT rule — apply to every face.
2. ⭐ **The needle drew straight THROUGH the centred readout** (it pivots on the hub). RANGED_VALUE now puts
   the value **below the hub**, ONE value only (the provider's TEXT already carries units, so no TITLE row).
   Registers with no needle keep the centred value + title.
3. **Readout was sized for "68", not for a universal gauge** — a 6-char string like "5.2 km"/"72 bpm"
   dominated the dial. Now `max(MIN_SLOT_TEXT, dia*0.15)`; still ellipsis-protected for extremes.
4. **Owner restored the flange red-line arc** (reverses part of NF-1): `arc r221 w4.5 −58°→58° accent
   value:1` — same radius/span/thickness as Ti's flange arc, in GT's red. Drawn **SOLID with no track**, so it
   reads as a painted bezel zone (a tachometer redline), NOT a half-filled fake gauge — which is what NF-1
   actually objected to. Baked = permanent dial art.
⚠ **Light-theme contrast note (pre-existing, all VAKT):** on `t1` the register plate bakes to a mid-grey and
the readout uses `ink` (near-black) — readable but low-contrast. Not changed (role colours have fleet-wide
blast radius); add to the on-wrist check list.

**Ship (unchanged):** after all VAKT faces built+validated → fablecollection push → Dialed submodule
bump → `gen-facepacks.mjs "<ABS ROOT>"` → version bump app/ AND wear/ → next free `dialed-v*` tag →
both APK links → on-wrist checklist. Wave-1's other faces (Aurum/Halftone/Counterform) still pending
after VAKT.
