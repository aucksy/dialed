# Dialed — End-to-End Assessment (phone `:app` + watch `:wear`)

**Date:** 2026-07-15 · **Assessed at:** `dialed-v0.18.0` (commit `b7af4f5`, versionCode 19)
**Method:** Fable-mode review — every finding below was read from the actual source, the design
handoffs, and the in-repo research docs. Each finding carries `file:line` evidence and is tagged
**[VERIFIED]** (read/greped from code this session) or **[ASSUMED]** (inference that needs a
device/CI check). "Already solid" is reported as such — no invented problems.

Companion document: `docs/IMPLEMENTATION-PLAN.md` (the phased plan that fixes everything here and
adds the collections/billing/admin direction).

---

## 0. Verdict in one paragraph

The **WFP core is genuinely solid** — the transfer/lock/timeout/activation machinery matches the
hard-won playbook (`wff-watchface.skill` → `companion-app.md`) point for point, and the honest-state
work of v0.15–v0.18 shows on every path. What stands between Dialed and a real marketplace is not
the plumbing; it is (a) a **monetization layer that is currently a stub that grants free unlocks in
any release build**, (b) a **catalog IA** that is a flat 18-face grid rather than the owner's
collections model, (c) **store-readiness** (release signing, privacy policy, Play lane) that hasn't
started, and (d) a set of small but real phone-side correctness/UX bugs and dead controls. The wear
app is in the best shape of the two; the phone app carries most of the debt.

---

## 1. Already solid (verified — do not re-litigate)

These were attacked and survived. Evidence read this session:

| Area | Evidence |
|---|---|
| **Single-transfer lock + one-way terminal claim** — setup-timeout vs channel race is settled by `claimTerminal()`; the lock is released in every terminal path | `wear/.../wfp/TransferSession.kt:52-67`, `DialedListenerService.kt:97-119, 203-209` |
| **Every WFP binder call on the transfer path is timeout-bounded, cancellation is rethrown** (v0.16 rule) | `WatchFacePushRepository.kt:38, 61-62, 76, 95-96, 103, 125, 132` |
| **A real install can never be downgraded to FAILED**; success UI commits before fallible side-effects | `DialedListenerService.kt:162-194` |
| **Finalize exactly once, in `finally`, defaulting FAILED** | `DialedListenerService.kt:139-141, 203-209` |
| **Receive/install runs on a process-scoped scope, not the service Job** | `DialedListenerService.kt:281-289` |
| **Activation honesty** — no local latch; platform is the authority; refused set-active maps to the manual coach, never a false "Dialed in." | `WatchFaceActivationStrategy.kt:20-40`, `DialedListenerService.kt:174-187`, `ConciergeScreen.kt:57-67` |
| **Home shows genuine slot state** (None / Installed+active / Installed+inactive), anti-stale name/preview rule | `WearViewModel.kt:160-181`, `wear/.../screens/HomeScreen.kt:64-146` |
| **"Open on phone" really opens the phone app** (RemoteActivityHelper + `dialed://open` filter both sides) | `wear/.../HomeScreen.kt:158-190`, `app/src/main/AndroidManifest.xml:27-32` |
| **Connection = capability of the Dialed phone app, with retry + 12s self-heal** (not "any paired phone") | `WearViewModel.kt:189-209`, `app/src/main/res/values/wear.xml` |
| **Guaranteed-circle previews** (`requiredSize` + explicit center-cropped `drawImage`) | `wear/.../components/FaceDial.kt:78-111` |
| **Stable committed signing key shared by `:app`+`:wear`; faces key distinct** (WFP rule) | `app/build.gradle.kts:27-34`, `wear/build.gradle.kts:27-34`, `facepacks/*/build.gradle.kts` |
| **CI is a strict gate**: every face must pass the WFP validator or the build fails; 18-face allowlist prevents silent Collection-3 bloat | `.github/workflows/build.yml:40-67`, `tools/gen-facepacks.mjs:29-36` |
| **Wire protocol is ordinal-stable and documented**; RPC paths have the load-bearing manifest intent-filters | `wear-common/.../WearConstants.kt:132-152`, `wear/src/main/AndroidManifest.xml:48-61` |
| **Exit-to-watch-face** after apply (`finishAndRemoveTask()`, clear after teardown) | `wear/.../MainActivity.kt:59-62` |

**On the two standing owner questions:**

- **First-of-day auto-apply: nothing more is possible in software.** The unattended
  `setWatchFaceAsActive` is a platform-enforced anti-hijack budget (Google API ref: *"can be set by
  this means only once"*); v0.15 already removed the pessimistic local latch, always attempts, logs
  the refusal verbatim (`WatchFacePushRepository.kt:89-94`), and degrades to the honest manual
  coach. The remaining unknown (does the budget ever reset — per-boot? per-new-face?) is answerable
  only from the owner's morning logcat, which the shipped instrumentation already supports.
  **Recommendation: close the item as platform-limited**; the only product lever left is copy that
  sets expectations (folded into the plan's Phase 2 copy pass). [VERIFIED against code + R4 +
  companion-app.md §5]
- **Home face size (73dp):** the code matches the spec exactly (spec §A: 146px @ 2px/dp = 73dp at
  fraction .245 — `wear/.../HomeScreen.kt:111-115`). The owner has raised "small/high" twice, so
  this is now a **deliberate spec deviation to make**: bump to ~88dp and rebalance the caption
  fraction (plan Phase 8, one-liner + visual check at 192dp and 225dp). Not a bug.

---

## 2. Findings — severity-ranked

### CRITICAL

**C1. The paywall grants a free permanent unlock in ANY release build.** [VERIFIED]
`PaywallScreen`'s "Unlock everything" and both "Restore purchase" entry points are wired directly
to `debugToggleEntitlement()` with **no `BuildConfig.DEBUG` guard**:
- `app/.../ui/DialedApp.kt:112` — `onPurchase = { viewModel.debugToggleEntitlement(); screen = Screen.Home }`
- `DialedApp.kt:113` and `:121` — `onRestore = { viewModel.debugToggleEntitlement() }`
- `MainViewModel.kt:149-150` — no debug gate; writes `EntitlementStore.setUnlocked(...)` durably.

*Failing scenario:* any user of a **release** build taps "Unlock everything" → entitlement flips to
`true`, persisted forever, zero payment. (Today's shipped APKs are `assembleDebug` — which default
to unlocked anyway, `EntitlementStore.kt:25` — so nothing has leaked *yet*; the moment a release
build ships, this is a total paywall bypass.) A second quirk: when already unlocked, tapping
"Restore purchase" **locks** the collection (it's a toggle).
*Fix:* Phase 1 gates the toggle behind `BuildConfig.DEBUG` (and hides debug affordances in
release); Phase 3 replaces the seam with real Play Billing.

### HIGH

**H1. No billing exists at all — monetization is a placeholder end-to-end.** [VERIFIED]
`billing-ktx:9.1.0` is declared (`app/build.gradle.kts:80`) but there is no `BillingClient`
anywhere (`grep BillingClient` → 0 hits). Price is a hardcoded default parameter
(`HomeScreen.kt:61`, `PaywallScreen.kt:45` — `"$11.99"`). `EntitlementStore` is a single boolean
(`collection_unlocked`), which also cannot represent the owner's new **per-collection** model.
This is the known Phase-2 seam, listed here because the entitlement schema must change shape
(boolean → per-collection set) *before* billing lands, or it will be migrated twice.

**H2. Concurrent/zombie pushes: `startPush` has no in-flight guard and `dismissPush` doesn't cancel.** [VERIFIED]
`MainViewModel.startPush()` (`MainViewModel.kt:99-109`) launches `watchBridge.pushFace(...)` with no
job handle; `dismissPush()` (`:143-146`) only nulls the UI state. `uninstallFace` has exactly the
guard pattern needed (`:121-122`) — pushes don't.
*Failing scenario:* push face A → swipe the sheet away mid-transfer → tap Install on face B. Two
`pushFace` coroutines now run; the watch answers BUSY to B (A still holds the watch lock) so the
user sees "Your watch is busy" for no visible reason — and when A's transfer later finalizes, its
`emit(Done/Error)` **overwrites B's sheet status** (both writers target `_pushStatus`), so face B's
sheet can show face A's outcome.
*Fix:* hold the push `Job` in the VM; `startPush` while active either no-ops or cancels-then-starts;
`dismissPush` cancels; stale emits are ignored by transfer identity.

**H3. Failed uninstall is completely silent on the phone.** [VERIFIED]
`MainViewModel.uninstallFace()` (`MainViewModel.kt:124-136`) checks only `REMOVED`; on
`FAILED`/`NOT_FOUND` (watch unreachable, WFP error) nothing is surfaced — the spinner stops and the
badge stays.
*Failing scenario:* watch just out of range → user taps the trash icon → spinner → nothing. No
error, no retry affordance.
*Fix:* surface an error snackbar/state and keep the truth from `refreshInstalledState()`.

**H4. A Wear OS < 6 watch looks fully "Connected" and pushes fail with a generic error.** [VERIFIED code; failure mode ASSUMED on-device]
The wear app installs from `minSdk 33` (`wear/build.gradle.kts:18`) and advertises
`dialed_wfp_install` **statically** (`wear/src/main/res/values/wear.xml`) — the capability does not
depend on `isSupported()`. The listener never checks support either (`DialedListenerService.kt:67-120`
has no `isSupported()` call; grep confirms the only caller is `WearViewModel.kt:51`).
*Failing scenario:* Galaxy Watch on Wear OS 5 installs the companion → phone shows
"Connected" → push proceeds → `WatchFacePushManagerFactory.createWatchFacePushManager` throws inside
`installOrUpdate` → caught → `FAILED` → phone says "The watch couldn't install this face" with no
hint the watch can never work. The phone's `WatchConnection.UNSUPPORTED` pill state exists but is
**unreachable** (grep: no producer sets it — only `WatchStatusPill.kt:38` renders it).
Irrelevant for the owner's Pixel Watch 4; **blocking for Play distribution**.
*Fix (Phase 1):* wear `onRequest` replies a new `RESPONSE_UNSUPPORTED = 2` byte when
`!repo.isSupported()` (ordinal-appended, wire-compatible); phone maps it to an honest
unsupported sheet state + pill. Longer-term (Phase 4): consider raising wear `minSdk` to 36 so Play
never delivers the companion to unsupported watches.

**H5. Store readiness has not started; the release lane would currently ship the debug-unlocked build.** [VERIFIED]
CI builds and releases `assembleDebug` for both apps (`.github/workflows/build.yml:70, 76`), debug
builds are hardwired entitled (`EntitlementStore.kt:25`), there is no upload/release keystore path
(`app/build.gradle.kts:43` comment only), no AAB, no privacy policy, the licenses/privacy rows are
dead (M4), and `com.dialed.app`'s availability as a Play applicationId is unverified (the Pause app
lost `com.pause.app` this way — real precedent). One coherent Phase-4 work package.

**H6. The repo's two entrypoint docs are 16 versions stale — the next session will be misled.** [VERIFIED]
`CLAUDE.md` says "shipped `dialed-v0.2.1`" (`CLAUDE.md:6`), lists `:wear` as "NEXT (Phase 3-4)"
(`:36`), and its Known-deviations list (`:74`) still says "Open on phone … does NOT deep-link-launch
the phone app" — fixed in v0.18.0. `docs/IMPROVEMENTS-PLAN.md`'s Progress table ends at v0.10.0.
Under the fresh-chat-handoff protocol these files ARE the context for the next chat; staleness here
is a process defect, not cosmetics. *Fix:* Phase 1 refreshes both and makes "update the entrypoint"
part of every phase's exit checklist (it already is in theory; it hasn't happened since v0.10).

### MEDIUM

**M1. Restored navigation can crash the app on a removed face id.** [VERIFIED]
`DialedApp.kt:93` — `viewModel.faces.first { it.id == target.faceId }` throws
`NoSuchElementException` if a `rememberSaveable`-restored `"detail:<id>"` no longer resolves (e.g.
catalog changed between app versions across process death). One-line fix: `firstOrNull` + fall back
to Home.

**M2. Detail-screen "feature" chips are series-level guesses, not the face's real complications.** [VERIFIED]
`tools/gen-facepacks.mjs:38-53` stamps `features` from `SERIES_META` (e.g. every Aether face claims
"Weather", every Vespera claims "Heart rate") without reading the face's `watchface.xml`
`<ComplicationSlot>`s. The phone renders these as capability chips (`FaceDetailScreen.kt:103-111`).
*Failing scenario:* a buyer picks a face for "Weather" that has no weather complication → refund/
review risk. *Fix:* gen-facepacks derives features from each face's actual complication slots (it
already parses the XML for the `@drawable/` patch).

**M3. Phone deep link stacks duplicate MainActivities.** [VERIFIED manifest; behavior ASSUMED]
`app/src/main/AndroidManifest.xml:16-33` — MainActivity has no `launchMode`, so the watch-fired
`VIEW dialed://open` (delivered with `NEW_TASK`) adds a **second** MainActivity instance (own
ViewModel, own capability listener) on top of an already-running task. The wear side already uses
`singleTask` (`wear/src/main/AndroidManifest.xml:37`). *Fix:* `android:launchMode="singleTask"` (or
`singleTop`) on the phone activity.

**M4. Dead/no-op controls and stale surface strings on the phone.** [VERIFIED]
- Detail "More" button does nothing: `FaceDetailScreen.kt:65` — `CircleIconButton(R.drawable.ic_more_vert, "More", {})`. This is exactly the v0.18 lesson ("a shown button must work").
- Settings "Privacy policy" and "Open-source licenses" rows are `clickable {}` no-ops: `SettingsScreen.kt:126-129` + `:166`.
- Settings "Version" is hardcoded `"0.1.0"` (`SettingsScreen.kt:124`) while the app is 0.18.0 — should read `BuildConfig.VERSION_NAME`.
- The Home top-right *Settings* affordance uses the **filter** icon (`HomeScreen.kt:89` — `R.drawable.ic_filter` with `contentDescription = "Settings"`).

**M5. Accessibility gaps vs HANDOFF.md §8.** [VERIFIED]
- Touch targets < 48dp: Home settings icon is 22dp (`HomeScreen.kt:91`), paywall close 40dp (`PaywallScreen.kt:55`), detail icon buttons 42dp with no expanded target (`FaceDetailScreen.kt:182-187`), compact uninstall 38dp (`InstallButton.kt:119`).
- The phone `WatchStatusPill` has **no live region** (spec: "status pill announces connection changes via live region") — `WatchStatusPill.kt:58-72` has no semantics. (The wear `ConnectionStatus` does it right — `WearComponents.kt:151-154`.)

**M6. Phone transport hygiene: cancellation swallowed; staged APKs accumulate.** [VERIFIED]
- `WatchBridge.pushFace` ends in `catch (e: Exception)` (`WatchConnection.kt:209-211`) with no `CancellationException` rethrow — the exact anti-pattern the v0.16 wear fix documents (it would also mis-render a cancelled push as "Couldn't reach your watch"). `queryInstalledState`/`uninstallFace` use `runCatching` around `withTimeout`, which likewise eats parent cancellation.
- `FaceAssetProvider.stageApk` writes `cacheDir/push_<id>.apk` (`WatchConnection.kt:58-64`) and nothing deletes them — up to 18 stale APK copies in cache. (The wear side deletes its temp file — `DialedListenerService.kt:207`.)
- `hasInstalledFace()` is **dead code** and the one repo call with no timeout wrapper (`WatchFacePushRepository.kt:113-117`; grep: zero callers) — delete it or bound it.

**M7. Catalog IA vs the owner's direction (collections) — plus a latent overflow.** [VERIFIED]
Home is a flat 2-col grid of all 18 faces with series filter chips in a **non-scrollable Row**
(`HomeScreen.kt:97-101`) — more collections will push chips off-screen. The owner's chosen model
(collection cards on Home → per-collection screens, free faces per collection, no paywall on Home)
replaces this IA outright — planned as Phase 2, so don't patch the chips separately.

**M8. Design-fidelity deltas that still matter (phone).** [VERIFIED vs HANDOFF.md]
Not re-listing every known deferral; the ones that shape perceived quality:
- **Instrument Sans not wired** (both apps) — `Type.kt:10-13` TODO; brand typography is the single biggest "premium feel" lever the spec calls for.
- **F2 shared-element expand** replaced by a plain crossfade (`DialedApp.kt:71-77`); **F3 transfer beats** replaced by a static face + indeterminate bar (`PushToWatchSheet.kt:84-105`); **F4 unlock celebration** absent; **F6 logo launch sweep** absent (plain splash, `MainActivity.kt:19`).
- Home grid `GridCells.Fixed(2)` vs spec `Adaptive(150dp)` (`HomeScreen.kt:71`); no loading/empty states (spec 1d).
These become the Phase-2 (IA rebuild) and Phase-6 (living gallery) work — flagged so they're not
lost, not to demand a separate pass.

**M9. Wear receive flow shows a placeholder, not the incoming face (spec W1 "face materializes").** [VERIFIED]
`ReceiveScreen.kt:44, 58` passes `preview = null` (the preview only exists *after* the APK arrives).
The initiate payload carries only id/token/name (`WearConstants.kt:72-73`).
*Option (Phase 8):* phone sends a ~64px thumbnail in the initiate payload (small enough for a
MessageClient payload) so the catch shows the real face materializing. Cosmetic, but it is the
spec's money moment on the watch.

### LOW / NICE-TO-HAVE

**L1. Dead model/UI states:** `WatchStatus.wearOsVersion`/`activeFaceName` never written (grep: no
writers; rendered at `SettingsScreen.kt:71-78`), `WatchConnection.CONNECTING`/`UNSUPPORTED` never
produced, `InstallState.Installing`/`Error` never used by any caller (sheet handles those phases).
Either wire them (H4 wires UNSUPPORTED) or delete. [VERIFIED]

**L2. `rememberSecondsAngle`/`ticking` is currently dead** — the only `ticking=true` path was
removed in v0.10 (`FaceDetailScreen.kt:84` passes `false`; grep confirms no true call-site). It is
the intended hook for Phase 6 (R7 E-LITE) — keep, but know it ships dead today. [VERIFIED]

**L3. Preview memory at scale:** 18 full-res `drawable-nodpi` previews decoded at full size via
`painterResource` into 150dp cells. Fine at 18; at 43+ faces (Collection 3) switch to size-aware
decoding (Coil lands in Phase 6 anyway). [ASSUMED — no profiling done]

**L4. Wear battery:** the 12s link self-heal loop runs whenever the ViewModel is alive
(`WearViewModel.kt:93-98`); with the app foregrounded it's ~3 capability probes/12s — negligible in
practice since the wear app is open briefly, but worth remembering if a tile/ongoing surface ever
keeps the process warm. [VERIFIED code, impact ASSUMED]

**L5. `wakeDevice` uses deprecated `FULL_WAKE_LOCK`** (`DialedListenerService.kt:261-270`). Works
today; the modern equivalent is an activity with `setTurnScreenOn`/`setShowWhenLocked`. Cosmetic
until an API level removes it. [VERIFIED]

**L6. CI/tooling nits:** `libs.versions.toml` still carries the wrong commented validator version
(`# validatorPushCli = "1.0.0-alpha10"` — alpha10 does not exist; the workflow correctly pins
alpha09); versionCode/Name are duplicated across `app/` and `wear/` gradle files and must be bumped
in lockstep (a `dialedVersion` in one place would remove a foot-gun). [VERIFIED]

---

## 3. Screen-by-screen design-fidelity summary

Phone (vs `HANDOFF.md` + `Dialed - Design Spec.dc.html`):

| Screen | Verdict | Deltas (evidence above) |
|---|---|---|
| Onboarding | Good | No F6 logo sweep; page art simplified — acceptable |
| Home | Diverges | Fixed(2) grid, no loading/empty states, filter Row overflow risk, wrong settings icon, UnlockBanner OK — **superseded by the Phase-2 collections IA** |
| Face detail | Good- | Dead "More" button (M4); chips content wrong (M2); hero static (by design until Phase 6) |
| Paywall | Good | Hardcoded price; becomes per-collection paywall in Phase 3 |
| Push sheet | Partial | 4-beat F3 motion absent; honest states + persistent Done are right; no unsupported state (H4) |
| Settings | Partial | Dead rows, stale version (M4); watch card renders never-populated fields (L1) |
| Dev sideload | Missing | Phase 4A never built — fold into backlog (debug-only) |

Wear (vs `HANDOFF-WATCH.md` + `Dialed Watch - Design Spec.dc.html`):

| Screen | Verdict | Deltas |
|---|---|---|
| Home | Good | Fractional layout per spec; owner wants face > 73dp (deliberate deviation, Phase 8); installed-inactive state is a justified spec extension |
| FirstRun | Good | Single-deny routes to Settings (slightly stricter than the OS requires) — acceptable |
| Receive | Good- | Placeholder instead of incoming face (M9); indeterminate vs determinate arc is a platform limit (no byte progress) |
| Concierge | Good | Honest celebration/coach split is exactly right; W3 loop is the static reduced-motion form for everyone (polish, Phase 8) |
| Unsupported | Good | — |
| Tile (1p) | Not built | Spec says optional, not v1 |

---

## 4. Marketplace-gap register (the product view)

| Gap | State | Where it lands |
|---|---|---|
| Collections IA + free-face trial model (owner direction) | Not built | Phase 2 |
| Real billing (per-collection) + entitlements | Not built (C1/H1) | Phase 3 |
| Remote catalog config + "admin panel" for free/paid/price | Not built | Phase 2 (config) + Play Console (price) |
| Play release lane, privacy policy, listing | Not built (H5) | Phase 4 |
| Default watch face → system-gallery tile (R4 recipe; also fixes "not in the + carousel" as far as the platform allows) | Not built | Phase 5 |
| Living gallery / real in-app animation (R7 hybrid) | Not built (L2 hook ready) | Phase 6 |
| Collection-3 (25 faces) onboarding incl. icon-label rollout | Not started | Phase 7 |
| Wear polish (face size, W3 loop, receive thumbnail, tile) | Open items | Phase 8 |

---

## 5. What I did NOT verify (honest limits)

- Nothing was compiled or run this session (no local Android toolchain — standing rule). All
  correctness claims are from reading code; anything marked [ASSUMED] needs its listed check.
- CI-built artifacts (assets/faces, tokens, APK sizes) are gitignored and were not inspected.
- The `.dc.html` visual specs were assessed through their HANDOFF summaries plus the spec facts
  embedded in memory/PROGRESS (§A fractions, F-moments); pixel-level mockup comparison was not
  re-done screen by screen.
- On-device behavior of H4 (unsupported watch) and M3 (duplicate activity) follows from documented
  platform behavior but was not reproduced on hardware.
