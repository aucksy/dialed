# R6 — WFF Canvas Resolution: the universal authoring target

**Question:** 450 is native for a 456 px watch, but would upscale again on a larger display. From
**primary sources**, what is the correct **universal** `<WatchFace width height>` canvas size — and
should the Dialed faces be re-scaled again from 450?

**Verdict (headline):** **Author at 480 × 480.** WFF composites the scene into a buffer sized at the
declared canvas and then scales that buffer to the physical display, so the canvas must be **≥ the
largest real display**. The largest current Wear OS display is **480 × 480** (Samsung's large sizes) —
**not** the 456 R1 assumed (R1 only weighed Pixel Watches). **→ Re-scale all 18 faces 450 → 480**
(uniform ×1.0667). This is done in `dialed-v0.9.0`.

**Date:** 2026-07-12. Supersedes R1's "target = largest fleet display = 456" (R1 §1 undercounted the
fleet max).

---

## 1. The crux — WFF's scaling model (definitive, dual-sourced)

**Answer: the runtime renders the face into a buffer whose pixel dimensions equal the declared
`<WatchFace width height>`, then bitmap-scales that buffer to the physical display.** Therefore the
canvas size **does** determine crispness, and you must author at ≥ the biggest display you want to be
native on. It is **not** the "vectors/text rasterize natively at device resolution regardless of canvas
units" model.

This is settled by combining the docs with our own on-wrist evidence, because Google's prose is
deliberately abstract and, read alone, is ambiguous:

**(a) What Google's docs say (design-space framing).**
- WFF setup: *"The `height` and `width` define the extent of the coordinate space for use in your watch
  face, and the watch face is scaled to fit the device it is being used on; `height` and `width` don't
  represent actual pixels."*
  ([setup](https://developer.android.com/training/wearables/wff/setup))
- WatchFace element ref: *"Width of the visual screen … in pixels. All child geometric elements … have
  their sizes and positions set relative to the visual screen size. **Note that the visual screen might
  be a different size than the display resolution of a physical Wear OS device.**"*
  ([watch-face ref](https://developer.android.com/reference/wear-os/wff/watch-face))
- Codelab: the coordinate space is *"used irrespective of the actual pixel dimensions the watch face
  runs on, scaling up or down accordingly."*
  ([codelab](https://developer.android.com/codelabs/watch-face-format))

  Read literally, "don't represent actual pixels / scaling up or down accordingly" could mean *either*
  model. It tells you the canvas is a coordinate space; it does **not** tell you whether the scene is
  re-rasterized at device px or up-scaled from a canvas-sized buffer.

**(b) What our own hardware proved (the decisive datum).** In Phase 2 (shipped `dialed-v0.8.0`) the
owner A/B-tested on a **Pixel Watch 4 (456 px)**: the 16 faces authored on a **300-unit** canvas were
visibly **soft**, while the 2 Arclight faces authored on a **450-unit** canvas were **crisp** — same
device, same session, same fonts. Every raster asset in all 18 faces was already ≥ 2× its on-canvas
footprint (R1 §3), so raster resampling is ruled out; the softened elements were **text and vector
primitives**. **If WFF rasterized text/vectors natively at device resolution, the 300 face would have
been just as crisp as the 450 face. It wasn't.** The only model consistent with that observation is:
the whole scene is drawn into a **canvas-sized buffer** (300²), then up-scaled ~1.52× to 456 → uniform
softening of text + vectors. The 450 face lands at 456/450 = 1.013× (essentially native) → crisp. This
is a direct, on-device, controlled experiment on the exact runtime we ship to — it outranks the docs'
abstract wording.

**(c) Corroborating doc signal.** Google's performance guidance computes image memory at the **physical
display size** ("*750 KB … on a 450-pixel × 450-pixel screen*") and lists a **resize-to-fit-screen** step
on the decompressed bitmap
([performance](https://developer.android.com/training/wearables/watch-faces/performance)). That is
consistent with a display-sized compositor pass — i.e. the scene is realized at real pixels via a scale
step, exactly the buffer-then-scale pipeline the A/B revealed.

> **One-line rule:** *the `<WatchFace>` canvas is the render buffer's pixel size; make it ≥ the largest
> display in the fleet so that display is 1:1 and every smaller display is a crisp down-sample.*

---

## 2. Google's recommended / reference authoring resolution

There is **no single mandated number**, but there is a clear canonical reference:

- **Google's own current WFF samples declare `<WatchFace width="450" height="450">`** — the primary
  `watchface_basic.xml` in the setup guide and the DWF/samples use 450
  ([setup](https://developer.android.com/training/wearables/wff/setup)). 450 is the *de-facto reference
  size*, matching the mid-2021 Galaxy Watch 4 44mm panel that Wear OS 3 launched on.
- The multi-shape example shows **both 300 and 450** are legal and that you can even ship *different*
  canvas files per configuration
  (`<WatchFace shape="CIRCLE" width="450" .../>`) — confirming the canvas is a free authoring choice,
  not a fixed constant.
- Google's Wear design guidance sizes round screens as **192 dp → 240+ dp**; at xhdpi (density 2.0)
  that is **384 px → 480+ px**, so 480 is the top of Google's own stated design envelope
  ([screen sizes](https://developer.android.com/design/ui/wear/guides/m2-5/foundations/screen-sizes)).

**Takeaway:** 450 is Google's *reference/back-compat* size, not a crispness ceiling. Authoring **above**
the reference (up to the real fleet max) is legal and is the correct move for a marketplace that must
look native on the biggest panels.

---

## 3. Actual pixel resolutions of current Wear OS round watches (verified)

Per-size figures from Wikipedia spec tables / manufacturer pages (GSMArena collapses Samsung multi-size
lines to the top size only; it and Wikipedia also mis-list the original Pixel Watch/PW2 as 450 — that is
mathematically impossible at 1.2"/320 ppi and is really **384**).

| Watch | Size | Resolution (px) | Source |
|---|---|---|---|
| Pixel Watch (1st gen) | 41 mm 1.2" | **384 × 384** | [AndroidCentral](https://www.androidcentral.com/wearables/google-pixel-watch-3) |
| Pixel Watch 2 | 41 mm 1.2" | **384 × 384** | [PhoneArena](https://www.phonearena.com/phones/Google-Pixel-Watch-2_id12185) |
| Pixel Watch 3 | 41 mm 1.27" | **408 × 408** | [AndroidCentral](https://www.androidcentral.com/wearables/google-pixel-watch-3) |
| Pixel Watch 3 | 45 mm 1.43" | **456 × 456** | [GSMArena](https://www.gsmarena.com/google_pixel_watch_3-13253.php) |
| **Pixel Watch 4 (owner's)** | 41/42 mm | **408 × 408** *(derived; see note)* | [Google support](https://support.google.com/googlepixelwatch/answer/12651869) |
| **Pixel Watch 4** | 45 mm 1.4" | **456 × 456** | [GSMArena](https://www.gsmarena.com/google_pixel_watch_4-14088.php) |
| Galaxy Watch 4 | 40 mm | 396 × 396 | [Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_4) |
| Galaxy Watch 4 | 44 mm | 450 × 450 | [Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_4) |
| Galaxy Watch 4 Classic | 42 / 46 mm | 396 / 450 | [Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_4) |
| Galaxy Watch 5 | 40 / 44 mm | 396 / 450 | [Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_5) |
| Galaxy Watch 5 Pro | 45 mm 1.4" | 450 × 450 | [GSMArena](https://www.gsmarena.com/samsung_galaxy_watch5_pro-11749.php) |
| Galaxy Watch 6 | 40 / 44 mm | 432 / **480** | [Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_6) |
| Galaxy Watch 6 Classic | 43 / 47 mm | 432 / **480** | [Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_6) |
| Galaxy Watch 7 | 40 / 44 mm | 432 / **480** | [Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_7) |
| **Galaxy Watch Ultra (2024/2025)** | 47 mm 1.5" | **480 × 480** | [GSMArena](https://www.gsmarena.com/samsung_galaxy_watch_ultra-13127.php) |
| Galaxy Watch 8 | 40 / 44 mm | 438 / **480** | [Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_8) |
| Galaxy Watch 8 Classic | 46 mm | 438 × 438 | [Wikipedia](https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_8) |
| TicWatch Pro 5 / Enterprise / Enduro | 1.43" | 466 × 466 | [Notebookcheck](https://www.notebookcheck.net/Mobvoi-TicWatch-Pro-5-Smartwatch-Review-Does-a-lot-and-lasts-just-as-long.738093.0.html) |
| OnePlus Watch 2 / 2R | 46 mm 1.43" | 466 × 466 | [GSMArena](https://www.gsmarena.com/oneplus_watch_2r-13209.php) |
| OnePlus Watch 3 | 43 / 46 mm | 466 × 466 | [OnePlus](https://www.oneplus.com/us/oneplus-watch-3/specs) |

**Pixel Watch 4 41/42 mm note:** no spec DB lists it separately; Google gives only "320 ppi". PW4 reuses
PW3's two panel sizes (1.27"/1.43", 320 ppi) → 41 mm ≈ **408 × 408**. Derived, not directly cited. It is
**≤ 456**, so it does not affect the max-target decision either way.

### Resolution clusters
| px | # | Watches |
|---|---|---|
| 384 | 2 | Pixel Watch 1, Pixel Watch 2 |
| 396 | 3 | GW4 40, GW4 Classic 42, GW5 40 |
| 408 | 2 | Pixel Watch 3 41, Pixel Watch 4 41* |
| 432 | 3 | GW6 40, GW6 Classic 43, GW7 40 |
| 438 | 2 | GW8 40, GW8 Classic 46 |
| 450 | 4 | GW4 44, GW4 Classic 46, GW5 44, GW5 Pro 45 |
| 456 | 2 | Pixel Watch 3 45, **Pixel Watch 4 45** |
| 466 | 6 | TicWatch Pro 5 (+Ent/Enduro), OnePlus Watch 2 / 2R / 3 (46 & 43) |
| **480** | **5** | **GW6 44, GW6 Classic 47, GW7 44, GW Ultra 47, GW8 44** |

**Maximum = 480 × 480.** No shipping Wear OS watch exceeds it. The two densest clusters at the top are
**466 (6 non-Samsung devices)** and **480 (5 Samsung large sizes)** — and Samsung is by far the largest
Wear OS installed base, so the 480 cohort is commercially the one to get native. A **450** canvas
up-scales **480/450 = 1.067×** on that whole cohort and **466/450 = 1.036×** on the TicWatch/OnePlus
cohort — mild, but exactly the "worse on bigger watches" direction the owner complained about, just
smaller than the 1.52× that triggered it.

---

## 4. Is authoring larger than the display (downscaling) safe & crisp? (yes)

**Yes — down-sampling is always crisp.** Scaling a larger source buffer *down* to a smaller display is a
minifying resample with anti-alias/area averaging; it never blurs (blur is a *magnifying* artifact). So a
480 canvas is:
- **Native** on the 480 cohort (5 Samsung larges) — 1:1, sharpest possible.
- A **crisp down-sample** on everything smaller: 480→466 (0.97×), 480→**456** (0.95×, the owner's PW4),
  480→450 (0.94×), 480→432 (0.90×), 480→408 (0.85×), 480→396 (0.825×), 480→384 (0.80×). Every one is a
  minify → crisp.

**Downsides of authoring at 480 vs 450 — all negligible here:**
- **Render-buffer memory:** 480² × 4 B ≈ 921 KB vs 450² ≈ 810 KB → **+111 KB** (+14%). The budget is
  **10 MB ambient / 100 MB interactive** ([performance](https://developer.android.com/training/wearables/watch-faces/performance)).
  Immaterial.
- **Raster source assets:** unchanged. Our full-screen plates are 600 px source (R1 §3); a full-screen
  box on a 480 canvas is 480 units, and 600 ≥ 480, so plates still **down-sample** → **no PNG re-export
  needed** (same as the 300→450 pass). Hands/glyph sprites are all ≥ 2× their (now slightly larger) boxes
  and remain down-samples. So `rescale-canvas.mjs` (geometry only) is sufficient again.
- **Coordinate precision:** *improves.* 480 gives 1.0 px/unit on the 480 cohort and finer sub-pixel
  landing everywhere; no precision is lost relative to 450.
- **Rounding drift from the ×1.0667 factor:** each integer coordinate is independently rounded in
  480-space → ≤ 0.5 px error, sub-pixel, imperceptible (same discipline as the ×1.5 pass). WFF's
  integer-geometry rule is honored by the tool (rounds x/y/w/h/radius/center/start/end/dashIntervals;
  keeps `size`/`thickness` float).

**Why not go even higher than 480 (future-proof to, say, 512)?** No shipping Wear OS watch is above 480,
and 480 has been the platform ceiling since the very first 480×480 Android Wear panel (LG Nemo, 2015) —
a decade with no round Wear OS device exceeding it. Over-authoring buys nothing today (everything would
just down-sample from a bigger buffer) while paying memory + drift. **Track the fleet max; if a >480
round Wear OS watch ever ships, revisit — but 480 is correct now and for the foreseeable roadmap.**

---

## 5. Is there a resolution-independent authoring approach in WFF? (no)

Not in the "author once, always pixel-native" sense. WFF's coordinate space is *relative* (positions
scale), which makes a single canvas **fit** any display, but — per §1 — the scene is composited at the
canvas's pixel size and then scaled, so a face is only **pixel-native at exactly one resolution (its
canvas size)** and up/down-sampled everywhere else. The only levers are:
1. **Pick the canvas = the fleet max** so the sole up-scale case (native > display) never happens →
   every real device is native-or-down-sampled. *(This is what we do: 480.)*
2. **Ship multiple canvas files** via `<WatchFace shape/width/height file=…>` variants (the multi-shape
   mechanism) — e.g. a 480 and a 384 variant. Overkill for round-only faces where a single 480 canvas
   already down-samples cleanly to every smaller round panel; adds packaging + validation surface for no
   visible gain. **Not pursued.**
3. **Keep flat/solid shapes as WFF vectors** (`Line`/`Rectangle`/`Ellipse`/`Arc`/`PartDraw`) rather than
   rasters — vectors re-rasterize with the buffer, so they stay as sharp as the buffer allows at any
   canvas; but they are still bounded by the buffer's resolution, so this complements, not replaces,
   choosing a large canvas. (Our faces already vector their hairlines/rings/ticks — R1 §4.)

So: **"resolution-independent layout, resolution-dependent crispness."** The correct universal target is
therefore a single number = the fleet max = **480**.

---

## 6. Definitive recommendation

- **Canonical `<WatchFace>` canvas: `width="480" height="480"`.** Native on the largest current Wear OS
  panels (Galaxy Watch 6/7/8 44 mm, Watch 6 Classic 47 mm, Watch Ultra), crisp down-sample on all
  smaller round watches including the owner's Pixel Watch 4 (456). Author **all future faces at 480**.
- **Re-scale the Dialed collection 450 → 480? → YES.** Uniform ×(480/450 = 1.0666…) geometric zoom via
  `faces/tools/rescale-canvas.mjs 1.0666666666666667`, applied to all **18** bundled faces (the 16
  formerly-300 faces *and* the 2 Arclight faces are all at 450 now, so all 18 scale from 450). No raster
  re-export (plates 600 px ≥ 480). All prior fixes preserved by the uniform zoom: P3 complication insets
  (scale proportionally, stay inside the round safe zone), P4 `Sweep frequency=15` (unitless), P4b
  Metronome sprite (raster, native px unchanged), pivots/angles/alpha/colors/em-letterSpacing untouched.
- **Why this is worth a third canvas migration (300→450→480):** the owner's own device (456) is already
  crisp at 450, so this changes nothing *for the owner* — but Dialed is a **marketplace**, and its
  largest-panel buyers (the entire 480 Samsung cohort — the biggest slice of the Wear OS base) currently
  get a ~6.7% up-scale. 480 makes them pixel-native. Cost is one mechanical rescale + one CI build +
  ~0.1 MB RAM. Clear net-positive; it closes issue #1 for *every* watch, not just 456-and-below.

**Correction to R1:** R1 §1 named the fleet max as **456** and would have stopped at 450 — but it only
tabulated Pixel Watches. The Samsung 480 cohort makes the true max **480**; this doc supersedes that
figure. Everything else in R1 (all rasters ≥ 2×; buffer-upscale is the softness mechanism) stands and is
in fact *confirmed* by the Phase-2 on-wrist result.

---

## Sources
- WFF setup / coordinate space + 450 sample — https://developer.android.com/training/wearables/wff/setup
- WatchFace element ref ("visual screen might be a different size than the display resolution") — https://developer.android.com/reference/wear-os/wff/watch-face
- WFF codelab ("scaling up or down accordingly") — https://developer.android.com/codelabs/watch-face-format
- Watch face performance / memory (cost at display px; resize-to-fit) — https://developer.android.com/training/wearables/watch-faces/performance
- Wear screen sizes 192–240+ dp — https://developer.android.com/design/ui/wear/guides/m2-5/foundations/screen-sizes
- Pixel Watch 4 456 — https://www.gsmarena.com/google_pixel_watch_4-14088.php
- Pixel Watch 3 456 / 408 — https://www.gsmarena.com/google_pixel_watch_3-13253.php · https://www.androidcentral.com/wearables/google-pixel-watch-3
- Galaxy Watch 6 / 7 / 8 (per-size 432/438/480) — https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_6 · https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_7 · https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_8
- Galaxy Watch Ultra 480 — https://www.gsmarena.com/samsung_galaxy_watch_ultra-13127.php
- Galaxy Watch 4 / 5 (per-size 396/450) — https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_4 · https://en.wikipedia.org/wiki/Samsung_Galaxy_Watch_5
- TicWatch Pro 5 466 — https://www.notebookcheck.net/Mobvoi-TicWatch-Pro-5-Smartwatch-Review-Does-a-lot-and-lasts-just-as-long.738093.0.html
- OnePlus Watch 2R / 3 466 — https://www.gsmarena.com/oneplus_watch_2r-13209.php · https://www.oneplus.com/us/oneplus-watch-3/specs
- Phase-2 on-wrist A/B (300 soft vs 450 crisp on 456 px) — `docs/IMPROVEMENTS-PLAN.md` Progress row `2 / v0.8.0`; `docs/research/R1-resolution-crispness.md` §2–3
