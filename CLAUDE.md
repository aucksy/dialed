# Dialed — project context (read first)

Dialed is a **native Kotlin/Compose Watch Face Push (WFP) marketplace** for Wear OS 6. It is a
**living hub**: it bundles the whole fablecollection watch-face collection, sells it as a
**one-time purchase**, pushes faces to the watch via WFP — and doubles as the developer's
**laptop-free test harness** for every new face. Build guide: `watchface-marketplace-build-guide.md`.
Design source of truth: `Dialed Watch-Face Store-handoff/.../Dialed - Design Spec.dc.html` + `HANDOFF.md`.

## Non-negotiable constraints
- **Wear OS 6 / API 36 only** for WFP. Gate every use with `WatchFacePushManager.isSupported()`.
- **Slot limit = 1** on WO6 → installing face B over A is `updateWatchFace` (full replace), never a 2nd slot.
- **One face per APK**, package `com.dialed.app.watchfacepush.<series>.<face>`, `hasCode=false`, no Activity/Service, minSdk 33, signed with a key **different from `:app`**.
- **Each face APK needs a validation token** (Watch Face Push validation tool). Tokens don't expire; regenerate only when the APK changes.
- **One-time INAPP** (`unlock_all_faces`): acknowledge within 3 days, never consume, restore on launch.
- **No local Android builds on the dev machine** (RAM). Everything is **cloud-built via GitHub Actions**; the WFP tokens are produced in CI, not locally.

## Verified dependency/policy facts → see `WFP-RESEARCH.md` (verified 2026-07-11)
- WFP lib `androidx.wear.watchfacepush:watchfacepush:1.0.0` (stable). Validator `…validator.watchface.validator:validator-push[-cli|-android]:1.0.0-alpha10`. Play Billing `billing-ktx:9.1.0`.
- Toolchain pinned to Google's WatchFacePush sample: AGP 9.1.1 · Gradle 9.4.1 · Kotlin 2.3.21 · Compose BOM 2026.04.01 · compileSdk 36 · JVM 17.

## Architecture (androidify split)
- **`:app`** — phone: Compose storefront + Play Billing + (Phase 4) Data-Layer sender. Bundles face APKs in `assets/faces/` + tokens in `assets/tokens/` (CI-produced).
- **`faces/`** — git submodule → `github.com/aucksy/fablecollection` (the face library, one source of truth).
- **`facepacks/<key>/`** — GENERATED thin Gradle modules (one per face) that reuse the submodule's WFF res+manifest and only override `applicationId`. Regenerate with `tools/gen-facepacks.mjs`.
- **`:wear` / `:wear-common` / `:watchface`** — DEFERRED to Phase 3-4 (WFP bridge + transport + bundled default face).
- `dialed-faces.keystore` — shared throwaway debug key for all facepacks (≠ `:app` key, per WFP rule).

## THE add-a-face routine (§3A — used forever)
Faces are reused from the submodule, so adding one is:
```
node scripts/add_face.mjs          # sync submodule to latest + regenerate facepacks/catalog/previews
# review diff → git commit → git tag dialed-vX.Y.Z → push --follow-tags → CI builds+validates+bundles
```
`tools/gen-facepacks.mjs` is idempotent and rebuilds the whole set from `faces/`.

## Ship loop
Validate → adversarial review → push main → tag `dialed-v*` → paste the direct `.apk` link. Git author `simpleapps108@gmail.com`. Owner must create the GitHub repo `aucksy/dialed` (no `gh` CLI on the machine).

## Current FaceCatalog (18 faces) — regenerate, don't hand-edit
Arclight (Solstice, Pulsar) · Kinetik (Orrery, Escapement, Odometer, Turbine, Metronome) · Aether (Horizon, Ember) · Settype (Counterform, Masthead, Marquee, Halftone) · Vespera (Aurum, Noir, Salon, Meteorite, Opaline). Full table: `FACE-INVENTORY.md`. Home filters by series.

## Phase status
- ✅ **Phase 0** pipeline (repackage → validate → tokenise → bundle) scaffolded; runs in CI. `faces` CI job is `continue-on-error` until the `validator-push-cli` artifact/output format is confirmed on first real run.
- ✅ **Phase 1** storefront: Home (grid), FaceDetail, Paywall, Settings, Onboarding, full design system (tokens/type/motion). Install button present, gated on entitlement; push is Phase 4.
- ⏳ **Phase 2** Billing · **Phase 3** Wear bridge (WFP on real Pixel Watch 4) · **Phase 4** phone→watch transport + **4A** dev/sideload · **Phase 5** default face + activation · **Phase 6** polish/publish.

## Known deviations / TODO
- Brand font Instrument Sans not yet wired (metrics match §3; typeface pending — add `ui-text-google-fonts` + certs).
- FaceDial renders each face's real `preview.png` (not the design's procedural placeholder dials) — the spec said to swap real previews in; `ticking` overlay deferred.
- Tokens shipped as **asset files** (`assets/tokens/*.token`) not string resources, to keep `:app` buildable before tokens exist.
- Nothing is locally compiled (no toolchain) — **first CI run is the verifier**.
