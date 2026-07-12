# Dialed — project context (read first)

Dialed is a **native Kotlin/Compose Watch Face Push (WFP) marketplace** for Wear OS 6 — a **living
hub** that bundles the whole fablecollection watch-face collection, sells it as a **one-time
purchase**, pushes faces to the watch via WFP, and doubles as the developer's **laptop-free test
harness**. Repo: **github.com/aucksy/dialed** (live; CI green; shipped `dialed-v0.2.1`). Build guide:
`watchface-marketplace-build-guide.md`.

**▶ ACTIVE ROADMAP (post-v0.2.1): `docs/IMPROVEMENTS-PLAN.md`** — the 8 owner-reported on-device issues
(resolution, installed-state/uninstall, animated showcase, wear setting-face animation, carousel
visibility, motion jank, complication alignment) phased v0.3.0→v0.8.0. Start there; keep its Progress
table live. Phase 0 is a research spike that unblocks the faces-repo fixes.

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
- **`:app`** — phone: Compose storefront + (Phase 2) Billing + (Phase 4) Data-Layer sender. CI bundles face APKs → `assets/faces/<key>.apk` + tokens → `assets/tokens/<key>.token` (gitignored, built fresh each run).
- **`faces/`** — git submodule → github.com/aucksy/fablecollection (the 18-face library, one source of truth).
- **`facepacks/<key>/`** — GENERATED per-face WFP packaging modules. `wff-res/` = a COPY of the submodule's res with `watchface.xml` patched to **bare resource names**; `build.gradle.kts` overrides applicationId. Regenerate with `tools/gen-facepacks.mjs` (idempotent).
- **`:wear-common`** — DONE. Shared phone↔watch protocol (`WearConstants`, dependency-free) + `WatchFaceActivationStrategy`.
- **`:wear`** — NEXT (Phase 3-4): WFP bridge. **`:watchface`** — Phase 5 (bundled default face).
- `dialed-faces.keystore` — shared throwaway faces key (≠ `:app` key). `dialed-app` currently debug-signed in CI.

## ⚠ WFP FACE-VALIDATION GOTCHAS (these blocked everything — the 18 faces were never WFP-validated before)
Google's validator rejected all 18 for two reasons; both fixed in `tools/gen-facepacks.mjs`:
1. **`@drawable/` prefix** → memory-footprint "asset @drawable/X not found" (known bug google/watchface#52: `WatchFaceResourceCollector` looks up the literal `resource` attr against a map keyed by bare name). Fix: the generator copies each face's res into `facepacks/<key>/wff-res` and strips `@drawable/` → bare `resource="X"` (canonical WFF, renders identically).
2. **stub `classes.dex`** → "files not allowed". Fix: facepack `isMinifyEnabled=true` (strips dex; KEEPS arsc resource names — path-shortening res/0I.png is harmless since the validator resolves by NAME) + packaging `excludes += "kotlin/**"` (AGP 9 built-in Kotlin bundles kotlin-stdlib metadata, also disallowed).
Dead-ends ruled out: NOT density (nodpi is fine), NOT PNG-decode (ImageIO reads them).

## Local WFP validation loop (rebuild each session — scratchpad is temporary)
1. Portable JDK17/JRE17: `curl -sL "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk" -o jre17.zip` (JDK variant for `jshell`). Extract with PowerShell `Expand-Archive`. (Machine's default java is 8 = too old; validator needs class v61.)
2. Validator jar: `https://dl.google.com/android/maven2/com/google/android/wearable/watchface/validator/validator-push-cli/1.0.0-alpha09/validator-push-cli-1.0.0-alpha09.jar`.
3. `java -jar validator-push-cli-1.0.0-alpha09.jar --apk_path=<face>.apk --package_name=com.dialed.app` → on success prints `generated token: <base64>:<base64>`.
4. Download CI artifacts w/o gh CLI: `git credential fill` → `curl -H "Authorization: Bearer $TOKEN"` the Actions API (NEVER print the token). Extract zips with PowerShell (tar chokes on `C:` paths).

## Wear bridge plan (Phase 3-4, next) — reference: Google androidify
Dep-free protocol already in `:wear-common/WearConstants`: phone `MessageClient.sendRequest("/dialed/initiate_transfer", "<transferId>\n<token>")` → watch replies 1 byte (proceed) → phone `ChannelClient.openChannel("/dialed/transfer_apk/<id>")` + `sendFile(apkUri)` → watch receives, installs via WFP, sends `/dialed/finalize_transfer/<id>` back.
- **`:wear`**: `WatchFacePushRepository` (androidx WFP: `listWatchFaces()` → `remainingSlotCount`/`installedWatchFaceDetails.first().slotId`; `addWatchFace(pfd,token)`/`updateWatchFace(slotId,pfd,token)` catching `AddWatchFaceException`/`UpdateWatchFaceException`; `setWatchFaceAsActive(slotId)` one-shot; `isWatchFaceActive(pkg)`) + `DialedListenerService : WearableListenerService` (3 intent-filters) + minimal Wear-Compose status/permission UI (build from `HANDOFF-WATCH.md`) + manifest (WFP perms `PUSH_WATCH_FACES` + `SET_PUSHED_WATCH_FACE_AS_ACTIVE`, capability `dialed_wfp_install` in `res/values/wear.xml`, `DEFAULT_WATCHFACE_VALIDATION_TOKEN` meta-data for Phase 5).
- **`:app`**: `WatchConnection` (CapabilityClient discover `dialed_wfp_install` + `WearableApiAvailability` guard; MessageClient/ChannelClient sender reading `assets/faces/<key>.apk`+`assets/tokens/<key>.token`; APK→PFD via cacheDir copy) + `PushToWatchSheet` (4-beat, HANDOFF.md F3) + wire the Install button.
- **Gotchas**: never persist slotId (re-`listWatchFaces()`); set-active is one-shot (max 1 rejection → route to settings); compute activation strategy BEFORE install; single concurrent transfer (AtomicBoolean + transferId match + timeout); `updateWatchFace` same-pkg = iterative (keeps style), different-pkg = full replace.
Exact androidify reference files (re-fetch raw): `wear/.../watchfacepush/WatchFaceOnboardingRepository.kt`, `wear/.../service/AndroidifyDataListenerService.kt`, `watchface/.../transfer/WearAssetTransmitter.kt` + `WearDeviceRepository.kt`, `wear/common/.../{WearableConstants,Messages,WatchFaceActivationStrategy}.kt` in github.com/android/androidify. (androidify uses Hilt + kotlinx-serialization ProtoBuf — Dialed drops both.)

## THE add-a-face routine
`node scripts/add_face.mjs` (sync submodule + regenerate facepacks/catalog/previews) → commit → tag `dialed-vX.Y.Z` → push --follow-tags → CI builds+validates+bundles.

## Ship loop
Validate → adversarial review → push main → tag `dialed-v*` → paste the direct `.apk` link. Git author `simpleapps108@gmail.com`. No `gh` CLI (poll/download CI via the API + credential-helper token).

## Phase status
- ✅ **Phase 0 COMPLETE + verified** — all 18 faces pass WFP validation (18/18); CI bundles 18 APKs + 18 tokens into `:app` (confirmed in the built APK).
- ✅ **Phase 1** storefront (Home/FaceDetail/Paywall/Settings/Onboarding + full design system) + debug-unlock. `:wear-common` done.
- ✅ **Phase 3-4 Wear bridge + phone→watch transport** — CODE COMPLETE + adversarially reviewed (4-agent, primary-source API-verified; ships on CI green). `:wear` companion (applicationId `com.dialed.app` = token package_name) = `WatchFacePushRepository` (androidx suspend WFP) + `DialedListenerService` (WearableListenerService: onRequest→proceed byte, onChannelOpened→receiveFile→install→finalize; single-transfer `AtomicBoolean` + one-way `claimTerminal`) + `TransferSession` (process-static bridge to the Wear-Compose UI) + `WfpStateStore` (DataStore: one-shot set-active + permission-denied) + `FacePreviewExtractor` (preview from the received APK). Wear UI (Home/FirstRun/Receive/Concierge/Unsupported) built from the wear design spec. `:app` = `WatchBridge` (CapabilityClient discover `dialed_wfp_install` + Message/Channel sender) + `PushToWatchSheet` + real watch status. CI builds both APKs in one run (same debug key → Data-Layer pairs them). **Owner-gated e2e remains: install `:wear` on the Pixel Watch 4 + grant set-active once.**
- ⏳ Remaining: **4A** dev/sideload · **Phase 2** Billing · **Phase 5** default face + activation (`DEFAULT_WATCHFACE_VALIDATION_TOKEN` meta-data) · **Phase 6** polish/publish.

## Known deviations / TODO
- Instrument Sans not wired (metrics match; typeface pending — `ui-text-google-fonts` + certs); wear side uses default typeface too.
- FaceDial renders real `preview.png` (not procedural dials); `ticking` overlay deferred.
- CI release APKs now `<tag>-phone.apk` + `<tag>-wear.apk` (fixed the old double-"dialed" name).
- Wear "Browse/Open on phone" shows the platform `OpenOnPhoneDialog` only — it does NOT deep-link-launch the phone app (avoids the `wear-remote-interactions` dep); wire that in a later polish pass.
- Watch receive progress is **indeterminate** (the Data Layer file transfer exposes no byte progress); the design's determinate % is not literally reproducible.
- Phone push sheet has no distinct "unsupported watch" state — a watch that doesn't advertise `dialed_wfp_install` reads as disconnected.
- `DEFAULT_WATCHFACE_VALIDATION_TOKEN` meta-data intentionally omitted until Phase 5 (needs a real default face APK).
