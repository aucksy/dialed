# Dialed — Phase 0 Research Index

**Purpose:** de-risk the owner's 8 complaints before any code/build. Five research spikes (R1–R5),
each research-only (no XML edits, no builds, no tags), each mapping to a later phase.
**Date:** 2026-07-12. **Device under test:** Pixel Watch 4 (owner's = small variant), Wear OS 6.

This index was written by the accuracy critic **after** cross-checking every doc's load-bearing
claims against primary sources (WFF reference, Watch Face Push docs, androidify, wear-os-samples).
It does not replace the docs — it rates their confidence and collects what is still owed.

---

## The five docs (one-line takeaway + headline confidence)

| Doc | Owner issue | Phase | One-line takeaway | Headline confidence |
|---|---|:---:|---|---|
| **R1 — Resolution / crispness** | #1 "low res, worse on big watches" | 2 | Raster art is **not** the cause — every asset is authored ≥2× and *down-samples* on a 456 watch; the softness is either the **preview path** (→R5) or a hypothetical full-canvas upscale — only the screenshot + a one-face 300-vs-450 twin can tell which. | **SCREENSHOT-GATED** (raster-ruled-out sub-claim is SOLID) |
| **R2 — Complication alignment** | #8 "alignment bad on most faces" | 3 | Not a bounds-vs-paint offset — bounds and paint are co-registered everywhere. It's **round-display corner clipping**: wide top/bottom bars and offset corner slots on the 300 canvas push corners past the visible circle (R=150). Fix = inset via exact circle math or switch `BoundingBox`→`BoundingOval`. | **SOLID** (per-face visual severity is screenshot-gated) |
| **R3 — Smooth motion** | #7 "motion janky" | 4 | Three real offenders: **Metronome** (12 fps free-running sprite, primary), **Pulsar** (per-second `[SECOND]*6` tick, not a sweep), **Turbine/Escapement** (`Sweep frequency="5"` on big rotors). Fix = raise Sweep to `15`/`SYNC_TO_DEVICE`, convert ticks to swept hands, re-author the Metronome sprite with more frames. | **SOLID** (no screenshot needed — diagnosed from XML) |
| **R4 — Carousel / slot model** | #6 "not in the '+' favorites carousel" | 6 | **Slot count on WO6 = 1** (hard ceiling), and **there is no API to add a face to favorites/carousel**. A pushed face becomes the *active* face but is not auto-listed as a browsable tile. The **only** lever is bundling a **default watch face** (`assets/default_watchface.apk` + validation-token meta-data) → seeds ONE "Dialed" tile in the system gallery. | **SOLID** (2 device-gated sub-items, see checklist) |
| **R5 — Installed-state query + distorted preview** | #2 query / #5 distortion | 1 / — | **Part A (Phase 1): fully feasible** — `listWatchFaces()` + `isWatchFaceActive(pkg)` answer "which Dialed face is installed AND is it active" (no getter for the active package → you probe it; slot=1 makes that sufficient). Clean wire-protocol spec provided. **Part B (#5): no aspect-ratio bug on any Dialed code path** (previews are 450×450 square, `ContentScale.Crop`+`CircleShape` can't distort) → almost certainly the **platform's** activation/picker UI. | **Part A SOLID / Part B SCREENSHOT-GATED** |

---

## Primary-source verification (what the critic re-confirmed)

Every headline rests on facts that were re-checked against the live primary docs this pass:

- **R4/R5 — WO6 slot limit = 1.** CONFIRMED verbatim: *"The system sets a maximum number of slots
  that a marketplace can have; with Wear OS 6, the limit is 1."* (WFP wear-os-app guide).
- **R4 — no favorites/carousel API.** CONFIRMED: the `WatchFacePushManager` surface is
  add / list / update / remove / `isWatchFaceActive` / `setWatchFaceAsActive` — **no** `addToFavorites`,
  `pin`, or picker-injection method exists. `addWatchFace` throws `ADD_SLOT_LIMIT_REACHED_ERROR` at the
  cap; different-package `updateWatchFace` is a full replacement that carries active-state forward. All
  CONFIRMED.
- **R4 — default watch face → picker.** CONFIRMED verbatim: the `assets/default_watchface.apk` +
  `DEFAULT_WATCHFACE_VALIDATION_TOKEN` meta-data *"makes your watch face available in the system watch
  face picker."*
- **R4/R5 — `setWatchFaceAsActive` is one-time** and the `SET_PUSHED_WATCH_FACE_AS_ACTIVE` permission
  has **max rejection count = 1.** Both CONFIRMED verbatim.
- **R5 — `isWatchFaceActive(pkg)` returns Boolean** and no method returns the active package (probe
  pattern is required). CONFIRMED; matches androidify's `hasActiveWatchFace` implementation.
- **R3 — `Sweep frequency` ∈ {2, 5, 10, 15, SYNC_TO_DEVICE}.** CONFIRMED verbatim. So "raise 5→15" and
  "15 is the smooth ceiling / no higher fixed value" are both correct.
- **R2 — bounding-area crop semantics.** CONFIRMED verbatim: *"Any content outside of the bounding area
  is cropped,"* and the bounding area is also the selection region — so R2's dual-role premise holds.
- **R1 — memory is realized at physical display px.** CONFIRMED verbatim: *"750 KB or more on a
  450-pixel x 450-pixel screen,"* cost `= 4 × width × height`, limits **10 MB ambient / 100 MB
  interactive.** This is the evidence for R1's "primitives render at device resolution" argument.

### Minor accuracy caveats found (do not block any phase)
- **API naming layer (R5).** R5's code uses the **androidx** Kotlin wrapper names
  (`listWatchFaces().installedWatchFaceDetails`, `remainingSlotCount`, `.packageName`) — which is what
  the Dialed repo already compiles against and what androidify uses. The lower-level Google-services
  Java reference exposes the same capability under different names (`listWatchFaceSlots()`,
  `getAvailableSlotCount()`). Not a contradiction; the Phase 1 implementer should just confirm the
  installed-detail element exposes `.packageName` (androidify proves it does).
- **Two quotes not found on their cited page.** R1's "avoid upscaling" and R3's "most animations look
  fluid at 30 fps" are attributed to the watch-face *performance* page; that page (as fetched) shows the
  memory-cost and 10/100 MB facts but not those two lines. The lines are standard Wear guidance and the
  conclusions don't depend on them, but treat those two specific attributions as **PARTIAL**.

---

## Does each doc end with an execution-ready recipe? — YES (all five)

- **R1 §7** — decision + the one-face **300-vs-450 canvas twin** experiment (gated on the screenshot).
- **R2 §7** — numbered per-face Phase-3 recipe with concrete first-pass edits + the exact circle math.
- **R3 §6** — numbered Phase-4 recipe (quick `frequency` wins → Pulsar decision → Metronome asset work).
- **R4 RECIPE** — numbered 8-step Phase-6 recipe (build default face → token → bundle → meta-data →
  revert-to-default semantics → on-device verify).
- **R5 A.5** — numbered Phase-1 spec (WearConstants codecs → repo methods → listener wiring → phone UI)
  + B.3 optional hardening.

---

## Cross-doc consistency check — no contradictions found

- **Canvas model is uniform across R1/R2/R3:** 16 faces on a **300-unit** canvas + **2 Arclight**
  (Solstice, Pulsar) on a **450-unit** canvas = 18. R3's motion table, R2's slot table, and R1's asset
  table all agree.
- **Scale factor** 300→456 = **1.52 px/unit**, 450→456 = **1.013** — identical in R1 and R2.
- **`preview.png` = 450×450 square** — stated identically in R1, R2, R5, and load-bearing for all three
  ("square preview hides the round-clip bug"; "square source can't distort under Crop").
- **`setWatchFaceAsActive` one-time + slot=1** — R4 and R5 agree and both match primary docs.
- The **owner's small-PW4 resolution (456 vs 384)** is flagged `[UNVERIFIED]` identically in R1 and R2,
  and both correctly note it does **not** change their fix.

---

## Open questions / owner-gated items — ONE checklist

**★ UPDATE (2026-07-12): the #5 screenshot ARRIVED and was analysed** (`R5-screenshot-evidence.md`).
It shows the **wear "setting the face" moment**, and it CLOSES R5-B: the stretched oval is the
**platform** system "applying watch face" thumbnail (smaller than any FaceDial; geometrically
impossible for our square→circle FaceDial to draw), **not** a Dialed bug. But that one screenshot does
**not** settle the other three docs it was hoped to feed — those need a *different* capture:

- [x] **#5 SCREENSHOT — RECEIVED + RESOLVED (R5-B).** Verdict: platform apply-preview, no app-side fix.
      → the #5 remedy moves to **Phase 5** (cover the system moment with Dialed's own circular Concierge).
- [ ] **R1 still needs a DIFFERENT shot — a full-screen photo of the WORN, applied face** (not the
      Dialed app / apply thumbnail). The received screenshot is the activation moment, so it does **not**
      tell us whether the *live* face is soft. Get a plain photo of the face on-wrist to classify #1.
- [ ] **R4-§4 still needs the long-press picker screenshot** (favorites strip vs "+ Add watch faces"
      gallery) — the received shot doesn't show that surface.
- [ ] **R2 per-face visual severity** — still best confirmed on-device (corner clipping); the received
      shot doesn't show a complication-heavy face full-screen.
- [ ] **R1 — 300-vs-450 canvas twin test (owner on-watch A/B).** Only *if* the screenshot shows the
  **live** face is soft. Decides whether the fleet migrates 300→450. ~30 min XML + one cheap build.
- [ ] **R1/R2 — confirm owner's PW4 (41/42 mm) native resolution: 456 vs 384.** `[UNVERIFIED]`. Does
  **not** affect either fix; only needed for a pixel-exact overlay.
- [ ] **R3 — Metronome design decision (owner).** Smoothing it means **regenerating the sprite** at
  ~48–60 frames (asset/pipeline work), or accepting it as subtle decoration. Not a one-line edit.
- [ ] **R3 — bench max sustainable sprite `frameRate` on PW4** (device may cap ~15 fps and frame-skip);
  and `[UNVERIFIED]` the exact millisecond token in a `<Transform>` expr (only for Pulsar option B2,
  off the primary fix path).
- [ ] **R4 — does a bundled default face consume the single slot?** `[UNVERIFIED]` — confirm on the
  Phase 6 device build (best-evidence read: **yes**, forcing every push down the `updateWatchFace`
  replace path).
- [ ] **R4 — exact favorites-strip vs gallery rendering on PW4** `[UNVERIFIED — screenshot-gated]`;
  don't promise the quick favorites strip, only the documented "+ gallery."
- [ ] **R1 — `renderMode` enum values** `[UNVERIFIED]` (defaults are correct; reference-only).
- [ ] **R2 — `0.146447` inscribed-square constant** `[UNVERIFIED-by-doc-text]` (the §2.1 circle test and
  all §5 math are exact and independent of it — informational only).

None of the above blocks Phase 1.

---

## GO / NO-GO — can Phase 1 start?

**Phase 1 needs: R5 Part-A query protocol + R4 slot-count. Verdict: GO. ✅**

- **R4 slot-count = 1** — SOLID, primary-source confirmed.
- **R5 Part A** — SOLID. `isWatchFaceActive` (Boolean) + `listWatchFaces().installedWatchFaceDetails`
  answer installed-and-active; the "no active-package getter → probe" pattern is exactly androidify's,
  and Dialed's repo already has both primitives + never persists a slotId. The wire protocol (two new
  paths `PATH_QUERY_STATE` / `PATH_UNINSTALL`, newline/byte-ordinal codecs) is a clean extension of the
  existing dep-free `WearConstants` style, and package→catalog mapping is a pure lookup.
- **Neither depends on the #5 screenshot.** Phase 1 is unblocked today.
- **One confirm-at-implementation (not a blocker):** verify the androidx `installedWatchFaceDetails`
  element exposes `.packageName` in the version Dialed compiles against (androidify confirms it does).

**Phases that ARE gated:** Phase 2 (R1) waits on the screenshot ± the twin test; Phase 4's Metronome
sub-task (R3) waits on the owner's design decision + asset regen; Phase 6 (R4) needs the default-face
build to settle the slot-consumption `[UNVERIFIED]`. Phase 3 (R2) can proceed on paper now (deterministic
geometry fix), with on-watch confirmation of visual severity.
