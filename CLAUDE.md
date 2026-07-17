# Dialed — project context (read first)

Dialed is a **native Kotlin/Compose Watch Face Push (WFP) marketplace** for Wear OS 6 — a **living
hub** that bundles the fablecollection watch-face collection, pushes faces to the watch via WFP, and
doubles as the developer's **laptop-free test harness**. Repo: **github.com/aucksy/dialed** (live; CI
green; shipped **`dialed-v0.22.0`**). Build guide: `watchface-marketplace-build-guide.md`.

**▶ ACTIVE ROADMAP: `docs/ASSESSMENT.md` + `docs/IMPLEMENTATION-PLAN.md`** (2026-07-15 end-to-end
Fable assessment of both apps). **Start there.** The plan is 8 dependency-sliced phases —
1 hygiene (v0.19.0 ✅) · 2 collections IA + remote catalog config · 3 per-collection Play Billing ·
4 store readiness · 5 default face · 6 living gallery · 7 Collection-3 scale-out · 8 wear polish —
each with exact scope, acceptance criteria, and a ready-to-paste kickoff prompt. Its §0 carries the
standing rules and the owner-approved product decisions (collections model, free-face trials,
per-collection one-time purchases, `config/catalog.json` as the admin surface, prices in Play Console).

`docs/IMPROVEMENTS-PLAN.md` is the **superseded** v0.3.0→v0.10.0 roadmap — history only; its Progress
table records what each of those tags shipped.

**Design source of truth:**
- Phone: `Dialed Watch-Face Store-handoff/dialed-watch-face-store/project/Dialed - Design Spec.dc.html` + `HANDOFF.md`.
- **Wear companion: `Dialed Wear OS app Designs/dialed-watch-face-store/project/Dialed Watch - Design Spec.dc.html` + `HANDOFF-WATCH.md`.**

## Non-negotiable constraints
- **Wear OS 6 / API 36 only** for WFP. Gate every use with `WatchFacePushManager.isSupported()`.
- **Slot = 1** on WO6 → installing face B over A = `updateWatchFace` (full replace), never a 2nd slot.
- **One face per APK**, package `com.dialed.app.watchfacepush.<series>.<face>`, `hasCode=false`, no Activity/Service, minSdk 33, signed with the faces key (≠ `:app` key).
- **Each face APK needs a validation token**; tokens don't expire (regenerate only when the APK changes).
- **One-time INAPP** (`unlock_all_faces`): acknowledge within 3 days, never consume, restore on launch. **Debug builds default to UNLOCKED** (free testing) via `EntitlementStore`.
- **NO local Android builds** (RAM). Everything cloud-built via GitHub Actions; **first CI run is the verifier**. But you CAN validate CI-built face APKs locally (see local-validation-loop below).

## Verified deps/toolchain (see WFP-RESEARCH.md; re-verify occasionally)
- WFP lib `androidx.wear.watchfacepush:watchfacepush:1.0.0` (stable). Play Billing `billing-ktx:9.1.0`.
- **WFP validator: `com.google.android.wearable.watchface.validator:validator-push-cli:1.0.0-alpha09`** (alpha10 does NOT exist — earlier note was wrong). Self-contained fat jar on Google Maven.
- Toolchain pinned to Google's WatchFacePush sample: **AGP 9.1.1 · Gradle 9.4.1 · Kotlin 2.3.21 · Compose BOM 2026.04.01 · compileSdk 36 · JVM 17**. AGP 9 has built-in Kotlin — do NOT apply `org.jetbrains.kotlin.android` (fatal); apply only `android-application`/`library` + `compose-compiler`.

## Architecture
- **`:app`** — phone storefront (Compose) + Data-Layer sender (`transport/WatchConnection.kt` = `WatchBridge`). CI bundles face APKs → `assets/faces/<key>.apk` + tokens → `assets/tokens/<key>.token` (gitignored, built fresh each run). Billing is still a **debug-only stub** — real Play Billing is plan Phase 3.
- **`faces/`** — git submodule → github.com/aucksy/fablecollection (the 43-face library, one source of truth). Two families: **A** = the original 18 (WFF v2 @ 480 canvas, colour swatch); **B** = Collection 3's 25 (WFF v1 @ 450, five baked themes, ~2.1 MB of art each vs family A's ~0.36 MB). `tools/gen-facepacks.mjs` keeps an explicit `BUNDLED_FACES` allowlist so a submodule bump can't silently change the store; CI re-runs the generator and fails on any drift from the committed output.
- **`facepacks/<key>/`** — GENERATED per-face WFP packaging modules. `wff-res/` = a COPY of the submodule's res with `watchface.xml` patched to **bare resource names**; `build.gradle.kts` overrides applicationId. Regenerate with `node tools/gen-facepacks.mjs "<ABSOLUTE-REPO-ROOT>"` — **pass the root explicitly**, the space in "Dialed App" breaks the script's own path resolution.
- **`:wear-common`** — shared phone↔watch protocol (`WearConstants`, dependency-free) + `WatchFaceActivationStrategy`. Wire enums/bytes are **ordinal/value-stable: append only**.
- **`:wear`** — the WFP bridge + concierge UI (`wfp/` = repository, `DialedListenerService`, `TransferSession`, `WfpStateStore`, `FacePreviewExtractor`). **`:watchface`** (bundled default face) = plan Phase 5.
- **Signing:** `:app` + `:wear` share the committed **stable** `dialed-app-debug.keystore` (a per-run debug key gave every release a new signature → "app not compatible"). `dialed-faces.keystore` signs the faces and MUST stay a different key (WFP rule).

## ⚠ WFP FACE-VALIDATION GOTCHAS (these blocked everything — the 18 faces were never WFP-validated before)
Google's validator rejected all 18 for two reasons; both fixed in `tools/gen-facepacks.mjs`:
1. **`@drawable/` prefix** → memory-footprint "asset @drawable/X not found" (known bug google/watchface#52: `WatchFaceResourceCollector` looks up the literal `resource` attr against a map keyed by bare name). Fix: the generator copies each face's res into `facepacks/<key>/wff-res` and strips `@drawable/` → bare `resource="X"` (canonical WFF, renders identically).
2. **stub `classes.dex`** → "files not allowed". Fix: facepack `isMinifyEnabled=true` (strips dex; KEEPS arsc resource names — path-shortening res/0I.png is harmless since the validator resolves by NAME) + packaging `excludes += "kotlin/**"` (AGP 9 built-in Kotlin bundles kotlin-stdlib metadata, also disallowed).
Dead-ends ruled out: NOT density (nodpi is fine), NOT PNG-decode (ImageIO reads them).

3. **Bare `displayName="theme_label"`** (v0.21.0) → the WATCH's face-customisation editor renders the
   literal resource KEY ("theme_label", "slot_1") instead of the human label. Every Collection-3 face
   writes bare names while defining the proper strings in `values/strings.xml`; family A correctly writes
   `@string/cfg_theme`. Fix (`patchDisplayNames` in the generator): rewrite `displayName="x"` →
   `@string/x` whenever the face defines string `x` — in the facepack COPY, never the submodule, exactly
   like the `@drawable/` strip. ⚠ Note the asymmetry that makes this easy to get backwards: for
   **`resource=`** (images) the bare name is canonical and `@drawable/` is the bug; for **`displayName=`**
   (strings) the `@string/` reference is canonical and the bare name is the bug. Opposite directions.

## Local WFP validation loop (rebuild each session — scratchpad is temporary)
1. Portable JDK17/JRE17: `curl -sL "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk" -o jre17.zip` (JDK variant for `jshell`). Extract with PowerShell `Expand-Archive`. (Machine's default java is 8 = too old; validator needs class v61.)
2. Validator jar: `https://dl.google.com/android/maven2/com/google/android/wearable/watchface/validator/validator-push-cli/1.0.0-alpha09/validator-push-cli-1.0.0-alpha09.jar`.
3. `java -jar validator-push-cli-1.0.0-alpha09.jar --apk_path=<face>.apk --package_name=com.dialed.app` → on success prints `generated token: <base64>:<base64>`.
4. Download CI artifacts w/o gh CLI: `git credential fill` → `curl -H "Authorization: Bearer $TOKEN"` the Actions API (NEVER print the token). Extract zips with PowerShell (tar chokes on `C:` paths).

## ⚠ WFP APP-LAYER RULES (each one was paid for with an on-wrist cycle — see `Resources/wff-watchface.skill` → `references/companion-app.md`)
- **Timeout EVERY WFP binder call** (`withTimeoutOrNull`) and **rethrow `CancellationException` before any broad `catch`** — an unbounded call on a cold service hangs the receive coroutine, its `finally` never runs, the single-transfer lock leaks, and every later push answers BUSY ("your watch is busy") until force-stop.
- **Never downgrade a real install to FAILED**: commit success first, wrap post-install side-effects in `runCatching`. **Finalize exactly once, in a `finally`, defaulting FAILED.**
- **Activation is a platform once-ever budget.** Always ATTEMPT the unattended set-active when we don't own the active face and hold the permission; let the platform's own refusal be the authority; never latch locally; a refused set → the manual long-press coach, **never** a false "Dialed in.". Auto-apply keeps working only via the ownership chain (updating the slot we already own). First-of-day auto-apply after the user switches away is **platform-limited — closed, not fixable**.
- **Never `channelClient.close()` after `sendFile`** (sendFile closes its own stream → receiver sees `CLOSE_REASON_NORMAL` = success; closing yourself surfaces `REMOTE_CLOSE` and breaks every transfer).
- Receive/install runs on a **process-scoped** scope, not the service `Job` (GMS destroys an idle `WearableListenerService` the moment a callback returns).
- Home/glance must show the **genuine** slot state (`listWatchFaces()` + `isWatchFaceActive`), never a "last pushed" cache; trust a cached name/preview only when the stored package matches what WFP reports installed.
- Wear round hero layouts: **no centred `Column`** (it overflows + clips) — `BoxWithConstraints` + fractional anchors per the spec; let the EdgeButton overlay the bezel. `ScrollInfoProvider` is in **`wear.compose.foundation`**, not material3.

## Wear bridge design — reference: Google androidify
Dep-free protocol already in `:wear-common/WearConstants`: phone `MessageClient.sendRequest("/dialed/initiate_transfer", "<transferId>\n<token>")` → watch replies 1 byte (proceed) → phone `ChannelClient.openChannel("/dialed/transfer_apk/<id>")` + `sendFile(apkUri)` → watch receives, installs via WFP, sends `/dialed/finalize_transfer/<id>` back.
- **`:wear`**: `WatchFacePushRepository` (androidx WFP: `listWatchFaces()` → `remainingSlotCount`/`installedWatchFaceDetails.first().slotId`; `addWatchFace(pfd,token)`/`updateWatchFace(slotId,pfd,token)` catching `AddWatchFaceException`/`UpdateWatchFaceException`; `setWatchFaceAsActive(slotId)` one-shot; `isWatchFaceActive(pkg)`) + `DialedListenerService : WearableListenerService` (3 intent-filters) + minimal Wear-Compose status/permission UI (build from `HANDOFF-WATCH.md`) + manifest (WFP perms `PUSH_WATCH_FACES` + `SET_PUSHED_WATCH_FACE_AS_ACTIVE`, capability `dialed_wfp_install` in `res/values/wear.xml`, `DEFAULT_WATCHFACE_VALIDATION_TOKEN` meta-data for Phase 5).
- **`:app`**: `WatchConnection` (CapabilityClient discover `dialed_wfp_install` + `WearableApiAvailability` guard; MessageClient/ChannelClient sender reading `assets/faces/<key>.apk`+`assets/tokens/<key>.token`; APK→PFD via cacheDir copy) + `PushToWatchSheet` (4-beat, HANDOFF.md F3) + wire the Install button.
- **Gotchas**: never persist slotId (re-`listWatchFaces()`); set-active is one-shot (max 1 rejection → route to settings); compute activation strategy BEFORE install; single concurrent transfer (AtomicBoolean + transferId match + timeout); `updateWatchFace` same-pkg = iterative (keeps style), different-pkg = full replace.
Exact androidify reference files (re-fetch raw): `wear/.../watchfacepush/WatchFaceOnboardingRepository.kt`, `wear/.../service/AndroidifyDataListenerService.kt`, `watchface/.../transfer/WearAssetTransmitter.kt` + `WearDeviceRepository.kt`, `wear/common/.../{WearableConstants,Messages,WatchFaceActivationStrategy}.kt` in github.com/android/androidify. (androidify uses Hilt + kotlinx-serialization ProtoBuf — Dialed drops both.)

## THE add-a-face routine
`node scripts/add_face.mjs` (sync submodule + regenerate facepacks/catalog/previews) → commit → tag `dialed-vX.Y.Z` → push --follow-tags → CI builds+validates+bundles.

## Ship loop
Edit → compile-review → **adversarial logic review** → fix → commit → **bump versionCode/versionName in BOTH `app/` and `wear/`** → push main → `git tag dialed-vX.Y.Z` → **push the tag explicitly** (`git push origin <tag>`; a lightweight tag does NOT ride `--follow-tags`) → poll CI green → paste BOTH direct APK links (`releases/download/<tag>/<tag>-phone.apk` and `-wear.apk`) + what to re-test. Review BEFORE tagging, never after. Git author `simpleapps108@gmail.com`. No `gh` CLI (poll/download CI via the Actions API + `git credential fill` token — never print it).

## Phase status (see docs/IMPLEMENTATION-PLAN.md for what's next)
- ✅ **Pipeline** — all 18 faces pass WFP validation; CI builds facepacks → validates → tokenises → bundles into `:app`, then builds both APKs (same stable key → the Data Layer pairs them).
- ✅ **Phone storefront** (Home/FaceDetail/Paywall/Settings/Onboarding + the full design system).
- ✅ **Wear bridge + transport** — shipped and hardened on-wrist across v0.2.1 → v0.18.0: push-reporting, installed-state + uninstall, auto-apply reliability + exit-to-face (v0.15), WFP call timeouts / transfer-lock leak (v0.16), genuine Home state (v0.17), working "Open on phone" (v0.18).
- ✅ **Face quality** — complication alignment (v0.4.0), smooth motion (v0.5.0/v0.6.0), canvas → 480 fleet-max (v0.8.0/v0.9.0), icon complication labels across 17 faces (v0.11–v0.12).
- ✅ **v0.19.0 hygiene** (plan Phase 1) — release paywall can no longer grant a free unlock; push-job race + cancellation hygiene; uninstall errors surfaced; honest unsupported-watch path (`RESPONSE_UNSUPPORTED` + query sentinel); restored-detail crash guard; `singleTask`; 48dp targets; dead code/controls removed.
- ✅ **v0.21.0 = plan Phase 2C** — store 18 → **43** faces (Collection 3 bundled). M2 fixed: detail chips now derived from each face's real `<ComplicationSlot>`s. Home's filter row made scrollable (see below). CI gained a generator-drift gate, a 43/43 count gate, and asset/APK size logging.
- ✅ **v0.22.0 = VAKT complications** (submodule bump; face-side only, no app code). Assigning any provider to a VAKT register used to paint an opaque flat disc over the machined dial. A slot is now a FRAME (permanent dial art) plus swappable CONTENT, with the empty-state scale/needle in a `<Complication type="EMPTY">` block the platform swaps out; all 8 types render; VAKT is the **first WFF v2** face family (GOAL_PROGRESS + WEIGHTED_ELEMENTS; CI reads the version per-face from the manifest, so this needed no CI change). ⚠ **The facepack template hardcodes `minSdk = 33`** while a v2 face wants 34 — inert because WFP is Wear OS 6 only, but revisit if these are ever sold standalone. Full detail + the traps: `faces/collection3-tools/VAKT-COMPLICATIONS-PLAN.md` §7.
- ⏭️ **Next: plan Phase 2D = `dialed-v0.23.0`** — collections IA + `config/catalog.json` + free faces + coming-soon tiles. Then 2B colour parity (v0.24.0), 2E showcase (v0.25.0), Billing (v0.26.0), store (v0.27.0), default face (v0.28.0), living gallery (v0.29.0), wear polish (v0.30.0); 2F gap builds roll alongside.
- ⚠ **Tag numbers are assigned at SHIP time, never reserved** (renumbered 2026-07-17). `v0.20.0` was held for 2B and never tagged — it is now **permanently unusable** (versionCode is 22; an APK at code 20 can't install over it). `v0.22.0` was reserved for 2D and got spent by the out-of-band VAKT ship. Don't hold a number for a future phase.

## ⚠ Phase 2C findings that contradict the plan (read before re-attempting them)
- **The "icon-label rollout to the 25" is a NO-OP — do not redo it.** 2C step 1 assumed the 25 needed the
  v0.12.0 transform that replaced hardcoded `<Default>` labels with `[COMPLICATION.MONOCHROMATIC_IMAGE]`.
  Verified against every file: the 18 icon-less faces have **no hardcoded label of any kind** — no `<Default>`,
  no `[COMPLICATION.TITLE]`, no literal text inside *or* outside a `<Complication>` block, and no baked
  `lbl_*` sprites (only Vakt-One has `ic_*`, and it already uses provider icons). They render `[COMPLICATION.TEXT]`
  only, so they are **already swap-safe** — the "still says Steps" bug cannot occur. *Adding* icons would be a
  redesign, not a transform: the slots are 34×26 – 64×26 (no room beside the value), and each would need a
  tinted `PartImage` inserted into all five baked theme groups plus the dark group, per slot — against 450-canvas
  art we'd also have to re-lay out. Audit §4 says leave the 25 alone; this agrees.
- **The two Terra renames are SKIPPED — three independent sources say the current names are right.** The audit's
  §6/§11-q6 silkscreen check *failed*: `TERRA SOLSTICE` and `TERRA / MERIDIAN LINE` are painted into the dial art
  of every theme. The handoff spec (`faces/collection3-tools/spec/cat-c.js`) names them "Terra Solstice" /
  "Terra Meridian Line", and each face's own on-watch label (`app_name`) says the same. Renaming them in the
  storefront would contradict the watch on the wrist. Revisit only if the art is re-baked from spec.
- **Terra-Compass still has the leaked design note** `COMPASS · ROSE IS STATIC` baked into its dial art
  (`spec/cat-c.js:187`). Audit §11-q5, still an open owner call — it is a one-line spec edit + re-bake, not a hand edit.
- **Home's filter row was already overflowing** and 2C would have made it unreachable: it was a plain `Row`
  (no wrap, no scroll) and the series count went 5 → 10 (11 chips ≈ 900dp against ~312dp usable), so half the
  store would have been unfilterable. Made horizontally scrollable as a stopgap; **Phase 2D's collection cards
  replace this surface entirely.**
- **Size — and the delivery model.** Family B is ~6× heavier per face than family A (5 baked themes of
  full-canvas art), so 43 faces = **~35 MB of assets → a ~70 MB phone APK**. CI prints both to the job summary.
  ⚠ **The ceiling is Play's 200 MB compressed AAB base-module limit, NOT 100 MB** — 100 MB is the legacy raw-APK
  limit and does not apply, because Play requires an AAB for new apps (the Phase 4 lane). So **70 MB ≈ 35% of the
  real ceiling: nothing forces a delivery change now.** The trigger is scale — the reserve pool is 149 more designs
  (see docs/CATALOG-AUDIT.md §13), and the whole 192 would breach 200 MB.
  **When it does bite, the answer is Play Asset Delivery / Feature Delivery (per-collection packs — which is the
  billing model anyway), or a CDN — NOT a backend database.** Faces are static files; the catalog is already a
  static JSON (plan D3). ⭐ **The codebase is already shaped for this:** `FaceAssetProvider` (WatchConnection.kt:60)
  is the ONLY thing that touches `assets/` — it hands the push path `stageApk() -> File` + `readToken() -> String`,
  and everything downstream (PFD, Data-Layer channel, watch-side WFP install) consumes a file + a token and cannot
  tell where they came from. Swapping bundled → downloaded is a reimplementation of those two methods plus a cache;
  the hard-won wear bridge is untouched. Keep that seam intact.

## Known deviations / TODO
- **Billing is a debug-only stub.** `EntitlementStore` is one boolean; debug builds default UNLOCKED; the paywall/restore buttons are `BuildConfig.DEBUG`-gated no-ops in release. Real per-collection Play Billing = plan Phase 3; the entitlement schema becomes a per-collection set in Phase 2.
- **Store readiness has not started** — CI ships debug-signed APKs only (no upload key, no AAB, no privacy policy, no listing). Plan Phase 4. ⚠ verify `com.dialed.app` is still free on Play FIRST: a collision forces a new applicationId, which re-mints every face token.
- Instrument Sans not wired (metrics match; typeface pending — `ui-text-google-fonts` + certs); wear side uses the default typeface too.
- `FaceDial` renders the real `preview.png` (static). The `ticking`/`rememberSecondsAngle` hook exists but is **currently unused** — it's the landing point for the real per-face animation (plan Phase 6 / `docs/research/R7`).
- ~~Detail "feature" chips are series-level guesses~~ — **fixed in v0.21.0 (M2)**: `gen-facepacks.mjs` `deriveFeatures()` reads each face's real `<ComplicationSlot>`s (default provider → chip, + slot count, + a derived Always-on). Note it counts **slots**, not `<Complication>` blocks — a slot carries one block per supported type (Arclight-Solstice = 5 slots / 10 blocks), so counting blocks would inflate every chip.
- Watch receive progress is **indeterminate** (the Data Layer file transfer exposes no byte progress); the design's determinate % is not literally reproducible. The receive screen also shows a placeholder, not the incoming face (plan Phase 8).
- Phone F2 shared-element / F3 transfer beats / F4 unlock sheen / F6 logo sweep are not built (plain crossfade + indeterminate bar today).
- `DEFAULT_WATCHFACE_VALIDATION_TOKEN` meta-data intentionally omitted until the default face exists (plan Phase 5) — the placeholder comment sits in `wear/src/main/AndroidManifest.xml`.
- Wear Home face is **73dp per the spec**; the owner finds it small/high — a deliberate deviation is queued (plan Phase 8).
