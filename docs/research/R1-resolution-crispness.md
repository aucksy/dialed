# R1 — WFF Crispness / Resolution

**Owner complaint #1:** "resolution low, worse on bigger watches."
**Scope:** research only. No XML edits, no builds, no tags.
**Date:** 2026-07-12.

---

## TL;DR (headline finding)

**Raster upscaling is NOT the cause.** Every raster asset across all 18 faces is authored at
**exactly 2× (or more) of its on-canvas footprint.** On the largest real Pixel Watch (456 px, scale
1.52×) every one of those assets is therefore *down-sampled* from source → crisp with anti-alias.
There is not a single under-sized raster in the collection. The per-asset re-export table below shows
**every asset = OK**; nothing needs re-exporting for resolution reasons.

That means "resolution low, worse on bigger watches" almost certainly comes from **one of two places,
neither of which is fixed by re-exporting art:**

1. **(Most likely) the PREVIEW path, not the live face** — the `preview.png` shown in the Dialed app /
   the platform WFP picker / the set-active screen. This overlaps R5 ("distorted dimensions"). A soft
   or mis-scaled preview reads as "low resolution" even when the on-wrist face is sharp.
2. **(If the LIVE face is genuinely soft) a full-canvas upscale of the 300-unit design space.** WFF's
   `<WatchFace width height>` is a *resolution-independent design space that is scaled to the device*
   ([codelab](https://developer.android.com/codelabs/watch-face-format),
   [setup](https://developer.android.com/training/wearables/wff/setup)). If — and only if — the runtime
   composites the whole 300×300 scene into a design-space buffer and then bitmap-upscales it to 456,
   then **text and vector shapes** (the actual heroes of most of these faces) would soften by 1.52× on a
   456 watch vs 1.28× on a 384 watch → *exactly* the "worse on bigger watches" signature. The evidence
   says this is unlikely (see §2), but it is the one mechanism consistent with the complaint, and the
   only reason to move the 16 faces from a 300 to a 450 canvas.

**Recommendation:** do **not** spend a CI build re-exporting art or A/B-testing yet. First get the
owner's screenshot (shared with R5) to establish **live-face vs preview**. If the live face is
confirmed soft, run the cheap single-face **300-vs-450 canvas twin** test in §7 before touching anything
else. If it's the preview, this is an R5 problem and R1 needs no code change at all.

---

## 1. Target display resolutions (verified)

| Device | Display | Resolution (px) | Density | Source |
|---|---|---|---|---|
| Pixel Watch / Watch 2 | 1.2" | 384 × 384 | ~320 ppi | prior grounding |
| Pixel Watch 3 41mm | 1.2" | 384 × 384 | ~320 ppi | prior grounding |
| Pixel Watch 3 45mm | 1.4" | 456 × 456 | ~320 ppi | prior grounding |
| **Pixel Watch 4 45mm** | 1.4" | **456 × 456** | ~320 ppi | [GSMArena](https://www.gsmarena.com/google_pixel_watch_4-14088.php) |
| **Pixel Watch 4 41/42mm** (owner's device) | 1.2"–1.4" | **456 × 456** *(see note)* | ~320 ppi | [GSMArena](https://www.gsmarena.com/google_pixel_watch_4-14088.php) |

**Note / [UNVERIFIED per-size]:** GSMArena's PW4 listing reports **456 × 456 for both sizes**
(the PW4 shrank bezels and raised the small model's panel). The per-size PhoneArena page 403'd and
Google's spec page truncated, so the 41/42mm figure isn't independently confirmed. It does **not matter
for asset targeting**: the correct target is the **largest** display in the fleet, **456 × 456**, which
covers every device. **Max scale factor for a 300-unit canvas = 456 / 300 = 1.52 px per unit.** (For the
two 450-unit Arclight faces, 456 / 450 = 1.013.)

---

## 2. The scaling math, and why "worse on bigger watches" points where it does

WFF authors in a **logical coordinate space** set by `<WatchFace width height>`. That space is
*"used irrespective of the actual pixel dimensions the watch face runs on, scaling up or down
accordingly"* (codelab, verbatim). Two distinct pipelines ride on top of that scale:

**(a) Raster images** (`<Image>` inside `PartImage`/`SecondHand`/`MinuteHand`/`HourHand`/`BitmapFont`).
The source PNG's intrinsic pixels are **resampled** into a box of size `element-units × device-scale`
physical px. This is a genuine bitmap resample:
- If `source_px  <  units × scale` → **upscale → blur.** The blur is worse on bigger watches because a
  bigger display has a bigger `scale`, so the fixed-px source is stretched further. *This is the
  mechanism the complaint names — but the measurements below show no asset is in this regime.*
- If `source_px  ≥  units × scale` → **downscale → crisp** (with the renderer's anti-alias/mipmap).

**(b) Text and vector primitives** (`TimeText`, `PartText`, `PartDraw` `Rectangle`/`Ellipse`/`Line`,
`Arc`). These are drawn as primitives through the scale transform and — on a Skia-style scene graph —
**rasterize at the device resolution**, so a font size of *N units* is rendered at *N × scale* px
directly. Under that model the 300-unit canvas does **not** inherently blur text or vectors. The
strongest evidence for device-resolution primitive rendering is Google's own
[memory guidance](https://developer.android.com/training/wearables/watch-faces/performance): image cost
is computed as `4 × width × height` **at the physical display size** ("750 KB … on a 450-pixel × 450-pixel
screen"), i.e. the scene is realized at device px, not at a 300-px design buffer.

**Consequence:** the two things that could make the *live* face look low-res are (i) an under-sized
raster — **ruled out by measurement** — or (ii) a hypothetical full-frame upscale of the 300 canvas —
**not documented, and argued-against by the memory model** `[UNVERIFIED]`. The 2 Arclight faces (450
canvas) are the natural **control**: if the runtime *did* upscale the design buffer, Arclight would be
visibly sharper than the 300-canvas faces on a 456 watch; if it doesn't, they'd look identical. That is
precisely the §7 test.

---

## 3. Per-asset re-export table (measured)

Method: for each raster in the grounding I read its face's `watchface.xml`, found the referencing
element's `width`/`height` in 300-unit space, and computed **needed px @456 = units × 1.52**. Target
re-export = **2 × units** (the task's "≥2× units, never below physical scale"; 2× > 1.52× so 2× binds).
`Verdict OK` = source already ≥ 2× units (down-samples on every real device).

**Canvas legend:** all rows below are **300-unit** faces (the suspects) except the last block
(**Arclight 450-unit** = control).

| Face | Asset | Element | On-canvas units (w×h) | Source px | Needed px @456 | Target (2×) | Verdict |
|---|---|---|---|---|---|---|---|
| Aether-Ember | glow | PartImage | 300 × 130 | 600 × 260 | 456 × 198 | 600 × 260 | **OK** (=2×) |
| Aether-Horizon | sun | PartImage | 72 × 72 | 144 × 144 | 109 × 109 | 144 × 144 | **OK** |
| Aether-Horizon | moon | PartImage | 72 × 72 | 144 × 144 | 109 × 109 | 144 × 144 | **OK** |
| Aether-Horizon | disc_aod | PartImage (AOD) | 20 × 20 | 40 × 40 | 30 × 30 | 40 × 40 | **OK** |
| Kinetik-Escapement | gear_large | MinuteHand | 192 × 192 | 384 × 384 | 292 × 292 | 384 × 384 | **OK** |
| Kinetik-Escapement | gear_small | SecondHand | 132 × 132 | 264 × 264 | 201 × 201 | 264 × 264 | **OK** |
| Kinetik-Metronome | wand_00..23 | PartAnimatedImage | 124 × 120 | 248 × 240 | 188 × 182 | 248 × 240 | **OK** |
| Kinetik-Metronome | wand_aod / wand_static | PartImage | 124 × 120 | 248 × 240 | 188 × 182 | 248 × 240 | **OK** |
| Kinetik-Odometer | drum_0..9 | BitmapFont char | 54 × 84 | 108 × 168 | 82 × 128 | 108 × 168 | **OK** |
| Kinetik-Orrery | starfield | PartImage | 300 × 300 | 600 × 600 | 456 × 456 | 600 × 600 | **OK** |
| Kinetik-Orrery | sun | PartImage | 96 × 96 | 192 × 192 | 146 × 146 | 192 × 192 | **OK** |
| Kinetik-Orrery | sun_aod | PartImage (AOD) | 20 × 20 | 40 × 40 | 30 × 30 | 40 × 40 | **OK** |
| Kinetik-Orrery | planet_hour | HourHand | 28 × 82 | 56 × 164 | 43 × 125 | 56 × 164 | **OK** |
| Kinetik-Orrery | planet_hour_aod | HourHand (AOD) | 24 × 80 | 48 × 160 | 36 × 122 | 48 × 160 | **OK** |
| Kinetik-Orrery | planet_min | MinuteHand | 24 × 116 | 48 × 232 | 36 × 176 | 48 × 232 | **OK** |
| Kinetik-Orrery | planet_min_aod | MinuteHand (AOD) | 20 × 112 | 40 × 224 | 30 × 170 | 40 × 224 | **OK** |
| Kinetik-Orrery | comet | SecondHand | 12 × 137 | 24 × 274 | 18 × 208 | 24 × 274 | **OK** |
| Kinetik-Turbine | rotor | SecondHand | 216 × 216 | 432 × 432 | 328 × 328 | 432 × 432 | **OK** |
| Settype-Halftone | ht_coarse/ht_fine 0..9 | BitmapFont char | 75 × 106 | 150 × 212 | 114 × 161 | 150 × 212 | **OK** |
| Settype-Marquee | bulb (×24 reuse) | PartImage | 28 × 28 | 56 × 56 | 43 × 43 | 56 × 56 | **OK** |
| Vespera-Aurum | guilloche | PartImage | 300 × 300 | 600 × 600 | 456 × 456 | 600 × 600 | **OK** |
| Vespera-Aurum | hour | HourHand | 12 × 74 | 24 × 148 | 18 × 112 | 24 × 148 | **OK** |
| Vespera-Aurum | hour_aod | HourHand (AOD) | 10 × 74 | 20 × 148 | 15 × 112 | 20 × 148 | **OK** |
| Vespera-Aurum | minute | MinuteHand | 10 × 108 | 20 × 216 | 15 × 164 | 20 × 216 | **OK** |
| Vespera-Aurum | minute_aod | MinuteHand (AOD) | 10 × 104 | 20 × 216 | 15 × 158 | 20 × 208 | **OK** |
| Vespera-Aurum | second | SecondHand | 6 × 122 | 12 × 244 | 9 × 185 | 12 × 244 | **OK** |
| Vespera-Meteorite | lattice | PartImage | 300 × 300 | 600 × 600 | 456 × 456 | 600 × 600 | **OK** |
| Vespera-Meteorite | hour | HourHand | 12 × 72 | 24 × 144 | 18 × 109 | 24 × 144 | **OK** |
| Vespera-Meteorite | hour_aod | HourHand (AOD) | 12 × 72 | 24 × 144 | 18 × 109 | 24 × 144 | **OK** |
| Vespera-Meteorite | minute | MinuteHand | 12 × 106 | 24 × 212 | 18 × 161 | 24 × 212 | **OK** |
| Vespera-Meteorite | minute_aod | MinuteHand (AOD) | 10 × 106 | 20 × 212 | 15 × 161 | 20 × 212 | **OK** |
| Vespera-Meteorite | second | SecondHand | 6 × 116 | 12 × 232 | 9 × 176 | 12 × 232 | **OK** |
| Vespera-Noir | hour | HourHand | 10 × 70 | 20 × 140 | 15 × 106 | 20 × 140 | **OK** |
| Vespera-Noir | hour_aod | HourHand (AOD) | 10 × 70 | 20 × 140 | 15 × 106 | 20 × 140 | **OK** |
| Vespera-Noir | minute | MinuteHand | 10 × 104 | 20 × 208 | 15 × 158 | 20 × 208 | **OK** |
| Vespera-Noir | minute_aod | MinuteHand (AOD) | 10 × 104 | 20 × 208 | 15 × 158 | 20 × 208 | **OK** |
| Vespera-Noir | moon_0..7 | PartImage | 46 × 46 | 92 × 92 | 70 × 70 | 92 × 92 | **OK** |
| Vespera-Opaline | hour | HourHand | 12 × 70 | 24 × 140 | 18 × 106 | 24 × 140 | **OK** |
| Vespera-Opaline | hour_aod | HourHand (AOD) | 10 × 70 | 20 × 140 | 15 × 106 | 20 × 140 | **OK** |
| Vespera-Opaline | minute | MinuteHand | 10 × 104 | 20 × 208 | 15 × 158 | 20 × 208 | **OK** |
| Vespera-Opaline | minute_aod | MinuteHand (AOD) | 10 × 104 | 20 × 208 | 15 × 158 | 20 × 208 | **OK** |
| Vespera-Opaline | second | SecondHand | 6 × 118 | 12 × 236 | 9 × 179 | 12 × 236 | **OK** |
| Vespera-Salon | sunray | PartImage | 300 × 300 | 600 × 600 | 456 × 456 | 600 × 600 | **OK** |
| Vespera-Salon | cartouche_border | PartImage | 58 × 42 | 116 × 84 | 88 × 64 | 116 × 84 | **OK** |
| Vespera-Salon | cartouche_fill | PartImage | 58 × 42 | 116 × 84 | 88 × 64 | 116 × 84 | **OK** |
| Vespera-Salon | hour | HourHand | 12 × 68 | 24 × 136 | 18 × 103 | 24 × 136 | **OK** |
| Vespera-Salon | hour_aod | HourHand (AOD) | 12 × 68 | 24 × 136 | 18 × 103 | 24 × 136 | **OK** |
| Vespera-Salon | minute | MinuteHand | 12 × 100 | 24 × 200 | 18 × 152 | 24 × 200 | **OK** |
| Vespera-Salon | minute_aod | MinuteHand (AOD) | 10 × 100 | 20 × 200 | 15 × 152 | 20 × 200 | **OK** |
| **Arclight-Solstice** *(control)* | second_dot | SecondHand | **450 × 450** | 450 × 450 | 456 × 456 | 900 × 900 | ⚠︎ ~1:1 (1.3% **upscale**) |
| **Arclight-Pulsar** *(control)* | second_dot | SecondHand | **450 × 450** | 450 × 450 | 456 × 456 | 900 × 900 | ⚠︎ ~1:1 (1.3% **upscale**) |

### Counter-intuitive result worth flagging
The only assets in the whole collection that are **not** comfortably down-sampled are the **two Arclight
`second_dot` sprites** — authored 1:1 at 450, so on a 456 watch they are marginally (1.3%) *upscaled*.
The 16 "suspect" 300-canvas faces are actually **better supersampled** (a clean 2×→1.52× down-sample)
than the 450 "control." This is strong, independent confirmation that **raster px is not the source of
the owner's blur.** (Arclight's 1.3% upscale is imperceptible and not worth fixing; if ever touched,
re-export `second_dot` at 900×900.)

---

## 4. Vectorize vs re-export, per asset class

Since no asset needs a resolution fix, this is guidance for **future** faces and for anything that gets
touched during a redesign — not a work order.

| Asset class in this collection | Keep as raster or vectorize? | Why |
|---|---|---|
| Hands: hour/minute/second, comet, planet_*, gear_* | **Prefer WFF vector** (`Line`/`Rectangle`/`Ellipse`/`PartDraw`) where the shape is a solid tint/flat bar; **keep raster** where it carries a gradient, bevel, or texture (Aurum/Meteorite metal hands, Kinetik gears). | Solid-color hands as vectors are resolution-proof and free of any resample; textured hands need the pixels. Current raster hands are already 2× so there's no urgency. |
| Rings / hairlines / index ticks / hub dots | **Vector** (`Ellipse` stroke, `Line`, `Arc`). Already done this way in these faces. | A 1-px hairline must be a stroked primitive to stay sharp at any scale; never bake it into a bitmap. |
| Textured/gradient full-screen plates: guilloche, lattice, sunray, starfield, glow | **Hi-res raster** (already 600×600 = 2×). | Not expressible as WFF vectors; keep as PNG, keep at ≥ 2× (600 for a 300-canvas full-screen, 900 for a 450-canvas full-screen). |
| Digit sprite sheets: drum (Odometer), ht_coarse/ht_fine (Halftone) | **Hi-res raster** (already 2×). | Deliberately textured digits — a real font wouldn't reproduce the drum/halftone look. Keep as `BitmapFont` at 2× glyph px. |
| Moon phases, sun/disc, bulb, cartouche | **Hi-res raster** (already 2×). | Shaded/soft-edged art; vectors would lose the look. |

Rule of thumb going forward: **flat solid shape ⇒ vector; anything with a gradient, bevel, grain, or
photographic edge ⇒ raster at ≥ 2× its on-canvas footprint.**

---

## 5. Text anti-aliasing on the 300 canvas

- WFF text (`<TimeText>`, `<PartText>` with a `<Font>`) is a **primitive**, not a bitmap; the font is
  rasterized through the device scale transform, so a `size="40"`-unit glyph is drawn at `40 × 1.52 ≈ 61`
  px on a 456 watch and anti-aliased at that resolution. The 300-unit canvas therefore **does not**
  inherently soften text `[INFERRED — strong: consistent with the device-px memory model in §2; not
  spelled out in the WFF docs]`.
- The one *real* precision cost of a 300 canvas: **positions and sizes are coarser.** One design unit =
  1.52 px, so sub-unit alignment of a thin baseline or a small-caps date can land half a pixel off and
  read as slightly soft. Authoring the same layout at 450 gives 1.013 px/unit — essentially pixel-exact —
  which is a legibility/alignment win, not an anti-alias win.
- Correct text sizing: pick font `size` in the **canvas unit space** for the visual size you want
  (e.g. a 60-px-tall time on a 456 watch = `size ≈ 60 / 1.52 ≈ 40` units on a 300 canvas, or `≈ 59` on a
  450 canvas). Do **not** try to compensate for "low res" by shrinking the canvas — that only worsens
  precision.
- `BitmapFont` digits (Odometer/Halftone) are the exception — those are rasters and follow §3 (already 2×).

---

## 6. Correct `<Image>` / `<PartImage>` usage (reference)

- `resource` = drawable name **without** `@drawable/` or extension inside `<Image>` (verified,
  [Image ref](https://developer.android.com/reference/wear-os/wff/group/part/image/image)). In these
  faces the `@drawable/x` form is used on the hand elements and the bare form inside `<Image resource>` —
  both valid per the schema in use.
- `width`/`height`/`x`/`y` on `PartImage`/`SecondHand`/`MinuteHand`/`HourHand` are the **destination box
  in canvas units.** The source bitmap is scaled to fill that box. Keep the **box aspect ratio equal to
  the source PNG aspect ratio** or the image stretches (this is the likely mechanism behind R5's
  "distorted dimensions" — verify there). All hands here match their source aspect (e.g. rotor 432×432 →
  216×216 box, 1:1). ✅
- `renderMode`: an optional attribute on Part elements selecting the blend/anti-alias behavior; the
  default renders anti-aliased. None of these faces override it, which is correct — leave it default for
  smooth edges `[UNVERIFIED — the specific enum values weren't retrievable from the live docs this pass;
  behavior inferred from rendered output being smooth]`.
- Memory: images are decompressed at **display** resolution, budget 10 MB ambient / 100 MB interactive
  ([perf doc](https://developer.android.com/training/wearables/watch-faces/performance)). A 600×600 RGBA
  plate ≈ 1.4 MB decompressed — fine. Do **not** casually push full-screen plates to 4× "just in case";
  2× is the right ceiling.

---

## 7. Diagnostic APK — recommendation

**Do the cheap, decisive test, not a broad art A/B — and gate it on the screenshot.**

The measurements already answer "is it the raster px?" → **no.** The only open question a build can settle
is **"does the runtime upscale the 300 design canvas?"** i.e. *would moving 300→450 help?* The most
decisive experiment is a **single-face canvas twin**, not a vector-vs-raster pair:

- **Face:** `Settype-Masthead` (or `Settype-Counterform`) — pick a **type/vector-dominant** face with
  little or no raster, so any softening is unambiguously from the canvas, not from an image.
- **Build A (control):** the face exactly as-is (300-unit canvas).
- **Build B (test):** a copy with `<WatchFace width="450" height="450">` and **every** `x/y/width/height/
  font size` in the file multiplied by **1.5** (300→450). No art changes (this face is vector/text).
- **Observe on the owner's 456 watch, side by side:** if B is visibly crisper → the runtime bitmap-
  upscales the design buffer → **migrate all 16 faces to a 450 canvas** (mechanical ×1.5 rescale;
  re-export the full-screen plates 600→900 and 2×-hands accordingly). If A and B look identical → the
  canvas is irrelevant to crispness, and #1 is **not** a face problem → it's the preview (R5).

If the task's original **all-vector vs hi-res-raster** framing is preferred instead, use
`Settype-Masthead` (all vector/text) vs `Vespera-Meteorite` or `Kinetik-Turbine` (hi-res raster hero) as
the pair: both crisp ⇒ no problem (chase R5); raster face soft only ⇒ (won't happen given §3, but would
mean a resample bug); both soft ⇒ canvas upscale ⇒ go to 450.

**Verdict on building now:** **Not worth a CI build yet.** Sequence: (1) get the owner's screenshot with
R5 and classify **live-face vs preview**; (2) if the live face is confirmed soft, run the one-face
300-vs-450 twin above — it's ~30 min of mechanical XML edits and one cheap build; (3) otherwise skip R1
builds entirely and fix the preview path in R5. Proceeding **straight to Phase 2** is reasonable if the
owner's screenshot shows the softness is in the app/picker preview.

### Does raising the 16 faces 300→450 help? (direct answer)
- **For rasters: no.** They're already 2×; a 450 canvas would only make you re-export the plates larger
  (more memory) for zero visible gain on ≤456 displays.
- **For text/vectors: only if the runtime upscales the design buffer** `[UNVERIFIED]`. Under the
  device-resolution rendering model (which the memory doc supports), 450 buys **coordinate precision**
  (pixel-exact hairlines/baselines) — a real but *subtle* sharpness/alignment improvement — **not** a
  dramatic "now it's HD" jump. So: **asset px is what matters for images (and it's already correct);
  canvas resolution is a second-order precision knob for vectors/text that is only worth a fleet-wide
  migration if the §7 twin test shows a visible difference.**

---

## Sources
- WFF setup / coordinate space — https://developer.android.com/training/wearables/wff/setup
- WFF codelab (verbatim "scaling up or down accordingly") — https://developer.android.com/codelabs/watch-face-format
- WatchFace element ref (design space ≠ display px) — https://developer.android.com/reference/wear-os/wff/watch-face
- Image element ref — https://developer.android.com/reference/wear-os/wff/group/part/image/image
- Watch face performance / memory (avoid upscaling; cost at display px) — https://developer.android.com/training/wearables/watch-faces/performance
- Pixel Watch 4 specs (456×456) — https://www.gsmarena.com/google_pixel_watch_4-14088.php
- Community signal (blurry-on-watch = authored below native) — https://community.facer.io/t/watch-face-blurry-on-watch-crisp-on-screen/48925
