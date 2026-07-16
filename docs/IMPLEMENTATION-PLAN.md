# Dialed — Implementation Plan (post-v0.18.0)

**Written:** 2026-07-15, from `docs/ASSESSMENT.md` (read it first — findings C1–L6 are referenced
by id throughout). This plan is written so a **fresh Opus session can execute it cold**, one phase
per chat, without re-deriving context.

---

## 0. Context primer (read once, verbatim facts)

- **What Dialed is:** a native Kotlin/Compose **Watch Face Push marketplace** — phone `:app`
  (storefront + Data-Layer sender) + watch `:wear` (WFP install concierge) + `:wear-common`
  (wire protocol) + 18 generated `facepacks/*` face APK modules built from the `faces/` submodule
  (github.com/aucksy/fablecollection). Repo: **github.com/aucksy/dialed**, local
  `D:\Apps\WearOS Apps\WatchFaces\Dialed App`. Currently at tag `dialed-v0.18.0`, versionCode 19
  (both `app/build.gradle.kts` and `wear/build.gradle.kts` — bump BOTH, in lockstep, every phase).
- **Platform hard limits (never fight these):** WFP is Wear OS 6 / API 36 only; **slot = 1** (one
  Dialed face on the watch at a time; different-package install = full replace); unattended
  `setWatchFaceAsActive` is a **once-ever platform budget** — always attempt, let the platform
  refuse, never fake success; there is **no API** to put faces in the watch's favorites strip — the
  only gallery lever is a bundled **default watch face**.
- **Read before any UI work (design-fidelity gate):**
  phone — `Dialed Watch-Face Store-handoff/dialed-watch-face-store/project/{HANDOFF.md, Dialed - Design Spec.dc.html}`;
  wear — `Dialed Wear OS app Designs/dialed-watch-face-store/project/{HANDOFF-WATCH.md, Dialed Watch - Design Spec.dc.html}`.
  New screens that the spec predates (collections IA) get a written addendum
  (`docs/DESIGN-ADDENDUM-COLLECTIONS.md`, produced in Phase 2) built from the existing token/
  component system — do not improvise off-system.
- **Read before any WFP/transport work:**
  `Resources/wff-watchface.skill → references/companion-app.md` (the app-layer playbook: set-active
  budget & ownership chain, timeout-every-binder-call, single-transfer lock, exit-to-face, genuine
  Home state, RemoteActivityHelper, signing, wear-compose traps).

### Standing rules (non-negotiable, every phase)

1. **NO local Android builds** (~5.9 GB RAM machine). CI (GitHub Actions) is the only compiler and
   the WFP-token verifier. You CAN validate CI-built face APKs locally (JDK17 + validator jar —
   CLAUDE.md "Local WFP validation loop").
2. **Ship loop per phase:** edit → compile-review (read your own diff as a compiler) →
   **adversarial logic review** → fix → commit → bump versionCode/versionName in BOTH app+wear →
   push main → tag `dialed-vX.Y.Z` → push the tag explicitly (`git push origin <tag>` — lightweight
   tags don't ride `--follow-tags`) → poll CI green → paste BOTH direct APK links
   (`https://github.com/aucksy/dialed/releases/download/<tag>/<tag>-phone.apk` and `…-wear.apk`)
   + a "what to re-test on device" list. Review BEFORE tagging, never after.
3. Git author `simpleapps108@gmail.com`. No `gh` CLI — poll CI via the Actions API using
   `git credential fill` (never print the token).
4. **AGP 9 built-in Kotlin:** never apply `org.jetbrains.kotlin.android` (fatal). Only
   `android-application`/`library` + `compose-compiler`.
5. `:app`+`:wear` sign with the committed `dialed-app-debug.keystore` (stable key = in-place
   upgrades; shared cert pairs them over the Data Layer). Faces sign with `dialed-faces.keystore`
   (MUST stay a different key — WFP rule). `:wear` applicationId MUST stay `com.dialed.app`
   (matches the token `--package_name`).
6. Faces are WFF v2 — **no `isAutoSize`** (v3-only, fails validation). New faces authored at
   **480×480**. Regenerate facepacks with `node tools/gen-facepacks.mjs "<ABSOLUTE-REPO-ROOT>"`
   (must pass the root explicitly — the space in "Dialed App" breaks the script's own resolution).
7. Wire protocol enums are **ordinal-stable — append only, never reorder**
   (`wear-common/.../WearConstants.kt`).
8. **Wrap every WFP binder call in `withTimeoutOrNull` and rethrow `CancellationException`**
   before any broad catch. Never downgrade a real install to FAILED. Never show a false "applied".
9. End every phase by updating `CLAUDE.md`'s phase-status + this plan's Progress table, then hand
   off a fresh-chat kickoff prompt for the next phase.
10. One phase per chat. If a phase turns out to be >1 tag of work, split it and tag each slice.

### Decisions taken as defaults (owner: veto by replying, otherwise these stand)

| # | Decision | Rationale |
|---|---|---|
| D1 | **Per-collection one-time INAPP products** (`unlock_<collection>`, e.g. `unlock_kinetik`) + an optional **all-access bundle** (`unlock_all_faces`, kept from the original design as an upsell). No subscriptions. | Owner: "price of collection"; one-time matches the brand promise already in the paywall copy. |
| D2 | **Prices live in Play Console** (per-product, localized). The app always displays the real `ProductDetails` price. The "admin panel" does NOT set prices — Play is the only place prices can actually be set for INAPP products, so pretending otherwise would be a lie in the UI. | Play Billing constraint; also gives owner per-country pricing for free. |
| D3 | **Remote catalog config = a JSON file in this repo** (`config/catalog.json`), served via the raw GitHub URL (repo is public), fetched by the app with a 24 h cache + the same file **bundled as the offline/first-run fallback**. It controls: which faces are FREE, face→collection grouping, collection→productId, ordering, "new" badges, and kill-switches. **Admin flow v1 = edit the JSON + commit** (from phone: GitHub web editor works). Admin v2 (optional, Phase 2 stretch): a single static `admin.html` in the repo that edits the file via the GitHub API with a PAT. No server, no new infra, versioned + reviewable. | Owner asked for "an easy way, backend admin panel of sorts"; a repo JSON is the cheapest thing that is genuinely easy, auditable, and free. A Cloudflare Worker/KV panel can replace it later without app changes if the URL is kept stable behind one constant. |
| D4 | **Free faces are fully installable without any purchase; Home carries no paywall.** Locked faces open their detail with a per-collection unlock CTA. Debug builds stay all-unlocked. | Owner's explicit trial-first funnel. |
| D5 | **Entitlements are client-enforced** (Play purchase cache + DataStore). No server-side receipt validation at this scale; revisit only if piracy is observed. | Proportionate; the faces are also extractable from any APK regardless. |
| D6 | iOS: out of scope. On-demand asset delivery (Play Asset Delivery): out of scope until APK size demands it (watch it in Phase 7). | Keep the machine simple. |

---

## Phase map (dependency-sliced)

| Phase | Tag | Theme | Depends on |
|---|---|---|---|
| 1 | `dialed-v0.19.0` | Correctness & hygiene batch (C1 guard, H2, H3, H4, M1–M6, docs refresh) | — |
| 2 | `dialed-v0.20.0` | **Collections Home + remote catalog config + free faces** (no billing yet) | 1 |
| 3 | `dialed-v0.21.0` | **Play Billing per-collection + entitlement v2 + paywall v2** (code-complete) | 2 |
| 4 | `dialed-v0.22.0` | Store readiness: release lane, AAB, privacy policy, listing kit (billing e2e happens here) | 3 |
| 5 | `dialed-v0.23.0` | Default watch face + system-gallery discoverability (R4 recipe) | 1 (independent of 2–4) |
| 6 | `dialed-v0.24.0` | Living gallery — real face animation phase 1 (R7 E-LITE + WebP delivery) | 1 |
| 7 | `dialed-v0.25.0` | Catalog scale-out: Collection 3 (25 faces) incl. icon-label rollout | 2 (config), 6 (nice) |
| 8 | `dialed-v0.26.0` | Wear polish: Home face size, W3 coaching motion, receive thumbnail, optional tile | 1 |

Phases 5/6 can be reordered after 2 if the owner wants visible sparkle before billing; 3→4 order is
fixed (billing e2e needs the Play upload from 4's prep, so 3 ships code-complete with debug fakes
and 4 lights it up).

---

## Phase 1 — `dialed-v0.19.0` · Correctness & hygiene batch

**Goal:** close every assessment finding that is small, sharp, and protects later phases. Both APKs.
No visual redesign.

**Scope / exact changes:**

1. **C1 guard** — `app/.../MainViewModel.kt`: make `debugToggleEntitlement()` a no-op unless
   `BuildConfig.DEBUG`; in `DialedApp.kt` route Paywall `onPurchase`/`onRestore` and Settings
   `onRestore` through it unchanged (they become inert in release until Phase 3 replaces them).
   Also fix the restore-toggles-OFF quirk: restore should only ever set `true` in debug.
2. **H2** — `MainViewModel`: hold `private var pushJob: Job?`; `startPush` returns early if a push
   is active (or cancels it first — choose cancel-then-start, matching user intent); `dismissPush`
   cancels `pushJob`; ignore late `emit`s by capturing the pushing face id and dropping callbacks
   that no longer match. In `WatchBridge.pushFace` (`app/.../transport/WatchConnection.kt`), add
   `catch (e: CancellationException) { throw e }` above the broad catch (M6), and replace the
   `runCatching`-wrapped `withTimeout` calls in `queryInstalledState`/`uninstallFace` with
   try/catch that rethrows `CancellationException`.
3. **H3** — surface uninstall failure: `MainViewModel.uninstallFace` exposes a transient
   `uninstallError: StateFlow<String?>`; Home/Detail show a snackbar ("Couldn't remove — keep your
   watch nearby") and clear on retry/refresh.
4. **H4 (wire + UX)** — `wear-common/WearConstants.kt`: add `RESPONSE_UNSUPPORTED: Byte = 2`
   (append-only). `wear/.../DialedListenerService.onRequest`: before `tryBegin()`, if
   `!repo.isSupported()` reply `RESPONSE_UNSUPPORTED` (also answer `PATH_QUERY_STATE` with an empty
   snapshot as it already does via the guarded repo). Phone `WatchBridge.pushFace`: map the byte to
   a new `PushStatus.Unsupported`; `PushToWatchSheet` gains the graceful unsupported state the spec
   asks for ("This watch runs an older Wear OS…"); `MainViewModel` maps it to
   `WatchConnection.UNSUPPORTED` so the pill state finally has a producer (fixes half of L1).
5. **M1** — `DialedApp.kt:93` → `firstOrNull { … } ?: return Home` guard.
6. **M3** — phone `AndroidManifest.xml`: `android:launchMode="singleTask"` on MainActivity.
7. **M4** — remove the dead "More" button; wire Settings "Version" to `BuildConfig.VERSION_NAME`;
   Privacy/Licenses rows: hide until Phase 4 provides real targets (leave a TODO referencing
   Phase 4) or wire "Privacy policy" to the URL once it exists; swap Home's settings affordance to
   a proper gear/`ic_more_vert` icon asset with a 48dp target.
8. **M5** — touch targets: `minimumInteractiveComponentSize()`/padding so settings icon, paywall
   close, detail icons, compact uninstall all hit ≥48dp; add `liveRegion = Polite` semantics to
   `WatchStatusPill`.
9. **M6** — delete dead `hasInstalledFace()` (`wear/.../WatchFacePushRepository.kt:113-117`);
   `FaceAssetProvider.stageApk` reuses one staging file name (`push_staged.apk`) or deletes after
   send in `pushFace`'s `finally`.
10. **L1 partial** — delete `WatchStatus.wearOsVersion`/`activeFaceName` and their Settings
    renderers (never populated); delete unused `InstallState.Installing/Error` branches **only if**
    Phase 2/3 won't use them (they will — leave with a comment) → just delete the WatchStatus dead
    fields.
11. **H6 (docs)** — rewrite `CLAUDE.md`'s status/deviation sections to v0.19.0 reality; append
    v0.11→v0.19 rows to `docs/IMPROVEMENTS-PLAN.md`'s Progress table (one line each, tags + dates);
    note in both that `docs/ASSESSMENT.md` + this plan are now the active roadmap.

**Acceptance / verification:**
- CI green; both APKs released with direct links.
- Adversarial review confirms: no new WFP call unbounded, no broad catch swallowing cancellation,
  wire enum append-only, all call-sites of changed signatures updated.
- On-device (owner): push A → dismiss mid-flight → push B immediately → B's sheet shows only B's
  outcome and a comprehensible busy/error message; uninstall with watch out of range → visible
  error; paywall in release-candidate build does NOT unlock (verify via a locally-validated release
  APK if convenient, else code-review-only with the debug APK).
- Zero-context check: Settings shows the real version.

**Risks:** the H4 wire change touches both sides — old-phone/new-watch mixes reply `2` to a phone
that only knows 0/1; the v0.18 phone treats non-`1` as busy ("try again") — acceptable degradation,
note it in the release notes; both APKs ship together anyway.

---

## Phase 2 — `dialed-v0.20.0` · Collections Home + remote catalog + free faces

**Goal:** the owner's marketplace IA. Home = collection cards (no paywall). Collection screen =
that collection's faces; a configured subset is FREE and installable by anyone; the rest show a
lock that routes to the (still-stub) per-collection unlock CTA. All driven by a remote-editable
catalog config with a bundled fallback.

**Design gate:** the `.dc.html` spec predates collections. Before writing UI code, produce
`docs/DESIGN-ADDENDUM-COLLECTIONS.md`: Home collection-card mockup description (reuse the vitrine
card language from spec 1e: cover trio of FaceDials, collection name, face count, "N free" pill,
unlock state), Collection screen (reuse the existing showroom grid 1d verbatim, scoped to one
collection, plus a header), and the locked-face detail CTA (reuse InstallButton.Locked). Stay
inside the existing tokens (`Color.kt`/`Type.kt`/`Dimens.kt`/`Motion.kt`). Owner eyeballs the first
CI build — treat his screenshot feedback as the fidelity check.

**Scope / exact changes:**

1. **Config schema + file** — new `config/catalog.json` (bundled copy at
   `app/src/main/assets/catalog.json`, kept identical by a gen step or by hand):
   ```json
   {
     "schemaVersion": 1,
     "storeMessage": null,
     "collections": [
       {
         "id": "kinetik",
         "title": "Kinetik",
         "subtitle": "Mechanical",
         "productId": "unlock_kinetik",
         "faceOrder": ["kinetik_orrery", "kinetik_turbine", "…"],
         "freeFaces": ["kinetik_orrery"],
         "badge": null
       }
     ],
     "allAccessProductId": "unlock_all_faces"
   }
   ```
   Faces not listed in any collection fall back to their `Face.series` grouping (forward-compat
   for Phase 7). Unknown face ids in config are ignored with a log (config may reference faces the
   installed app doesn't bundle yet — old app + new config must never crash).
2. **`CatalogConfigRepository`** (new, `app/.../data/`): serves `StateFlow<CatalogConfig>` =
   bundled asset immediately → then a network refresh from
   `https://raw.githubusercontent.com/aucksy/dialed/main/config/catalog.json` (constant in one
   place, D3), cached to DataStore with fetch timestamp; refresh at most every 24 h + manual on
   Settings. Parse with `org.json` (no new serialization dep) + schemaVersion tolerance (unknown
   fields ignored; schemaVersion > known → keep last-good). Network errors → keep last-good/bundled.
3. **IA** — `Screen` gains `Collection(collectionId)` (`DialedApp.kt` Saver updated). Home
   (`HomeScreen.kt` rewrite): wordmark + status pill + vertical list of collection cards (cover
   trio, title, count, "N free" pill, OWNED badge when entitled). **No price, no paywall on Home.**
   New `CollectionScreen.kt`: header + the existing 2-col face grid scoped to the collection;
   free faces render unlocked; locked faces keep the lock badge. `FaceDetailScreen`: entitlement
   input becomes per-face (`face is free || collection owned || debug`), locked CTA reads "Unlock
   <Collection>" and routes to the paywall carrying the collectionId.
4. **Entitlement v2 (schema only, billing in Phase 3)** — `EntitlementStore` becomes a
   `Set<String>` of owned collection ids + an `all` marker (DataStore string-set). Migration: old
   `collection_unlocked=true` → `all`. `isFaceUnlocked(face, config, owned)` helper with unit-style
   truth table in a comment. Debug default: all owned.
5. **Copy pass** — expectation-setting copy for slot=1 and first-of-day (assessment §1): collection
   screen footer: "Your watch holds one Dialed face at a time — installing another replaces it."
6. **Admin story v1** — `config/README.md`: exactly how to flip a face free/paid (edit
   `freeFaces`, commit to main, apps pick it up within 24 h or on Settings refresh), how ordering
   and badges work, and the constraint that **prices are edited in Play Console** (D2). Stretch
   (only if the phase is light): `admin/admin.html` static editor using the GitHub contents API
   with a PAT pasted at use time; do not block the tag on it.

**Acceptance:**
- CI green, links posted. Home shows 5 collection cards (Arclight/Kinetik/Aether/Settype/Vespera);
  tapping opens the collection; configured free faces install end-to-end on the watch with **no
  unlock**; locked faces show the per-collection CTA (stub purchase in debug).
- Flip a face free→paid in `config/catalog.json` on main → app reflects it after refresh
  (owner-verifiable with the Settings manual refresh).
- Kill-network test: airplane-mode fresh install renders the bundled config (no blank Home).
- Adversarial review: config parse against malformed/missing/oversized JSON; face-id drift between
  config and catalog; Saver restore of `Screen.Collection` for a removed collection id (fallback
  Home — same class as M1).

**Risks:** IA churn — keep `FaceDial`, `InstallButton`, push sheet, detail untouched except
entitlement inputs, so the WFP path is provably untouched (diff should show zero `:wear` changes).
Free-faces choice: default one free face per collection (first in `faceOrder`) — owner adjusts in
config afterwards, which is the whole point.

---

## Phase 3 — `dialed-v0.21.0` · Play Billing (per-collection) — code-complete

**Goal:** real money. `BillingManager` on billing-ktx 9.1.0 (already a dep), per-collection INAPP
+ optional all-access, restore, acknowledge, price display from `ProductDetails`. Ships
code-complete behind the debug seam; **e2e purchase testing happens in Phase 4** once the app is on
a Play internal-testing track (Play Billing cannot complete purchases for a package Play has never
seen).

**Scope / exact changes:**

1. **`BillingManager`** (new, `app/.../billing/`): connect on first use with retry/backoff;
   `queryProductDetailsAsync` for all productIds from the catalog config (D1);
   `queryPurchasesAsync` on launch + on resume → reconcile entitlements (restore = this same query,
   wired to the existing Restore buttons); `launchBillingFlow(activity, productId)`;
   `PurchasesUpdatedListener` → on PURCHASED: **acknowledge** (mandatory ≤3 days, never consume) →
   write entitlement; handle PENDING (show "pending" state, do not entitle), USER_CANCELED
   (silent), ITEM_ALREADY_OWNED (re-query + entitle). Map productId → collection id via config;
   `unlock_all_faces` → `all`.
2. **EntitlementStore** stays the cache; Play is the source of truth (assessment C1 note): on every
   successful `queryPurchasesAsync`, overwrite the owned-set (union with debug-grants in debug).
3. **Paywall v2** — `PaywallScreen` becomes per-collection: cover trio from that collection, value
   bullets, **real localized price** from `ProductDetails` (placeholder "—" until loaded, never a
   hardcoded string), optional secondary "Get everything" row showing the all-access price.
   Settings "Restore purchase" → `BillingManager.restore()` with success/failure feedback.
4. **Release-build behavior:** with billing present, delete the C1 stub wiring; debug builds keep
   `debugToggleEntitlement` for offline UI testing (guarded since Phase 1).
   ⚠ **Carry-over from Phase 1's review (must not survive this phase):** the v0.19.0 debug gate makes
   the paywall CTA and "Restore" *inert no-ops in a release build* — the safe failure mode (no unlock
   is granted), and unreachable in practice because CI only ships debug builds and Phase 4 is the
   first release lane. But a shown control that does nothing is exactly the defect Phase 1 removed
   elsewhere, so **it is this phase's job to end it**: acceptance requires that in a release build
   every paywall control either performs a real billing action or is not shown at all. No release
   build may ship with the stub still wired (plan order protects this: 3 lands before 4).
5. Unit-testable pure logic (no test infra exists — keep logic in plain functions):
   purchase-state → entitlement-set reducer.

**Acceptance:**
- CI green; adversarial review focuses: acknowledge-or-refund-in-3-days path, PENDING not entitled,
  no entitlement write from an unacknowledged purchase, reconnect-on-SERVICE_DISCONNECTED, price
  never hardcoded, all flows main-safe.
- Debug APK: paywall renders per-collection with "—" prices (no Play on sideload), purchase button
  degrades gracefully (billing unavailable state), debug toggle still works.
- **Owner gates (do in Phase 4):** create the INAPP products in Play Console with ids from
  `config/catalog.json`, set prices, add license testers.

**Risks:** billing-ktx 9.x API shape — verify `queryProductDetailsAsync` signatures against the
official docs during the phase (pinned facts move); do not bump the dep version casually.

---

## Phase 4 — `dialed-v0.22.0` · Store readiness (and billing e2e)

**Goal:** a Play-uploadable, properly-signed release lane, and the owner-facing kit. This is where
Phase 3's billing gets its real end-to-end test.

**Scope:**

1. **Signing:** generate a real upload keystore → repo secrets (`DIALED_UPLOAD_KEYSTORE_B64`,
   `…_PASS`, `…_ALIAS`, `…_KEY_PASS`); `release` signingConfig reads env/secrets (pattern already
   used in ColorCloset/Spends CI). Debug lane and the committed debug keystore stay for the
   sideload releases.
2. **CI:** add a release job (same workflow): `:app:bundleRelease` (AAB) + `:wear:bundleRelease`,
   plus keep the debug APK assets for the owner's sideload loop. Release artifacts uploaded on tag.
3. **Release-build correctness:** verify R8 keeps billing/wearable classes (add keep rules only if
   CI-built release APK actually fails — validate locally with the WFP validator loop +
   `apkanalyzer` if needed); `EntitlementStore` release default = locked (already true).
4. **minSdk decision (H4 long-term):** raise `:wear` minSdk 33→36 for the Play AAB so unsupported
   watches never get the companion (keep the runtime gate as belt-and-braces). Sideload debug APK
   can stay 33 if the owner wants; simplest is to raise both.
5. **Privacy policy** — static page (aucksy.github.io pattern from NotDigest/Spends); truthful:
   no accounts, no analytics, Data Layer transfer phone↔watch only, Play billing. Wire the
   Settings "Privacy policy" row (un-hide from Phase 1) + "Open-source licenses" via
   `com.google.android.gms.oss-licenses` **or** a hand-rolled static licenses screen (prefer
   hand-rolled: zero new plugin surface).
6. **Listing kit** in `play/`: 512 icon, feature graphic, phone + watch screenshots checklist,
   short/long description, data-safety answers — follow the structure of Spends' `play/` kit.
7. **Owner-gated runbook** (`play/SUBMISSION.md`): verify `com.dialed.app` availability (H5 —
   if taken, the applicationId change ripples into token `--package_name`, wear applicationId, and
   ALL 18 face tokens: document the blast radius prominently), create app, upload AAB to internal
   testing, create the INAPP products (ids from config), add license testers, **then** e2e-test
   purchases on-device and confirm entitlements restore on reinstall.

**Acceptance:** CI green producing signed AABs + the usual debug APKs; owner completes the runbook;
a real purchase unlocks a collection; reinstall restores it; refund (test) removes it on next
launch reconcile.

**Risks:** applicationId collision (checked first, before any other step); Play review flags for
`WAKE_LOCK`/wearable perms are standard-approvable.

---

## Phase 5 — `dialed-v0.23.0` · Default watch face + gallery discoverability

**Goal:** execute the R4 recipe (`docs/research/R4-carousel-slot-model.md` — execution-ready):
a branded "Dialed" default face seeds the system watch-face gallery at install time, and
uninstall reverts to it instead of emptying the slot.

**Scope (follow R4's RECIPE section verbatim, summarized):**
1. Author a simple, instantly-recognizable **Dialed-branded WFF v2 face at 480×480** (dial mark +
   wordmark + time; run the wff-watchface-polish §7 pass) as `faces-default/` in THIS repo (not the
   submodule — it's Dialed-brand, not Fable), packaged like a facepack
   (`com.dialed.app.watchfacepush.dialed.default`), faces keystore.
2. CI: build it, validate with the same validator, and (new step) copy the APK to
   `wear/src/main/assets/default_watchface.apk` + emit the token into a generated res file wired as
   an extra res dir; un-comment the manifest meta-data
   (`wear/src/main/AndroidManifest.xml:29-32` placeholder already exists):
   `com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN`.
3. `WatchFacePushRepository`: add `revertToDefault()` (an `updateWatchFace` with the bundled
   default APK/token, timeout-bounded); `DialedListenerService` `PATH_UNINSTALL` handler calls it
   instead of `removeByPackage` (phone copy changes from "Remove from watch" to "Reset watch to the
   Dialed face" — keep an explicit true-remove in Settings if the owner wants one).
4. Honest UX copy from R4 §5-A on phone detail + wear Home.
5. On-device verification closes R4's two `[UNVERIFIED]`s: does the default consume the slot
   (`remainingSlotCount`), and which surface (favorites strip vs "+" gallery) shows the tile —
   record both answers in R4.

**Acceptance:** fresh wear install (clean) shows a Dialed tile in the system gallery with no push;
uninstall-from-phone leaves the default face installed; CI 19/19 faces validated.
**Risks:** the slot-consumption unknown — if the default occupies the slot, the "first push" flow
must take the update path (it already does: `installOrUpdate` checks `remainingSlotCount`).

---

## Phase 6 — `dialed-v0.24.0` · Living gallery (R7 phase 1: E-LITE + WebP delivery)

**Goal:** real, per-face motion in the phone app — no fake hands — per
`docs/research/R7-face-animation-in-app.md` Phase-1 scope.

**Scope (R7 §4 Phase 1, plus the delivery gates):**
1. Add Coil 3 (`coil-compose` + `coil-gif`, register `AnimatedImageDecoder`).
2. For the ~8 analog/sweep faces + Pulsar: bake hands-off base plates + real hand sprites
   (submodule `collection3-tools/gen/bake.mjs` `!LIVE_KINDS` path where available; else a one-off
   bake from the face's own layered sources); bundle as drawables; extend `FaceDial` to composite
   base + rotate the REAL hand sprite about its true pivot using the existing `rememberSecondsAngle`
   loop (assessment L2 hook) — detail hero + on-screen grid cells only, reduce-motion freezes.
3. Digital faces: live `TimeText`-style overlay only where cheap; minute-cadence faces stay on
   `preview.png` (R7's census: 9/18 have sub-minute motion — don't animate what doesn't move).
4. Metronome (sprite): defer the emulator-capture WebP to a 6b slice unless trivially cheap; the
   emulator CI job (R7 Approach D, API 35 wear image, scrcpy/screencap NOT screenrecord) is its own
   tag if pursued.
**Acceptance:** detail hero of Solstice/Turbine/etc. moves with the face's own hand art; grid stays
calm (visible-cells only); reduce-motion static; no per-frame recomposition (transform-only rule).
**Risks:** pivot fidelity per face — verify against the face XML pivot values; battery — gate to
STARTED lifecycle as `rememberSecondsAngle` already does.

---

## Phase 7 — `dialed-v0.25.0` · Collection 3 scale-out (25 faces)

**Goal:** grow the catalog 18→43 with the premium Collection 3 (Vakt/Meridian/Terra/Halo/Aurum) —
the collections IA from Phase 2 exists precisely to absorb this.

**Scope:**
1. **Icon-label rollout to the 25 faces first** (owner standing rule: fix faces to current recipe
   before shipping them) — reuse the v0.12.0 scripted transform (`apply-icon-labels.mjs` pattern;
   scope inside `<Complication>` blocks only, the two label shapes, layout-aware STACKED/SIDE,
   value-box per-element match, tint rules), validate all 25 locally, ship in the **fablecollection**
   repo per its rules.
2. Dialed: extend `BUNDLED_FACES` (+ regenerate facepacks with the absolute-root invocation),
   extend `config/catalog.json` with the 5 new collections + free picks + product ids; Play Console
   products (owner). `SERIES_META` gains the 5 series (and per-face features come from the M2 fix).
3. **Watch the APK size:** 43 faces bundled — if the phone APK crosses ~150 MB the sideload story
   is fine but Play AAB may warrant Play Asset Delivery; measure first (CI logs the asset dir
   size), decide then (D6).
**Acceptance:** CI 43/43 validated + tokenized; new collections browsable/purchasable; existing
users see them after a config refresh (badge "NEW").
**Risks:** validator time ×43 in CI (acceptable, linear); memory of full-res previews (assessment
L3 — Coil from Phase 6 mitigates; make the grid use size-aware requests here at the latest).

---

## Phase 8 — `dialed-v0.26.0` · Wear polish batch

**Goal:** the remaining wear-side comfort items, sized by owner feedback.

**Scope:** Home face-size bump 73→~88dp with rebalanced fractions (deliberate spec deviation —
owner has asked twice; verify at 192dp AND 225dp, update the design addendum); W3 coaching loop
(animated 6 s loop ×3 then Replay, reduced-motion keeps the static cards); receive-flow thumbnail
(phone downsizes the preview to ≤64px PNG and appends it to the initiate payload — keep the message
comfortably under the Data-Layer message size limit; wear shows it materializing per W1); optional
DialedTile (spec 1p) if the owner wants a tile surface.
**Acceptance:** owner confirms the Home proportions on-wrist; receive shows the actual incoming
face; coaching plays.

---

## Progress table (keep live — update as each phase tags)

| Phase | Tag | State | Notes |
|---|---|---|---|
| 1 Hygiene | v0.19.0 | ✅ **SHIPPED** 2026-07-16 (CI run 29472817925 green, 18/18 faces validated; commit `0ed9dfe`) | C1 paywall debug-gate (+ restore only ever grants) · H2 push-token guard + `CancellationException` rethrow + `NonCancellable` cleanup · H3 uninstall-error snackbar · H4 `RESPONSE_UNSUPPORTED=2` + `!unsupported` query sentinel → honest sheet state + the UNSUPPORTED pill finally has a producer (`QueryStateResult.supported`) · M1 `firstOrNull` detail guard · M3 phone `singleTask` · M4 dead "More"/no-op rows removed + `BuildConfig.VERSION_NAME` + real gear icon · M5 48dp targets (incl. the theme selector) + pill live region · M6 dead `hasInstalledFace()` + per-transfer staged APK with sweep · L1 dead `WatchStatus` fields · H6 CLAUDE.md + this table refreshed. **7-lens adversarial review (43 agents, 3 refuters/finding) caught 2 real self-inflicted bugs pre-tag: (a) BLOCKER — the new `catch (CancellationException)` swallowed the setup RPC's `withTimeout` (`TimeoutCancellationException` IS a `CancellationException`) → the sheet hung on "Sending…" forever with no error and no Retry; fixed by `withTimeoutOrNull` + an explicit emit. (b) HIGH — cancelling the push job on dismiss abandoned a transfer the watch was still installing (the phone cannot stop the Data Layer anyway), so the face landed on the wrist while the phone still read "Install to watch"; fixed by making the token the whole guard, never cancelling, and recording watch FACTS (Done→refresh, Unsupported) above the token gate so only the sheet write is gated.** Also fixed off-review: the M3 default Snackbar was painting on unmapped `inverse*` roles (the one off-system surface) and the 48dp gear target visibly indented the glyph past the 24dp margin. |
| 2 Collections | v0.20.0 | ⬜ | |
| 3 Billing | v0.21.0 | ⬜ | |
| 4 Store | v0.22.0 | ⬜ | owner gates: Play account/products/upload |
| 5 Default face | v0.23.0 | ⬜ | closes R4 UNVERIFIEDs on device |
| 6 Living gallery | v0.24.0 | ⬜ | 6b = emulator WebP, optional |
| 7 Collection 3 | v0.25.0 | ⬜ | faces repo work first |
| 8 Wear polish | v0.26.0 | ⬜ | face-size = owner call |

---

## Kickoff prompt for the next session (Phase 1) — paste verbatim

```
You're executing Phase 1 of the Dialed implementation plan.

Repo: D:\Apps\WearOS Apps\WatchFaces\Dialed App (github.com/aucksy/dialed), currently at
dialed-v0.18.0 (versionCode 19). READ FIRST, in order:
1. CLAUDE.md (project context; note it is stale — Phase 1 itself refreshes it),
2. docs/ASSESSMENT.md (findings C1–L6 — Phase 1 fixes the ones listed below),
3. docs/IMPLEMENTATION-PLAN.md → "Phase 1 — dialed-v0.19.0" (your exact scope, acceptance
   criteria, and the standing rules in §0 — they are non-negotiable),
4. Resources/wff-watchface.skill → references/companion-app.md (WFP app-layer playbook).

Scope (details in the plan — do not exceed it): C1 debug-gate the entitlement toggle; H2 push-job
guard + cancellation hygiene; H3 uninstall error surfacing; H4 RESPONSE_UNSUPPORTED wire byte +
honest unsupported UI (append-only enum!); M1 detail-restore crash guard; M3 phone singleTask;
M4 dead controls + real version string; M5 48dp targets + status-pill live region; M6 dead
hasInstalledFace + staged-APK cleanup; L1 dead WatchStatus fields; H6 refresh CLAUDE.md +
IMPROVEMENTS-PLAN progress table.

Hard rules: NO local Android builds (CI compiles); adversarial review BEFORE tagging; bump
versionCode/versionName to 20 / 0.19.0 in BOTH app/build.gradle.kts and wear/build.gradle.kts;
git author simpleapps108@gmail.com; AGP 9 built-in Kotlin (never apply org.jetbrains.kotlin.android);
never reorder wire enums; every WFP binder call stays timeout-bounded.

Ship loop: commit → push main → tag dialed-v0.19.0 → push the tag explicitly → poll CI via the
Actions API (git credential fill for the token, never print it) → when green, paste BOTH direct
APK links (releases/download/dialed-v0.19.0/dialed-v0.19.0-phone.apk and …-wear.apk) + an
on-device re-test list for the owner (push/dismiss/push race, uninstall-out-of-range error,
settings version, unsupported-watch message if testable). Then update the plan's Progress table
and hand off the Phase 2 kickoff prompt (collections — read the plan's Phase 2 and the design
handoffs before writing any UI).
```
