# Dialed — verified dependency & policy facts (§11)

Verified against primary Google sources on **2026-07-11** (5-agent research workflow, all `verified-primary-source`). Re-verify occasionally; these are moving targets.

## Pinned versions
| What | Coordinate | Version | Notes |
|------|-----------|---------|-------|
| Watch Face Push lib | `androidx.wear.watchfacepush:watchfacepush` | **1.0.0** (STABLE, 2026-04-08) | The `androidx.wear.watchface:watchface-push` form is **not a real artifact** — do not use. Needs `google()` only. |
| WFP validator — CLI (build-time) | `com.google.android.wearable.watchface.validator:validator-push-cli` | **1.0.0-alpha10** | still alpha; used in CI/add_face. |
| WFP validator — Android runtime | `com.google.android.wearable.watchface.validator:validator-push-android` | **1.0.0-alpha10** | debug-only (on-device sideload). Needs `google()` **+** JitPack `maven{ url="https://jitpack.io"; content{ includeGroup("com.github.xgouchet") } }` (transitive dep). |
| Play Billing | `com.android.billingclient:billing-ktx` | **9.1.0** (2026-06-18) | v7 is today's floor; **v8 mandatory 2026-08-31** → ship 9.1.0. |

## Hard constraints (verified)
- **WFP needs Wear OS 6 / API 36.** `WatchFacePushManager` methods "Added in API 36." Gate every use with `WatchFacePushManager.isSupported(context)` (returns false < API 36) so the `:wear` module can keep `minSdk 33`.
- **Pushed face APK:** `minSdk >= 33`, `hasCode="false"`, exactly one WFF face, no code. Allowed manifest tags only: `<manifest> <uses-feature> <uses-sdk> <application> <property> <meta-data>`. Package MUST be `<marketplaceAppId>.watchfacepush.<name>`.
- **Slot limit = 1 on WO6.** Installing face B over A = `updateWatchFace` (full replace). Never design for >1 slot.
- **Validation token** required per APK from `addWatchFace()/updateWatchFace()`. Tokens **don't expire**; regenerate only when APK bytes change. Default face token goes in the manifest meta-data `com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN`.
- **Permissions** (`:wear` manifest): `com.google.wear.permission.PUSH_WATCH_FACES` (required) + `com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE` (runtime, **one-shot, max 1 rejection** — on deny, route to app-details settings).
- **Billing one-time product:** non-consumable INAPP; `acknowledgePurchase()` within 3 days (never `consumeAsync`); restore via `queryPurchasesAsync()` on launch/foreground.
- **Play model:** publish as a NORMAL phone + Wear app (NOT a watch-face listing). Individual faces are NEVER published to Play — delivered at runtime via WFP. → the WO-G4 circular-icon rule (2026-07-15) does **not** apply. WFF mandatory since 2026-01-14 (faces are WFF ✓). 64-bit required 2026-09-15 (no native code → fine).

## Implementation cheat-sheet (from android/wear-os-samples `WatchFacePush` + android/androidify)

**Architecture** = androidify split: **phone `:app`** holds catalog + billing + bundled face APKs in `assets/`; streams the chosen APK over the Data Layer to a **companion `:wear`** app that installs via WFP. (`:watchface` = the bundled default WFF face.)

**Core add-or-update (the WFP primitive), androidx suspend wrapper:**
```kotlin
val wfp = WatchFacePushManagerFactory.createWatchFacePushManager(context) // hold at Application scope
val resp = wfp.listWatchFaces()                 // query FRESH every journey — NEVER persist slotId
if (resp.remainingSlotCount > 0) wfp.addWatchFace(apkFd, token)
else wfp.updateWatchFace(resp.installedWatchFaceDetails.first().slotId, apkFd, token)
// exceptions: AddWatchFaceException / UpdateWatchFaceException / ListWatchFacesException /
//             SetWatchFaceAsActiveException / RemoveWatchFaceException
```
**APK → PFD:** from a received temp file `ParcelFileDescriptor.dup(FileInputStream(tempApk).fd)`; from local assets use `ParcelFileDescriptor.createPipe()` + copy on IO (catch/ignore the IOException if WFP force-closes the read end on a bad APK).

**Data-Layer transport (androidify):** `CapabilityClient` advertise `wfp_install` only when `isSupported()` → phone discovers reachable node → `MessageClient.sendRequest("/initiate_transfer", {transferId, token})` → `ChannelClient.openChannel("/transfer_apk/$id")` + `sendFile(apkUri)`. Watch `WearableListenerService` (3 intent-filters: `/initiate_transfer` REQUEST_RECEIVED, `/transfer_apk` CHANNEL_EVENT, `/cancel_transfer` MESSAGE_RECEIVED) → `receiveFile` into cacheDir temp APK → install via WFP → `/finalize_transfer/<id>` back. packageName is read from the APK itself (`PackageManager.getPackageArchiveInfo`), NOT sent. Check `GoogleApiAvailability.checkApiAvailability` before using Data Layer.

**Activation strategy (compute BEFORE install):** enum {NO_ACTION_NEEDED, CALL_SET_ACTIVE_NO_USER_ACTION, FOLLOW_PROMPT_ON_WATCH, LONG_PRESS_TO_SET, GO_TO_WATCH_SETTINGS} from (hasActiveWatchFace, setActiveGranted, canRequest, apiUsed). Persist in DataStore.

**Token generation (build-time), buildSrc Gradle task:**
```kotlin
val result = DwfValidatorFactory.create().validate(apkFile, marketplaceAppId) // "com.dialed.app"
if (result.failures().isEmpty()) tokenFile.writeText(result.validationToken())
else result.failures().forEach { it.name(); it.failureMessage() }
```

**Gotchas to encode:** never persist slotId (re-`listWatchFaces()`); `setWatchFaceAsActive` is one-shot (track apiUsed, else fall back to on-phone education); prefer replace-with-default over `removeWatchFace`; compute activation strategy before install; single concurrent transfer (AtomicBoolean) matched on transferId + timeout; `updateWatchFace` same-package = iterative (keeps style), different-package = full replace.

**Canonical module layout** (androidify phone-push variant, adapted): `:app` (phone: Compose store + billing + `assets/<face>.apk` + `res/values/wfp_tokens.xml` + Data-Layer sender) · `:wear` (companion: WFP repo + listener service + default-face token meta-data) · `:wear-common` (shared constants/messages/strategy) · `:watchface` (bundled default WFF face). Token generation via `buildSrc` task. `libs.versions.toml` pins `watchfacePush=1.0.0`.
