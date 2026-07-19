# Complication & Sub-dial Audit — all 43 bundled faces

**Date:** 2026-07-19 · **Auditor:** Fable 5 (assessment only; fixes executed per `COMPLICATION-FIX-PLAN.md`)
**Scope:** every `ComplicationSlot` on every bundled face (43 faces, 96 slots — every face has ≥1 slot), plus the two Settype typography bugs the owner photographed.
**Evidence tags:** `[WRIST]` = owner screenshot 2026-07-19 · `[XML]` = verified in file at the cited line · `[SWEEP]` = reported by the read-only inventory sweep (line numbers spot-checked 7/7 correct) · `[DOCS]` = official Google/androidx source.

Paths below are relative to the `faces/` submodule (fablecollection). Family A = hand-authored (18 faces, WFF v2, 480 canvas; edit XML directly). Collection 3 = generated (25 faces, 450 canvas; **never hand-edit** — fix `collection3-tools/spec/*` or `collection3-tools/gen/*` and re-bake).

---

## 0. Platform facts that decide everything (researched + on-wrist-proven)

1. **A slot with no active data renders NOTHING designable.** If no provider is chosen and the default is `EMPTY/EMPTY`, the system sends *NotConfigured* data — WFF has no block for that state, so the face draws nothing in the slot. The `<Complication type="EMPTY">` block renders **only** when the user explicitly picks "Empty" in the editor. Proven on-wrist by the VAKT blank circles `[WRIST]` + androidx `Data.kt` / WFF reference `[DOCS]`.
2. **Safe first-install system providers** (deliver data before any permission grant, per androidx `SystemDataSources`): `WATCH_BATTERY, DATE, TIME_AND_DATE, STEP_COUNT, WORLD_CLOCK, APP_SHORTCUT, UNREAD_NOTIFICATION_COUNT, DAY_OF_WEEK, DAY_AND_DATE`. **Not safe** (blank until the user grants complication permission): `NEXT_EVENT`, `FAVORITE_CONTACT`. **Undocumented either way** (treat as may-be-blank): `SUNRISE_SUNSET`, `HEART_RATE` (HR additionally needs the body-sensor permission; HR default is legal only on WFF v2). `[DOCS]`
3. **The system never draws its own box over a slot in normal wear** — the bounding outline exists only in the editor. Any visible backing rectangle is authored by our own XML. `[DOCS]`
4. Guaranteed types per safe source: WATCH_BATTERY → ST/LT/MI/**RV**; STEP_COUNT → ST (+GP on v2); DATE/DAY_AND_DATE/DAY_OF_WEEK/TIME_AND_DATE/WORLD_CLOCK/SUNRISE_SUNSET/HEART_RATE → ST only; NEXT_EVENT → ST/LT; APP_SHORTCUT → SI/LT; FAVORITE_CONTACT → SI. The only *safe* source that can drive a RANGED_VALUE gauge is **WATCH_BATTERY**. `[DOCS]`
5. Store-pushed faces may set `primaryProvider` to a data source **bundled in the same APK** (guaranteed present) — recorded as a future option, not used in the current plan. `[DOCS]`
6. `<Editable value="true"/>` is present in every face's `watch_face_info.xml` — assignability is not the problem anywhere. `[XML]`

---

## 1. Findings, ranked

### S1 — Owner-visible today (screenshot-anchored)

**F1 · VAKT hero registers are blank black holes on first install.** `[WRIST]` `[XML]`
All five VAKT faces give their machined registers `DefaultProviderPolicy defaultSystemProvider="EMPTY"` — e.g. Vakt-GT slot0 at `Vakt-GT/app/src/main/res/raw/watchface.xml:189`, slot1 at `:1106`. The beautiful empty-state art (register plate + ticking sub-seconds needle) exists and is correct (`:1029–1102`) — but per fact 0.1 it never shows on first install; the wearer sees the baked outline ring with a black hole inside (owner photo #2). Affected slots: Vakt-GT s0+s1, Vakt-One s0+s1, Vakt-Meridian s0, Vakt-NightWatch s0, Vakt-Ti s0+s1. The v0.22.0 frame+content refactor was right; the **defaults** are what's wrong.

**F2 · Aurum's slot is an alien rectangle patch on the dial.** `[WRIST]` `[XML]`
The generated SHORT_TEXT template draws a solid `Rectangle` patch behind the text — `Aurum-Guilloche/...watchface.xml:356–360` (light theme `#dcd9d2`) and `:370–374` (theme t1 `#101010` — the exact black box in owner photo #1), text at size **16** (below the 20-unit legibility floor). The same `patch_t*` template ships in **19 of the 20 non-VAKT Collection-3 faces** (all except Meridian-Roman). On some dials the patch color blends in; on Aurum-Guilloche's green guilloché it reads as a sticker. Also: all 5 Aurum faces gate the whole slot on the Date toggle (`[CONFIGURATION.date] ? 255 : 0`, `Aurum-Guilloche:350`) — turning Date off silently hides *any* assigned complication. And Aurum-Guilloche defaults to `EMPTY/EMPTY` (`:346`) → out of the box the window shows nothing at all.

**F3 · Settype-Halftone: hero time digits render as clipped fragments.** `[WRIST]` `[XML]`
Time is drawn with `BitmapFont` glyphs declared 121×170 at `size="169.6"` inside `TimeText` (`Settype-Halftone/...watchface.xml:26–51, 66–83`). On-wrist the digits appear as cropped chunks (photo #3) — consistent with the glyph PNGs' real pixel dimensions not matching the declared 121×170 cell (renderer crops instead of scales). Must be verified against the actual PNGs. Secondary suspects: non-integer `size="169.6"`, `format="hh"`, and `hourFormat="SYNC_TO_DEVICE"` (an attribute our 2026-07-17 XSD sweep flagged as unverified).

**F4 · Settype-Counterform: padded hour digit hangs half off-screen.** `[WRIST]` `[XML]`
The 312-unit hour numeral group sits at x=−22, y=−67 (`Settype-Counterform/...watchface.xml:39`) and renders **zero-padded** `[HOUR_1_12_Z]` (`:44`) — at 7 o'clock the "0" is mostly off-canvas and the face reads "J7" (photo #4). Oversized-bleed is this face's stated design intent (`:6–11`), but the current tuning fails on-wrist per the owner. AOD twins (`:67–92`) mirror the same geometry.

### S2 — "The design falls apart" when data is assigned (the flat-disc class)

**F5 · Meridian-PetiteSeconde: assigning data patches a flat grey disc over the machined small-seconds register.** `[XML]`
The ticking small-seconds sub-dial is **baked into the main scene** (`AnalogClock x=173 y=260 104×104` with `SecondHand`+`Tick`, `:37–41`, repeated per theme) at exactly slot0's bounds (`:270`). Slot0's RANGED_VALUE block then draws an opaque `Ellipse` patch `#bbb9b3` (`:280–284`) with an **unclamped, unguarded** arc (`:287`). Assign anything → grey disc covers the instrument while the needle keeps ticking beneath. This is the pre-VAKT defect pattern, verbatim.

**F6 · Terra-Altimeter: both machined registers have live baked needles + disc-patch slots on top.** `[XML]`
Steps needle baked at `:49–52` (`[STEP_COUNT]` mod 10 000 → 360°), battery needle at `:53–56`, both in the main scene at slot0/slot1 bounds; the slots' RV blocks patch discs over them with unclamped arcs (`:428/:441`, `:579/:592` per sweep). Same defect class as F5.

**F7 · Terra-Compass s1 sits on the top instrument register** (default `EMPTY/EMPTY`, RV renders numeric text only) — same family, milder; verify and treat with F5/F6. `[SWEEP]`

### S3 — First-install quality across the fleet (blank-risk defaults & duplicates)

**F8 · Unreliable defaults on prominent slots.** `[XML]`/`[SWEEP]`
- `HEART_RATE` defaults (blank until body-sensor permission): Aether-Ember s1, Kinetik-Escapement s1, Kinetik-Metronome s1, Vespera-Meteorite s1, Vespera-Aurum s2.
- `NEXT_EVENT` defaults (blank until complication permission): Arclight-Pulsar s3, Arclight-Solstice s2, Kinetik-Metronome s2, Settype-Marquee s1, Settype-Masthead s2, Halo-Ledger s0, Halo-Stack s2, Meridian-Calendrier s0 (**its only slot**), Terra-MeridianLine s2, Vakt-Meridian s1.
- `SUNRISE_SUNSET` defaults (undocumented-safe — owner must verify on-wrist once): Aether-Horizon s2+s3, Kinetik-Orrery s2, Vespera-Opaline s2, Aurum-Soir s0, Terra-Field24 s1+s2, Terra-MeridianLine s0+s1, Terra-Solstice s0+s1, Vakt-NightWatch s1.
- Remaining `EMPTY/EMPTY` defaults on visible slots (nothing out of the box): Aurum-Guilloche s0, Meridian-Classic s0+s1 (**both** slots — face installs with zero live data), Halo-Orbit s0+s1 (both data rings empty; only the faint track shows), Halo-Quadrant s0, Meridian-PetiteSeconde s0, Terra-Altimeter s0+s1, Terra-Compass s1, Vakt-One s1.

**F9 · Duplicate same-provider pairs — two windows, same value:** Aether-Horizon s2+s3, Terra-Field24 s1+s2, Terra-MeridianLine s0+s1, Terra-Solstice s0+s1, Vakt-NightWatch s1+s2 (both `SUNRISE_SUNSET` at `:1130`/`:1377` — the "sunrise" and "sunset" windows can never differ: the provider only ever reports the *next* event). `[XML]`/`[SWEEP]`

### S4 — Correctness under edge conditions

**F10 · Unclamped / unguarded gauges** (out-of-range endAngle is undefined behaviour; ÷0 → NaN → garbage): Halo-Orbit both rings (`:504`, `:524` — `((V−MIN)/(MAX−MIN))*360`, no clamp, no guard), Meridian-PetiteSeconde (`:287`), Terra-Altimeter (×2). Vespera-Aurum clamps the *value* but not the fraction and has **no ÷0 guard** on `(MAX−MIN)` (`:104`) nor on GP `TARGET` (`:126`), and no >100 % overflow lap. VAKT's math is the gold standard to copy (clamp 0..1 + `T>0?T:1` + gated overflow lap). `[XML]`

**F11 · EMPTY advertised but no EMPTY block:** every slot on all 20 non-VAKT Collection-3 faces, plus Arclight-Pulsar (4 slots) and Arclight-Solstice (5). User picks "Empty" → bare hole. For plain text rows a deliberate blank is acceptable *content*, but the block should exist (self-closing) as intent; for register-style slots (Halo-Orbit rings, Halo-Quadrant s0) real empty-state art is warranted. `[SWEEP]`

### S5 — Z-order & polish

**F12 · Hands draw UNDER complication content on all 20 non-VAKT Collection-3 faces** (hands live in the per-theme groups before the slots; only the AOD clock follows) — assigned data covers the hour/minute hands as they pass. VAKT already does the correct scene split (slots → hands after). Kinetik-Orrery (Family A) has the same order issue (planet-hands before slots). Rarely seen, but wrong. `[SWEEP]`

**F13 · Legibility misses in the generated small slots:** value text at 16 (floor is 20) across the C3 `patch` template; Aurum windows 50×24 are value-only-sized — any title row would overflow (fit must be checked arithmetically during the restyle, polish-skill §7). `[XML]`

### What is already right (don't touch)

- VAKT's frame+content system: EMPTY-state plates + ticking `[SECOND]*6` needles inside EMPTY blocks, all 8 types styled per slot, clamped+guarded gauges with overflow laps, hands drawn after slots. The defect is only the *defaults* (F1).
- Family A: no hardcoded labels anywhere; provider icon `[COMPLICATION.MONOCHROMATIC_IMAGE]` used as the swap-safe identifier; Vespera-Aurum's per-type instrument styling is the Family-A reference (needs only F8/F10 touches).
- `Editable=true` fleet-wide; slot geometry integers; bounding shapes present.

---

## 2. New lessons to fold into the WFF skill + Design Guidelines (after Wave-1 on-wrist confirmation)

- **L1:** `EMPTY/EMPTY` default ≠ "EMPTY art shows on install." NotConfigured renders NO block; EMPTY art appears only when the user explicitly selects Empty. The only guaranteed-visible first-install state is a **safe** system default styled by the face. (On-wrist proof: VAKT registers, photo 2026-07-19.)
- **L2:** The safe/unsafe/undocumented provider lists in §0.2, and "only WATCH_BATTERY safely drives a RANGED_VALUE gauge."
- **L3:** The system draws no slot chrome in normal wear — a visible box is always our own XML (editor outline ≠ runtime).
- **L4:** A store-pushed face can bundle its own data source and name it `primaryProvider` (guaranteed present) — the escape hatch if safe system defaults ever aren't enough.
