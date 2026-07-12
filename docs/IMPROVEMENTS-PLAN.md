# Dialed — Quality & Features Plan (v0.3.0 → v0.8.0)

**Status doc — read first, keep the Progress table live.** Owner reported 8 major issues after on-device
testing `dialed-v0.2.1` (Pixel Watch 4 42mm + phone). This plans them in phases. Entry point: `CLAUDE.md`
(architecture, WFP gotchas, ship loop). Design source of truth: the two `*.dc.html` specs + `HANDOFF*.md`.

> Created 2026-07-12 after the push-reporting fix pass (`dialed-v0.2.1`).

## Hard constraints (do not violate)
- **NO local Android builds** — CI (GitHub Actions, tag `dialed-v*`) is the ONLY compiler + WFP-token
  verifier. Every phase ends in a tagged release with direct `-phone.apk` + `-wear.apk` links.
- **Two repos.** Dialed app = `github.com/aucksy/dialed`. Faces = `faces/` submodule →
  `github.com/aucksy/fablecollection`. Face-quality fixes (#1/#7/#8) are edits in the **submodule**,
  then bump the submodule pointer in Dialed + `node scripts/add_face.mjs` (regenerate facepacks +
  previews + `FaceCatalog.kt`) + CI re-validates all faces.
- **WFP slot = 1 on Wear OS 6.** At most ONE Dialed face is installed at any time; pushing face B over A
  is `updateWatchFace` (full replace), not a 2nd slot. This reshapes #2/#3/#6 (see below).
- `:wear` `applicationId` MUST stay `com.dialed.app` (matches the `--package_name` the tokens are minted
  with). AGP 9 built-in Kotlin — never apply `org.jetbrains.kotlin.android`. Git author
  `simpleapps108@gmail.com`. `ScrollInfoProvider` is in `wear.compose.foundation`, not material3.
- Ship discipline: compile-review → adversarial logic review → push main → poll CI green → tag → paste
  direct APK links + what to re-test. (See CLAUDE.md "Ship loop".)

## Open questions / owner-gated
- **#5 screenshot ("PFA") never arrived.** Re-share the distorted "installing" image before Phase 5 (and
  it also informs Phase 2's preview-distortion fix).
- **Priority order below is a recommendation** (research-first, then highest user value, then face
  quality, then polish). Reorder freely — only Phase 0 must precede 2/3/4/6.
- Slot=1 UX: confirm the owner accepts "one installed face at a time, Install = Replace" framing (Phase 1).

---

## The 8 issues → phase map

| # | Issue (owner's words) | Root-cause read | Repo | Phase |
|---|---|---|---|---|
| 1 | Watch-face resolution low; worse on bigger watches | 450×450 WFF canvas is fine; **raster `<Image>` assets + text AA scaling** on 384→456px displays | faces | **0 (research) → 2** |
| 2 | Home + detail should show which faces are installed; shows "Install to watch" for installed ones — want Uninstall | Phone has no knowledge of watch's installed/active face; **slot=1 → exactly one** | Dialed | **1** |
| 3 | Uninstall from phone home via a single icon button | `removeWatchFace(slotId)` not implemented; needs phone→watch uninstall command | Dialed | **1** |
| 4 | The in-app showcase should be animated | Phone `FaceDial` renders a static `preview.png` (F1 "living gallery" never built) | Dialed | **5** |
| 5 | WearOS: no animation while setting the face; shows the face in **distorted dimensions** | (a) missing themed "setting…" state; (b) `FaceDial` bitmap aspect/`ContentScale` bug | Dialed | **2 (distortion) + 5 (animation)** |
| 6 | Pushed faces don't appear in the watch's long-press `+` face carousel | WFP marketplace faces may not enter the system favorites picker; **research** whether fixable | both | **0 (research) → 6** |
| 7 | Motion faces (rotating dial etc.) are janky/not smooth | WFF animation via 41 `<Transform>`; interpolation / update-rate / heavy per-frame expr | faces | **0 (research) → 4** |
| 8 | Complication alignment bad on most faces | Hardcoded `<ComplicationSlot x/y/w/h>` bounds don't register with the drawn decoration/round safe-zone | faces | **0 (research) → 3** |

---

## Phase 0 — Research & Diagnosis spike  _(no feature ship; produces research docs + optional diagnostic face)_
**Goal:** turn the 4 hard, research-dependent issues into concrete, per-face fix recipes so Phases 2/3/4/6
are execution, not guesswork. Run as a **multi-agent Workflow** (primary sources: WFF docs, Google
`wear-os-samples` WatchFacePush + `watchface` samples, androidify, Pixel Watch display specs). Ultracode is
off, so the new chat must explicitly opt into the workflow.

Deliverables → `docs/research/`:
- **R1 (#1 resolution):** WFF crispness across 384/408/456px round displays. Are the faces raster-limited?
  Capture every `<Image>`/`<PartImage>` asset's pixel dimensions vs its on-canvas size; determine target
  export resolution (likely 2× for the largest watch), whether to vectorize, text anti-alias settings, and
  the correct `<Image>` width/height/`renderMode`. Output: a per-asset re-export table + a WFF authoring
  checklist. **Optionally ship a 2-face diagnostic APK** (one all-vector, one hi-res raster) to A/B on the
  42mm before touching all 18.
- **R2 (#8 complications):** WFF complication slot alignment on round. `BoundingOval` vs `BoundingBox`,
  slot bounds vs the drawn ring/decoration, centering math on a 450 canvas, round safe-zone insets,
  default provider policy. Output: an alignment recipe + a reusable slot template.
- **R3 (#7 jank):** smooth WFF motion. `<Transform>` interpolation vs `<Sweep>`/`<AnalogClock>` seconds,
  `updateRate`/frame budget, expensive per-frame expressions, ambient/AOD freeze. Output: a de-jank recipe
  + which faces use which motion.
- **R4 (#6 carousel + slot model):** does a WFP-pushed face appear in the system long-press favorites
  carousel / the `+` picker? Confirm the WO6 marketplace slot count, `setWatchFaceAsActive` semantics, and
  whether a "favorite"/picker-visibility path exists — or document it as a platform limitation with the
  best-effort UX. Output: a definitive yes/no + the exact API/manifest change if any.
- **R5 (#2/#5):** how the phone can query the watch's installed + active face (package names via
  `listWatchFaces()`), and the exact cause of the wear preview distortion (extractor bitmap aspect vs
  `ContentScale`). Output: the query-protocol shape for Phase 1 + the FaceDial fix for Phase 2.

**Exit:** research docs merged; Phases 2/3/4/6 each have a concrete recipe. No version bump (docs only) —
optionally tag a diagnostic face if built.

## Phase 1 — Installed-state + Uninstall  _(Dialed phone + wear; depends on R5, R4 slot-confirm)_ → **`dialed-v0.3.0`**
Highest user-facing value and largely independent of the faces work.
- **`:wear-common`** — add protocol: `PATH_QUERY_STATE` (phone→watch) + reply encoding the installed
  package name(s) + the active package; `PATH_UNINSTALL` (phone→watch, carries the target) + result.
- **`:wear` `WatchFacePushRepository`** — add `installedState()` (list `installedWatchFaceDetails`
  packageNames + `isWatchFaceActive`) and `removeWatchFace(slotId)` (guard `RemoveException` +
  `catch(Exception)`, log). Reuse the exactly-once/guarded pattern from v0.2.1.
- **`:wear` listener** — handle query + uninstall requests (respond even on failure; mirror the
  finalize-in-finally discipline).
- **`:app` `WatchBridge` + `MainViewModel`** — `queryInstalledState()` on watch-connect + after every
  push/uninstall; expose `installedFaceId` / `activeFaceId` (map package → catalog id).
- **`:app` UI** — Home: an "Installed"/"Active" ring or badge around the installed face + a **single-icon
  uninstall button**; FaceDetail: show **Uninstall / "Active on your watch"** instead of Install for the
  installed face; for others, Install button reads **"Replace on watch"** (honest about slot=1).
- **Test:** push face A → Home badges A, detail shows Uninstall; push B → badge moves to B; uninstall
  icon clears the slot; reflects correctly after app restart.

## Phase 2 — Face resolution + preview distortion  _(faces submodule + Dialed; depends on R1, R5)_ → **`dialed-v0.4.0`**
- **faces:** apply R1 across all 18 — re-export/upscale (or vectorize) raster assets, fix `<Image>`
  sizing/`renderMode`, text AA. Re-render each `preview.png` crisp.
- **Dialed:** fix wear `FaceDial` + the "installing" preview distortion (enforce square + `ContentScale`
  + circular clip; verify `FacePreviewExtractor` bitmap dims). Fix phone `FaceDial` if it stretches too.
- **Pipeline:** bump submodule → `node scripts/add_face.mjs` → CI re-validates all 18 tokens.
- **Test:** owner A/Bs crispness on the 42mm (and mentally on 45mm); no stretched previews anywhere.

## Phase 3 — Complication alignment  _(faces; depends on R2)_ → **`dialed-v0.5.0`**
- Apply the R2 recipe to every face's `<ComplicationSlot>` bounds; align slot with the drawn decoration;
  respect round safe zones. Regenerate + CI-validate.
- **Test:** owner adds complications on several faces; they sit correctly centered/aligned.

## Phase 4 — Smooth animations  _(faces; depends on R3)_ → **`dialed-v0.6.0`**
- De-jank the rotating/motion faces per R3 (interpolation, sweep vs transform, update-rate, AOD freeze).
- **Test:** owner confirms smooth second/rotation motion on the affected faces; battery/AOD sane.

## Phase 5 — App animation polish  _(Dialed UI)_ → **`dialed-v0.7.0`**
- **Phone (#4):** animated showcase — F1 "living gallery" (animate the gallery/detail dials or the
  preview) per the phone design spec's F1/F5.
- **Wear (#5 animation):** a themed "Setting your watch face…" state during `setActive` (proper W2
  concierge motion, non-distorted), replacing the current abrupt/distorted moment.
- **Test:** showcase animates on Home/detail; the watch shows a branded setting-face animation.

## Phase 6 — Carousel / favorites visibility  _(both; depends on R4)_ → **`dialed-v0.8.0`**
- Implement whatever R4 found makes a pushed face appear in the watch's long-press `+` picker (a
  `setActive`/favorites nuance, manifest/flag, or — if a platform limit — the best-effort UX + clear copy).
- **Test:** after pushing from the phone, the face is findable in the watch's face-selection carousel.

---

## Progress
| Phase | Ver | State | Notes |
|---|---|---|---|
| 0 Research spike | — | ⬜ not started | run as a Workflow; outputs → docs/research/ |
| 1 Installed-state + Uninstall | v0.3.0 | ⬜ | needs R5 + slot-confirm |
| 2 Resolution + distortion | v0.4.0 | ⬜ | needs R1; re-share #5 screenshot |
| 3 Complication alignment | v0.5.0 | ⬜ | needs R2 |
| 4 Smooth animations | v0.6.0 | ⬜ | needs R3 |
| 5 App animation polish | v0.7.0 | ⬜ | #4 + #5-animation |
| 6 Carousel/favorites | v0.8.0 | ⬜ | needs R4; may be platform-limited |

**Update this table + the phase sections as each phase ships. One phase per fresh chat (see
[[fresh-chat-handoff-protocol]] equivalent): finish → tag → hand off the next kickoff prompt.**
