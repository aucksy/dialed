<!-- R7 — Research: showing the REAL watch-face animation in the Dialed phone app.
     Multi-agent workflow (17 agents, adversarially verified), 2026-07-13. -->

# Showing the real watch-face animation in the Dialed app

## 1. Direct answer

**Yes — there is a way to show the real, exact animation for the entire gallery with no watch connected. It is not the elegant phone-local option you'd hope for; that option is dead. The winning path is to pre-bake the animation in CI.**

Two "obvious" ideas are provably impossible and should be struck off now:

- **You cannot render these faces on the phone.** A WFF face APK is `hasCode=false` — it contains *zero* rendering code. The thing that turns `watchface.xml` into pixels is a closed Wear OS system process (`com.google.wear.watchface.runtime`) that does not exist on phone-form Android. `androidx.wear.watchface.client` compiles into a phone app but every render call fails at runtime with `ServiceNotBoundException`. **(REFUTED by primary sources.)**
- **There is no offline WFF rasterizer** from Google, Samsung, AOSP, or the community. Google open-sources the validator, a memory evaluator, and an optimizer — pointedly *not* the renderer. **(REFUTED.)**

That leaves one place the *real* renderer is reachable without a physical watch: **a Wear OS emulator**. And it turns out you already own an even cheaper lever — **the repo's own build-time SVG bake pipeline** (`faces/collection3-tools/gen/bake.mjs`) already produces clean, hands-off dial base plates and separated hand sprites from the same source art the faces are compiled from.

**Single best approach:** a **build-time hybrid** — bake exact pixel layers (base dial + moving-element sprites) at build time, ship a short **animated WebP** only for motion that can't be decomposed (the one sprite face, shimmer/glow), and **rotate hands / draw live digital time procedurally in Compose on the phone**. This is watch-free, covers the whole gallery, and is pixel-exact because the pixels come from the real renderer (or the real source art) — not a fake gold hand. Where the repo's SVG bake can't supply a layer, a **Wear OS emulator screen-capture job in CI (Approach D)** fills the gap with the genuine runtime.

---

## 2. Ranked comparison

| Approach | Fidelity | Works w/o watch | Whole gallery | Build complexity | Verdict |
|---|---|---|---|---|---|
| **D — Pre-bake on Wear OS emulator in CI, screen-capture the real runtime** | **Exact** (real renderer) | ✅ Yes | ✅ Yes | High (CI orchestration) | ✅ **RECOMMENDED — primary source of truth for exact frames** |
| **E-LITE — Build-time separated layers (repo SVG bake / emulator) + procedural Compose rotation** | **High→Exact** | ✅ Yes | ✅ Motion faces | Medium | ✅ **RECOMMENDED — fastest, cheapest, seamless; pairs with D** |
| **F — Animated WebP + procedural-overlay delivery layer** | High (exact when paired) | ✅ Yes | ✅ Yes | **Low** | ✅ **ADOPT — the playback/storage layer for D & E-LITE** |
| **E-FULL — From-scratch WFF interpreter in Compose** | Medium | ✅ Yes | ✅ Yes | Very high (5–8 wks) | ⚠️ Fallback only; avoid unless D/E-LITE blocked |
| **C — Watch renders headlessly, streams frames to phone** | Exact (if permitted) | ❌ No | ❌ No (slot = 1) | High | ❌ **REJECT** — needs watch; and third-party bind is **REFUTED** |
| **B — Phone renders WFF via `watchface.client` headless** | Exact *if it ran* — but renders nothing | ❌ No | ❌ No | High | ❌ **DEAD** (REFUTED) |
| **A — Existing offline/headless WFF rasterizer on a plain runner** | n/a | — | — | Very high | ❌ **DEAD** — no such tool exists (REFUTED) |

---

## 3. Each approach: how it works, requirements, risks, verdict

### A — Offline/headless WFF rasterizer on a plain server/CI runner — ❌ DEAD

**How it would work:** parse `watchface.xml` + resources and rasterize to frames on a plain JVM/CI runner, no emulator, no watch.

**What it requires:** a standalone WFF renderer that runs on a plain JVM. **It does not exist.**

**Load-bearing claim → REFUTED.** The WFF renderer is a closed Wear OS system component; the platform "includes a watch face renderer component… [that] parses your WFF XML document and renders a watch face from it." Google's open-source `github.com/google/watchface` ships only an XSD spec, the `wff-validator.jar`, a memory-footprint evaluator (parses XML for memory only — never composites a pixel), and an optimizer. Samsung Watch Face Studio can't even import raw WFF XML and has no headless export. Verified against a full recursive repo tree — no renderer path.

**Key risk:** none to weigh — this is a hard availability wall, not a difficulty. Note: the CI already runs headless JVM jars fine (it fetches + runs the WFP validator), so integration was never the obstacle; *availability* is.

Sources: `developer.android.com/training/wearables/wff` · `github.com/google/watchface` (+ README, `third_party/wff/README.md`, recursive git tree) · `developer.samsung.com/watch-face-studio/user-guide` · `developer.android.com/jetpack/androidx/releases/wear-watchface`

---

### B — Phone renders the WFF face via `androidx.wear.watchface.client` headless APIs — ❌ DEAD

**How it would work:** call `createWatchFaceRuntimeControlClient(...)` → `HeadlessWatchFaceClient.renderWatchFaceToBitmap(params, instant, style, slots)` at successive instants to drive real animation. The `instant` argument is genuinely the ideal animation primitive.

**What it requires:** the Wear OS system runtime `com.google.wear.watchface.runtime` and a bindable `WatchFaceControlService` on the *same device*. Neither exists on a phone. The face APK declares `uses-feature android.hardware.type.watch required=true`, so it won't even install on a phone.

**Load-bearing claim → REFUTED.** `watchface.client` is a thin IPC shell — it binds a `WatchFaceControlService` via `bindService()` and marshals the bitmap back; it contains no WFF interpreter. A resource-only WFF APK exposes no such service, and the runtime that would provide one is Wear-OS-only ("Currently WearOS only supports the runtime for the Android Watch Face Format"). On a phone the bind returns `ServiceNotBoundException`. The library links (~few hundred KB) and does nothing. The repo confirms nothing renders XML today: `FacePreviewExtractor.kt` only pulls a static `preview` drawable.

**Key risk / why it's a wall:** the runtime is a privileged Google system component with no side-loadable distribution. Even legacy *code-based* faces (which carry their own control service) wouldn't help — these faces are resource-only. Chasing this burns effort that belongs on D/E.

Sources: `developer.android.com/jetpack/androidx/releases/wear-watchface` (1.2.0 notes) · `.../wff`, `.../wff/debug`, `.../wff/setup` · `developer.android.com/reference/androidx/wear/watchface/client/HeadlessWatchFaceClient` · `androidx.tech` `WatchFaceControlClient.kt` source · in-repo `faces/Kinetik-Metronome/.../AndroidManifest.xml`, `wear/.../FacePreviewExtractor.kt`

---

### C — Connected watch renders headlessly and streams frames to the phone — ❌ REJECT (narrow feature at best)

**How it works:** on the watch, `createWatchFaceRuntimeControlClient` binds the system runtime, `renderWatchFaceToBitmap` produces true frames of the installed face, and `play-services-wearable` Data Layer ships them to the phone.

**What it requires:** `watchface-client` on `:wear`, a *connected, awake* watch, and the face *installed* on that watch.

**Three hard gates:** (1) needs a connected watch — cannot power an offline gallery; (2) **Wear OS 6 Watch Face Push slot count = 1** (confirmed via Google's Androidify blog) — only the single installed face is renderable, so whole-gallery coverage would require destructive install→render→uninstall cycling that clobbers the user's active face; (3) `watchface-client` is deprecated and slated for removal.

**Load-bearing claim → REFUTED.** The claim that a normal third-party app is *permitted* to bind the system runtime's control service and render an arbitrary installed face has **zero supporting primary source and multiple primary-sourced barriers**: the `WatchFaceControlService` is guarded by `android:permission="com.google.android.wearable.permission.BIND_WATCH_FACE_CONTROL"` (extracted from Google's own `watchface-1.2.1.aar` manifest) — a `BIND_*` permission in a Google system namespace, which by Android convention (cf. `BIND_WALLPAPER`, `BIND_ACCESSIBILITY_SERVICE`) is signature/privileged and not grantable to Play-Store apps. Google's sanctioned third-party WFP surface grants exactly two permissions (`PUSH_WATCH_FACES`, `SET_PUSHED_WATCH_FACE_AS_ACTIVE`) and **no render/control permission**, and *mandates a static `preview.png`* instead. Google's own flagship WFP app (Androidify) previews with that static image, not live render. (Only the permission's literal `protectionLevel` string — non-public, consistent with system-only — is inferred.)

**Verdict:** PARTIAL only because the render *mechanism* is real and exact. The single salvageable feature is a live preview of *whichever face is already on the user's watch* — a nice-to-have, not the gallery solution.

Sources: `developer.android.com/jetpack/androidx/releases/wear-watchface` · `developer.android.com/blog/posts/bringing-androidify-to-wear-os-with-watch-face-push` (slot count = 1) · `.../reference/kotlin/androidx/wear/watchface/client/HeadlessWatchFaceClient`, `.../WatchFaceControlClient` · `.../training/wearables/watch-face-push/wear-os-app` (third-party permission set) · extracted manifests from `dl.google.com/android/maven2/androidx/wear/watchface/{watchface,watchface-client}/1.2.1/*.aar`

---

### D — Pre-bake exact previews on a Wear OS emulator in GitHub Actions CI — ✅ RECOMMENDED (primary)

**How it works:** CI (`ubuntu-latest`, which exposes `/dev/kvm` on all free runners since Jan 2024) boots one headless KVM-accelerated Wear OS emulator via `reactivecircus/android-emulator-runner` (`target: android-wear`). Per face: build a standard installable WFF APK from the `facepacks/*` source → `adb install -r` → `svc power stayon true` → activate with the documented primitive `adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --es watchFaceId <package>` → verify it became the active surface → capture frames → `ffmpeg` crop/encode a loop → bundle in `assets/anim/<key>.webp`. Nothing runs on the phone but media playback; no watch is ever involved.

**What it requires:** `ubuntu-latest` + `/dev/kvm`; `reactivecircus/android-emulator-runner`; **the Wear OS 5.1 / API 35 x86_64 system image** (see caveat below); `adb`; the existing `faces/<Series>-<Face>/app` installable WFF modules; `ffmpeg`; and a phone-side animated-media player (Approach F).

**Load-bearing claim → UNCERTAIN (feasible, with two corrections you must bake into the plan):**
- ✅ **Proven:** the activation broadcast is the official codelab primitive; the emulator is a documented WFF deploy target that contains the platform renderer; KVM acceleration on standard GitHub runners is real; the WFF-v2 faces render on a Wear OS 5/5.1 image.
- ⚠️ **Correction 1 (capture):** `adb screenrecord` **is not supported on Wear OS** — Google's own screenshots page says so and recommends **`scrcpy`** (`scrcpy --no-audio --no-window --record video.mp4 --time-limit=N`) or an `adb exec-out screencap -p` loop. The codelab proves *deploy+activate*, **not** headless capture — do not assume `screenrecord`.
- ⚠️ **Correction 2 (image SKU):** the documented WFF-v2 emulator is **Wear OS 5.1 / API 35**, not "Wear OS 5 / API 34." Pin API 35 x86_64 for KVM. (Avoid the API 36 image: docs flag `DashedArcLine`/`circularProgressIndicator` render bugs, and these faces use dashed strokes.)
- ⚠️ **Inferred:** the WFP-wrapped APKs (`<appId>.watchfacepush.<name>`) may not be directly settable as an active face — so **build a parallel standard installable WFF APK from the same `watchface.xml`** for the capture pass. The repo owns that source, so this is cheap.

**Key risks:** seamless-loop economics (below); SwiftShader software-GL may differ subtly from Pixel Watch GPU on glow/blend — needs a visual spot-check; emulator boot/first-run dismissal + confirming the *correct* face is active before capture is the flaky part (retries needed); crop AVD resolution to the 480 canvas without softening; complications render with emulator defaults (empty/placeholder).

**Seamless-loop economics (the one real wrinkle):** a uniform second-sweep only returns to the same angle after a full 60 s. Options: (a) capture 60 s + downsample hard (exact + seamless, heavy — multi-MB); (b) a short 3–6 s clip (small, but a visible jump); **(c) hybrid — bake the exact hand + base once, rotate on the phone (seamless + tiny + exact).** Option (c) is the sweet spot and is exactly E-LITE below. Finite-period faces (Metronome's 2 s sprite) loop perfectly as-is.

Sources: `developer.android.com/codelabs/watch-face-format` · `.../training/wearables/wff/setup`, `.../wff/debug`, `.../wff/release-notes` · `.../training/wearables/get-started/{emulator,screenshots}` · `.../training/wearables/versions/5-1` · `github.blog/changelog/2024-04-02-...android-virtualization...` · `github.com/ReactiveCircus/android-emulator-runner`

---

### E — Parse `watchface.xml` and re-draw natively in Compose (extract real assets by name) — ⚠️ PARTIAL

**How it works — two variants:**
- **E-FULL:** a from-scratch partial WFF interpreter in Kotlin — XML parse → scene tree; extract drawables/fonts by name from the bundled APK (`getPackageArchiveInfo` → `getResourcesForApplication` → `getIdentifier`, exactly as `FacePreviewExtractor` does for `preview`); render shapes/gradients/text/bitmap-fonts in a Compose `Canvas`; hand-write the WFF expression engine (`[SECOND]`, `[HOUR_*]`, `[CONFIGURATION.*]`, ternaries…); animate hands/sweeps/sprites/arcs via `withFrameNanos`.
- **E-LITE:** render *only* the real extracted moving element over a clean base plate.

**Load-bearing claim ("E-FULL is the only clean form; there's no cheap clean base") → REFUTED — and this is the most important correction in the whole report.** The premises are true (preview.png bakes the moving element at PREVIEW_TIME 10:08:36; no off-watch renderer exists; assets *are* runtime-extractable, `isShrinkResources=false` on all 18 facepacks). **But the conclusion is false: the repo's own bake pipeline already produces clean, hands-off base plates cheaply.** `faces/collection3-tools/gen/bake.mjs` builds `dial_t0..t4.png` from `staticItems = items filtered by !LIVE_KINDS` (i.e. the full dial with `mainHand`/`centerSecond`/`subSecond` removed), and Collection-3 analog faces (`faces/Aurum-Squelette`) already ship **both** `dial_t0.png` (clean base) **and** separate `hand_hour/minute/second` sprites side-by-side. So an **E-LITE** form — composite an exact clean base + exact hand sprite, rotate procedurally — is faithful and cheap, and does **not** require reimplementing the runtime. E-FULL is the *only* clean form solely under the narrowest reading (runtime extraction from *today's* WFP APKs with no re-bake, for faces whose dial furniture is drawn from WFF primitives rather than shipped as a base PNG).

**Where each variant lands:**
- **E-LITE is excellent** for analog/rotor/sprite motion where a separated base + sprite is available (or cheaply re-baked from the repo SVG source). Seamless, tiny, exact-pixel, watch-free, whole-gallery — this is half of the recommendation.
- **E-FULL** is a 5–8 week from-scratch subset of a proprietary runtime with a long font/layout fidelity tail (~15 TTFs + a bitmap font), and its motion payoff covers only **9 of 18** faces (correcting the scout's undercount: **Pulsar animates per-second via `<Arc><Transform target=endAngle value=[SECOND]*6>`, not Sweep**). The other 9 are minute-cadence-only by the designers' own WFF fallbacks — a live render looks identical to the frozen preview except the digits show real time. **Complications cannot be faithful** phone-side (no data provider → empty/placeholder, diverging from the preview's populated numbers). This is what threatens the owner's fidelity bar.

**Bundle/runtime:** zero bundle increase (assets already shipped); CPU is the concern — animate only the detail screen + on-screen hero cell, cap sweeps at ~15 fps.

Sources: `developer.android.com/training/wearables/wff` · `github.com/google/watchface` · in-repo `faces/collection3-tools/gen/bake.mjs` (+ `wff.mjs:978` PREVIEW_TIME), `faces/Aurum-Squelette/.../drawable-nodpi` (dial_t0 + hand sprites), `facepacks/*/build.gradle.kts` (`isShrinkResources=false`), `wear/.../FacePreviewExtractor.kt`, `app/.../ui/components/FaceDial.kt` (`rememberSecondsAngle`)

---

### F — Storage + playback: animated WebP + procedural overlay — ✅ ADOPT (delivery layer)

**How it works:** bake each face's true periodic motion to an **animated WebP at 480×480**, played by **Coil 3** (`coil-gif` `AnimatedImageDecoder` → platform `AnimatedImageDrawable`) in the existing `FaceDial` surfaces. Grid: request small decode sizes (~150–256px), gate animation to on-screen cells only, freeze when backgrounded / reduce-motion. Detail: one full-480 `AsyncImage`. **Loop strategy:** bake only each face's *natural period* so it wraps seamlessly (Metronome = 2 s; shimmer/pulse = 2–4 s). **Do not bake 60 s sweeps or live digital time** — draw those procedurally in Compose (`rememberSecondsAngle` → `Canvas`/`rotate`, plus live `TimeText`). The WebP carries only pixels you can't cheaply synthesize.

**Load-bearing claim → UNCERTAIN (conclusion correct; one number wrong — use the corrected justification):**
- ✅ True: `ImageDecoder`/`AnimatedImageDrawable` (API 28+; app minSdk 30 clears it) natively decode + loop animated WebP via the graphics path, consuming **no MediaCodec video-decoder instance**. Video (MP4/WebM) does consume one.
- ❌ **Wrong number:** "OEMs commonly cap concurrent decoders around 4, CTS-enforced" is false. The Android CDD mandates a CTS-enforced **minimum of 6** concurrent hardware video decoders (a floor, not a cap; the "4" is AOSP's *secure*-decoder example). **Correct the rationale, keep the decision:** "Android guarantees only ~6 concurrent hardware video decoders — far short of an 18–43-cell animating grid — whereas animated WebP consumes none." Also note a recycling `LazyGrid` only decodes visible cells (~4–9), so "18–43 simultaneous" slightly strawmans video — but WebP is still the correct, safer grid format.

**Requirements:** `io.coil-kt.coil3:coil-compose` + `coil-gif`; register `AnimatedImageDecoder`; `libwebp img2webp`/`ffmpeg` encode step in CI; a **mandatory** visible-cell + background + reduce-motion animation gate; the procedural overlay for sweep/digital faces.

**Risks:** animated-WebP-in-Compose first-frame flash → fix with fixed `AsyncImage` size + `preview.png` placeholder; lossy WebP bands on dark gradient faces (Vespera/Aether) → use q≥90/lossless for those; APNG and GIF both rejected (APNG not natively decoded; GIF 256-color).

**Bundle:** short loops ≈ 150–400 KB each → ~3–7 MB for 18 faces, ~7–17 MB for 43. A 60 s sweep would be multi-MB per face — another reason to draw sweeps procedurally.

Sources: `developer.android.com/reference/android/graphics/{ImageDecoder,drawable/AnimatedImageDrawable}` · `coil-kt.github.io/coil/gifs/` · `source.android.com/docs/core/media/oem` · CDD 13/15/16 (`source.android.com/docs/compatibility/*`) · `developers.google.com/speed/webp/docs/img2webp` · in-repo `app/build.gradle.kts` (minSdk 30)

---

## 4. Recommendation & phased implementation

**Primary pick: a build-time hybrid = D (exact frames) + E-LITE (separated layers + procedural rotation) delivered via F (WebP + Compose overlay).** This is the only combination that is simultaneously watch-free, whole-gallery, and pixel-exact — and it directly answers the owner's rejection of a fake hand, because every moving pixel is either the real renderer's output or the real source art.

The key unlock: **you already have a build-time renderer.** The repo's `bake.mjs` compiles faces from SVG and can emit hands-off base plates + separated hand sprites today. For those faces you don't even need the emulator — you need a bake config change. The emulator (D) is the fallback that guarantees exact pixels for anything the SVG bake can't cleanly decompose.

**Classify the 18 faces by motion type and route each to the cheapest exact path:**

| Face type | Count | Path | Result |
|---|---|---|---|
| Uniform second-sweep / analog hands (Solstice, Vespera-Aurum/Meteorite/Opaline, Escapement/Orrery/Turbine) | ~8 | **E-LITE:** bake exact base + hand sprite (repo SVG bake, or emulator single-frame), rotate in Compose | Seamless, tiny, exact |
| Per-second arc (Pulsar) | 1 | **E-LITE:** bake base, draw `drawArc(endAngle=[SECOND]*6)` in Compose | Seamless, exact |
| Finite sprite (Kinetik-Metronome, 48-frame/2 s) | 1 | **D + F:** capture/encode a 2 s seamless WebP | Exact, small |
| Live-digital time (Turbine digits, Settype) | ~4 | **Compose `TimeText`** over baked base | Live real time |
| Minute-cadence only (both Aether, Vespera-Salon/Noir, Odometer) | ~4 | **Leave on existing `preview.png`** | No visible motion lost |

**Phase 1 — Delivery layer + fastest visible win (F + E-LITE for sweeps).** ~2–4 days. Add Coil 3, register `AnimatedImageDecoder`, swap `Image(painterResource)` → `AsyncImage`, add the visible-cell/reduce-motion gate. Re-bake `dial_t0` base + hand sprites for the ~8 analog/sweep faces via the existing `bake.mjs` (`!LIVE_KINDS` filter is already there), extend `FaceDial`'s `rememberSecondsAngle` to rotate the real extracted hand sprites. Ship it — half the gallery now moves, seamlessly, exact-pixel, no emulator. *Push + tag per your phase protocol.*

**Phase 2 — Emulator capture for non-decomposable motion (D).** ~2–4 days. Stand up the API 35 x86_64 `android-wear` emulator job with KVM; build standard installable WFF APKs from `facepacks/*`; install→activate (`DEBUG_SURFACE set-watchface`)→verify-active→capture via **`scrcpy` or `screencap` loop (not `adb screenrecord`)**→`ffmpeg`→seamless WebP. Cover Metronome + any shimmer/glow. Cache the ~1 GB image; expect ~15–25 min added CI for 18 faces.

**Phase 3 — Digital + polish.** Live `TimeText` overlays for digital faces; per-face capture-window tuning; visual spot-check emulator output vs a real Pixel Watch for glow/blend fidelity; file-size tuning; extend to 43 faces.

**Fallback (only if D and the SVG bake both stall): E-FULL scoped to the 9 motion faces.** Render the *entire* scene for those 9 in a Compose WFF interpreter (no base-plate needed, no double-element artifact), leave the other 9 on static previews. ~15–20 dev-days, medium fidelity, long font tail. Choose this only if CI emulator flakiness proves intractable — it's more work for less fidelity than the hybrid.

**Explicitly do not:** pursue B or C for the gallery (both refuted for the whole-gallery, no-watch goal), and do not bake 60 s sweeps or trust the "~4 decoder" rationale — draw sweeps procedurally and justify WebP by the ~6-decoder floor.

---

## 5. Reconciling with the earlier decision

The owner rejected a **generic fake gold second hand** and wants the **original** animation; only **~7/18** faces were thought analog. This recommendation is consistent and improves on the prior understanding:

- **No fake hands anywhere.** Every moving pixel is either the real WFF runtime's output (D, emulator) or the face's own extracted source art rotated at its real pivot/tint (E-LITE). This is the literal opposite of the rejected fake hand — it is the actual `hand_second.png` from the actual face, moving on the actual dial. The fidelity ceiling is exact.
- **Corrected motion census: 9, not 7.** The Sweep-only count undercounted — **Pulsar animates per-second via an `<Arc>` transform**, not a Sweep. True sub-minute motion = **9/18** (Solstice, Pulsar, Escapement, Orrery, Turbine, Metronome, Aurum, Meteorite, Opaline). The other **9** are minute-cadence by the designers' own WFF fallbacks: animating them adds nothing visible over the static preview except live digits, so leaving them on `preview.png` (with a live `TimeText` overlay where they show time) loses no real motion. That keeps effort concentrated where it shows.
- **The fake-hand overlay trap is now understood and avoided.** You cannot overlay a real hand onto the existing `preview.png` — that PNG bakes the hand at 10:08:36, so you'd get a double-hand artifact. That is *why* the hybrid uses a **hands-off base plate** (from `bake.mjs`'s `!LIVE_KINDS` filter or the emulator), not the shipped preview. This is the specific mistake the prior fake-gold-hand attempt stumbled into, and the recommendation routes around it structurally.
