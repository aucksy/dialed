# R2 — WFF Complication Slot Alignment on Round Displays

**Owner issue #8:** "complication alignment bad on most faces."
**Scope:** RESEARCH ONLY. No face XML is edited here. This maps to Phase 3.
**Device under test:** Pixel Watch 4 (owner's is the small variant; see §4).

---

## 0. Headline finding

The misalignment is **not** a bounds‑vs‑paint offset. In every face the
`<BoundingBox>` is `x=0 y=0 width=slotW height=slotH`, i.e. the tap/crop region is
exactly the slot rectangle and the drawn text/decoration is centred inside it — those
are correctly co‑registered.

The real defect is **round‑display geometry**: several 300‑unit faces place wide
top/bottom "bar" complications and offset bottom‑corner complications whose rectangle
corners land **at or beyond the round display's visible circle** (radius = 150 on the
300 canvas). On a round Pixel Watch those corners are **clipped by the bezel** or hug it
with zero margin, which reads as "alignment is bad." Critically, the marketplace/preview
image (`preview.png`, a 450×450 **square**) shows the full square canvas with nothing
clipped, so the bug is invisible in preview and only appears on the round watch.

Two fixes: (a) pull the offending slots inward so all four box corners sit inside a
safe circle; (b) for rim‑hugging or circular slots, switch `BoundingBox` → `BoundingOval`
so the clipped corners simply don't exist. The Arclight (450‑canvas, oval bounds) and
Vespera‑Opaline/Aurum/Salon faces are already correct and serve as templates.

---

## 1. `<ComplicationSlot>` geometry, from primary WFF docs

Sources:
[ComplicationSlot reference](https://developer.android.com/reference/wear-os/wff/complication/complication-slot),
[Complication Bounding Areas reference](https://developer.android.com/reference/wear-os/wff/complication/bounding),
[DefaultProviderPolicy reference](https://developer.android.com/reference/wear-os/wff/complication/default-provider-policy),
[Provide useful data through complications (guide)](https://developer.android.com/training/wearables/wff/complications).

### 1.1 Attributes
| Attribute | Type | Notes |
|---|---|---|
| `slotId` | integer | **Required**, unique per face. |
| `x`, `y`, `width`, `height` | integer | **Required.** "Integers that specify the element's size and position." Position of the slot's top‑left and its box size. |
| `name` | string | Optional internal id. |
| `displayName` | string (res id) | Optional; shown in the watch‑face editor. |
| `supportedTypes` | enum list (space‑separated) | **Required.** Valid: `SHORT_TEXT`, `LONG_TEXT`, `MONOCHROMATIC_IMAGE`, `SMALL_IMAGE`, `PHOTO_IMAGE`, `RANGED_VALUE`, `EMPTY`. (The docs list these; our faces also use `GOAL_PROGRESS`, accepted by the editor.) |
| `isCustomizable` | boolean | Optional, default `TRUE` — user may change the provider. |
| `pivotX/pivotY/angle/alpha/tintColor/scaleX/scaleY/blendMode` | — | Optional transforms on the slot. |

A face may declare **at most 8** `ComplicationSlot`s total. A slot must contain exactly
one **bounding area** and at least one `<Complication>` per supported type.

### 1.2 Coordinate space (confirmed)
- **Slot `x/y/width/height` are in the WatchFace canvas coordinate space** — the same
  logical units as `<WatchFace width=.. height=..>`. For 16 of our faces that is the
  **300×300** canvas; the two Arclight faces use **450×450**.
  *Confirmed empirically:* Kinetik‑Turbine (canvas 300) has `x="75" width="150"`, i.e.
  `75+150 = 225` and `300−225 = 75` → symmetric about the 150 centre line.
- **`BoundingBox`/`BoundingOval` `x/y` are relative to the slot's own top‑left**, not the
  canvas. *Confirmed empirically:* every face uses `<BoundingBox x="0" y="0"
  width="{slotW}" height="{slotH}">`, i.e. a full‑slot box anchored at the slot origin.
  WFF units scale to the physical display, so a 300‑unit canvas on a 456 px watch renders
  at 1 unit = 1.52 px (scale is irrelevant to the alignment analysis below — see §2.4).

### 1.3 Bounding areas — the dual role that matters here
Per the bounding reference, the bounding area is **both**:
1. the **hit/selection region** — "the region where the user can select the complication
   from the watch face," and
2. the **crop region** — "**Any content outside of the bounding area is cropped.**"

`outlinePadding` (float, default 0) only enlarges the **editor** outline; it does not move
or grow the rendered region. `BoundingArc` also exists (centerX/centerY/width/height/
thickness/startAngle/endAngle/direction) for arc‑shaped ranged complications.

### 1.4 `DefaultProviderPolicy`
`defaultSystemProvider` (a `SystemDataSources` constant, e.g. `STEP_COUNT`,
`WATCH_BATTERY`, `HEART_RATE`, `SUNRISE_SUNSET`, `NEXT_EVENT`, `DATE`,
`UNREAD_NOTIFICATION_COUNT`) is the fallback data source; `defaultSystemProviderType`
sets the type it supplies. This is orthogonal to alignment (it picks *what* shows, not
*where*), and our faces set it sensibly.

---

## 2. Diagnosis from the actual faces

I read the `<ComplicationSlot>` blocks of 14 faces. The pattern is uniform: `BoundingBox
= 0,0,slotW,slotH` (or a full‑slot `BoundingOval`), with a centred `<PartText>` / circular
`<PartDraw>` inside. **Bounds and paint are aligned.** The problem is purely *where the
slot rectangle sits on the round canvas.*

### 2.1 The round‑clipping test (exact geometry)
The physical round watch shows exactly the **circle inscribed** in the square logical
canvas. On the 300 canvas the visible disc is centre `C=(150,150)`, radius `R=150`; the
square's corners are off‑screen. A box corner `(px,py)` is visible **iff**
`dist(px,py → C) ≤ R`. Anything past `R` is cut by the bezel. This is scale‑independent —
it holds identically at 384 px and 456 px (§2.4).

For each slot the **binding corner** is the box corner farthest from centre (the outer
corner on the edge away from centre). Distances below are in canvas units; `R=150`.

### 2.2 Per‑face results (300‑unit faces)

**CLIPPED — a corner is past the visible circle (renders cut off on round):**
| Face | slot | rect (x,y,w,h) | worst corner | dist | verdict |
|---|---|---|---|---|---|
| Kinetik‑Turbine | s1 top bar | 75,18,150,34 | (75,18)/(225,18) | **151.8** | ends clipped |
| Kinetik‑Turbine | s2 bottom bar | 75,248,150,34 | (75,282)/(225,282) | **151.8** | ends clipped |
| Aether‑Horizon | s1 top bar | 75,20,150,36 | (75,20)/(225,20) | **150.1** | ends clipped |
| Settype‑Halftone | s1 bottom bar | 75,256,150,24 | (75,280)/(225,280) | **150.1** | ends clipped |
| Kinetik‑Orrery | s1 bottom‑L | 48,232,84,34 | (48,266) | **154.5** | outer‑bottom corner clipped |
| Kinetik‑Orrery | s2 bottom‑R | 168,232,84,34 | (252,266) | **154.5** | outer‑bottom corner clipped |

**TIGHT — inside but hugging the bezel (≈0.96–0.97 R, < ~6u gap; reads as "off"):**
| Face | slot | rect | worst dist | gap to rim |
|---|---|---|---|---|
| Kinetik‑Odometer | s1/s2 | 68/154,238,78,32 | 145.3 | 4.7u |
| Aether‑Ember | s1/s2 | 72/156,236,72,36 | 144.8 | 5.2u |
| Kinetik‑Metronome | s1/s2 | 26/198,190,76,34 | 144.4 | 5.6u |

**SIDE‑HUGGING — small `x` at mid‑height (kisses the left bezel):**
Vespera‑Noir s1 `x=22`; Settype‑Counterform `x=30`; Kinetik‑Escapement `x=26` (oval,
so tolerable). At mid‑height the circle edge is at `x=0`, so these leave only 22–30u.

**COMFORTABLE — well inside, use as templates:**
Vespera‑Opaline (0.66 R), Vespera‑Aurum (0.72 R, `BoundingOval`), Vespera‑Salon (0.81 R),
Kinetik‑Escapement bounds are oval so corners are moot.

### 2.3 The 450‑unit Arclight faces are fine
`C=(225,225)`, `R=225`. All Solstice/Pulsar slots use `BoundingOval` and sit at
0.69–0.92 R (worst is Pulsar's `header` bar at 207.5/225 = 0.92 R, acceptable). These are
the reference design: oval bounds + generous inset. **No change needed.**

### 2.4 Why scale doesn't matter, but preview hides it
The clip test is a *fraction of radius*: corner‑dist/R. Multiplying the canvas by the
device scale multiplies both, so `>R` stays `>R` on 384 px and 456 px alike (it is
marginally worse on larger physical watches only via anti‑alias softness, not geometry).
But `preview.png` is a **450×450 square** that shows the whole square canvas — the clipped
corners are fully visible there. **That is why the bug never shows in the store preview
and only appears once pushed to the round watch.**

---

## 3. Round safe‑zone insets

Sources: [Handle different watch shapes](https://developer.android.com/training/wearables/views/layouts)
(round screens can be cropped near the edge; use inset‑aware layout / relative sizing),
[Build beautifully for round screens](https://android-developers.googleblog.com/2016/04/build-beautifully-for-android-wear.html).

- **Exact "always‑visible" boundary:** the inscribed **circle** of radius `R` (150 on
  300; 225 on 450). Corner must satisfy `dist ≤ R`.
- **Inscribed‑square safe zone** (the conservative box that fits at *any* rotation inside
  the circle): margin = `R·(1 − 1/√2) ≈ 0.1464·D/2`. On the 300 canvas that is
  **≈ 44 units** per side → safe square `x,y ∈ [44, 256]`. Content fully inside `[44,256]²`
  can never clip. *(0.146447 is the classic `BoxInsetLayout` window‑inset factor from the
  AndroidX Wear UI library; the doc page describes the behaviour but does not print the
  constant — mark the number [UNVERIFIED‑by‑doc‑text], though the geometry in §2.1 is
  exact and independent of it.)*
- **Practical recommendation for these faces:** don't demand the full inscribed square
  (it wastes the round area our bars intentionally use). Instead enforce a **bezel gap
  `m` on the circle test**: keep every box corner at `dist ≤ R − m` with **`m = 8` units
  on the 300 canvas (≈2.7%)**, `m = 12` on the 450 canvas. That preserves the wide‑bar
  aesthetic while guaranteeing a visible margin. Where a design wants to touch the rim,
  use `BoundingOval` (corners don't exist).
- **Where slots must NOT go:** the four canvas corners and any point with
  `dist > R − m`. In practice: no box corner above `y≈27` or below `y≈273` at full 150
  width; no slot `x < 34` or `x+w > 266` at mid‑height.

---

## 4. Pixel Watch 4 display (for on‑watch verification)

- Pixel Watch 4 panel: **456 × 456**, 1.4", ~320 ppi per
  [GSMArena](https://www.gsmarena.com/google_pixel_watch_4-14088.php) /
  [Google Store specs](https://store.google.com/product/pixel_watch_4_specs).
- The **41 mm** (owner's; grounding note says "42mm" — the marketed sizes are 41/45 mm)
  variant's exact resolution is **[UNVERIFIED]**: GSMArena lists a single 456×456 for the
  line, but the prior‑gen small variant (Watch 3 41 mm) was 384×384. **It does not change
  the diagnosis** — §2.4 shows clipping is scale‑invariant. Confirm on‑device px only if a
  pixel‑exact overlay is wanted.
- Scale factors: 300 canvas → **1.52 px/unit** at 456 (or 1.28 at 384); 450 canvas →
  **1.013 px/unit** at 456.

---

## 5. Centering & fit math recipe

Let `D` = canvas size (300 or 450), `C = D/2`, `R = D/2`, `m` = bezel margin
(8 on 300, 12 on 450). `Rs = R − m` (safe radius: 142 on 300, 213 on 450).

**A. Horizontal centring of any slot of width `w`:** `x = (D − w) / 2`.
(Turbine/Horizon/Halftone already do this; keep it.)

**B. Max width of a centred bar whose near‑rim edge sits at row `yEdge`:**
let `dy = |yEdge − C|` (use the box edge closest to the rim: `y` for a top bar, `y+h` for
a bottom bar). Then `w_max = 2 · √(Rs² − dy²)`.

**C. Equivalently, max `dy` for a given width `w`:** `dy_max = √(Rs² − (w/2)²)`, so a
top bar needs `y ≥ C − dy_max`; a bottom bar needs `y + h ≤ C + dy_max`.

**D. Any box corner (general slot):** for all four corners `(px,py)` require
`(px−C)² + (py−C)² ≤ Rs²`. The binding corners are the two on the side away from `C`.

**E. When to use `BoundingOval` instead:** if the slot's decoration is circular, OR the
slot must sit near the rim, OR corners fail test D by a small amount and you'd rather keep
the position — use a full‑slot `BoundingOval` (`x=0 y=0 width=slotW height=slotH`). Its
hit/crop region is the ellipse, so the square corners that were clipping are gone. Use
`BoundingBox` only for genuinely rectangular content that comfortably passes test D.

Worked example (Kinetik‑Turbine top bar, `w=150`, 300 canvas, `Rs=142`):
`dy_max = √(142² − 75²) = √(20164 − 5625) = √14539 = 120.6` → top row must be
`y ≥ 150 − 120.6 = 29.4`. Current `y=18` → **fails**. Fix: set `y=30` (bar drops 12u) **or**
narrow to `w = 2·√(142² − 132²) = 2·√2740 = 104.7 → w≤104` keeping `y=18`. Dropping `y` is
less destructive to the design than halving the width.

---

## 6. Reusable slot templates (copy‑paste WFF)

### 6.1 Centred top/bottom text bar (safe on 300 canvas)
Replace `{Y}`, `{W}`, `{H}` per test C. For a **top** bar keep `Y ≥ 30` at `W=150`;
for a **bottom** bar keep `Y + H ≤ 270` at `W=150`. `x` is auto‑centred: `x=(300−W)/2`.
```xml
<!-- 300-unit canvas. W<=150, top: Y>=30 ; bottom: Y+H<=270. x=(300-W)/2 -->
<ComplicationSlot slotId="1" name="s1" displayName="@string/slot_1"
    supportedTypes="SHORT_TEXT RANGED_VALUE EMPTY"
    x="75" y="30" width="150" height="34">
  <DefaultProviderPolicy defaultSystemProvider="STEP_COUNT"
      defaultSystemProviderType="SHORT_TEXT" />
  <BoundingBox x="0" y="0" width="150" height="34" />
  <Complication type="SHORT_TEXT">
    <PartText x="0" y="0" width="150" height="20">
      <Variant mode="AMBIENT" target="alpha" value="0" />
      <Text align="CENTER"><Font family="barlow_condensed_semibold" size="16"
        weight="SEMI_BOLD" color="#FFF2F0EA">
        <Template>%s<Parameter expression="[COMPLICATION.TEXT]" /></Template>
      </Font></Text>
    </PartText>
  </Complication>
</ComplicationSlot>
```

### 6.2 Circular corner slot (use near the rim / for round decoration)
Position so the whole oval fits test D; a full‑slot `BoundingOval` removes corner clip.
```xml
<!-- corner dial. Keep dist(center, canvasCenter)+radius <= 142 on 300 canvas -->
<ComplicationSlot slotId="2" name="s2" displayName="@string/slot_2"
    supportedTypes="SHORT_TEXT RANGED_VALUE EMPTY"
    x="60" y="196" width="64" height="64">
  <DefaultProviderPolicy defaultSystemProvider="HEART_RATE"
      defaultSystemProviderType="SHORT_TEXT" />
  <BoundingOval x="0" y="0" width="64" height="64" outlinePadding="2" />
  <Complication type="SHORT_TEXT">
    <PartDraw x="0" y="0" width="64" height="64">
      <Variant mode="AMBIENT" target="alpha" value="0" />
      <Ellipse x="0.5" y="0.5" width="63" height="63">
        <Stroke color="#2EEDE7DA" thickness="1" /></Ellipse>
    </PartDraw>
    <PartImage x="24" y="6" width="16" height="16">
      <Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]" /></PartImage>
    <PartText x="0" y="26" width="64" height="22">
      <Text align="CENTER"><Font family="ibm_plex_mono_medium" size="13"
        weight="MEDIUM" color="#FFEDE7DA">
        <Template>%s<Parameter expression="[COMPLICATION.TEXT]" /></Template>
      </Font></Text>
    </PartText>
  </Complication>
</ComplicationSlot>
```
Keep the inner `PartText`/`PartImage`/`Variant` and `DefaultProviderPolicy` from each
face's existing slot — only the **slot `x/y/width/height`** (and box→oval choice) change.

---

## 7. RECIPE — Phase 3 execution (per face, no re‑research)

1. **Read** `faces/<Face>/app/src/main/res/raw/watchface.xml`; note `<WatchFace width>`
   (300 or 450). Set `C=D/2`, `Rs=142` (300) or `213` (450).
2. **For each `<ComplicationSlot>`**, compute the four box corners
   `(x,y)(x+w,y)(x,y+h)(x+w,y+h)` and `dist→C`. Flag any `> Rs` (test D).
3. **Fix flagged slots**, cheapest edit first:
   - Centred bar too tall/near rim → **lower/raise `y`** via test C (`dy_max =
     √(Rs²−(w/2)²)`; top `y≥C−dy_max`, bottom `y+h≤C+dy_max`). Re‑centre `x=(D−w)/2`.
   - Still failing → **narrow `w`** (`w_max = 2√(Rs²−dy²)`), keep `x=(D−w)/2`.
   - Offset/corner slot → **move toward centre** until all corners pass, and/or switch
     `BoundingBox`→`BoundingOval` (§6.2) so corners stop existing.
   - Side‑hugging (`x<34` or `x+w>266` on 300) → shift inward to `x≥34` / `x+w≤266`.
4. **Keep** `BoundingBox`/`Oval` at `x=0 y=0 width=slotW height=slotH`, and keep the
   inner `PartText/PartDraw` sizes = slot size so paint stays co‑registered (do not
   introduce a bounds/paint offset while fixing position).
5. **Concrete first‑pass edits** (from §2.2):
   - Kinetik‑Turbine s1 `y 18→30`; s2 `y 248→236` (so `y+h=270`).
   - Aether‑Horizon s1 `y 20→30`.
   - Settype‑Halftone s1 `y 256→246` (so `y+h=270`).
   - Kinetik‑Orrery s1 `x 48→58`,`y 232→226`; s2 `x 168→158`,`y 232→226` (or oval bounds).
   - Kinetik‑Odometer / Aether‑Ember / Kinetik‑Metronome: nudge inner slots ~6–8u toward
     centre (raise bottom `y` by ~6, or reduce `w` by ~10) to buy the bezel gap.
   - Vespera‑Noir `x 22→34`; Settype‑Counterform `x 30→34`.
   - Arclight Solstice/Pulsar, Vespera‑Opaline/Aurum/Salon: **no change** (reference).
6. **Do not** touch `preview.png` — it is square and unaffected; but note the fixed slots
   will now look slightly inset in that square preview, which is expected and correct.
7. **Validate**: run the repo's wff‑validator in CI (never local build), then owner does
   the on‑watch check on the Pixel Watch 4. A pixel‑exact overlay needs the confirmed
   41 mm resolution (§4) — request it only if the visual check is inconclusive.

---

### Gaps / to confirm
- **[UNVERIFIED]** Exact resolution of the owner's small Pixel Watch 4 (41/42 mm): 456 vs
  384. Does **not** affect the fix (clipping is scale‑invariant); only a pixel‑exact
  overlay would need it.
- **[UNVERIFIED‑by‑doc‑text]** The `0.146447` inscribed‑square inset constant is from the
  AndroidX Wear `BoxInsetLayout` source, not printed on the doc page. The §2.1 circle test
  and all §5 math are exact and independent of that constant.
- Owner has not supplied an on‑watch screenshot of the clipped complications; the
  diagnosis is derived from the slot geometry + WFF crop semantics and is definitive on
  paper, but the specific *visual* severity per face is best confirmed on the device.
