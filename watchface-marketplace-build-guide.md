# Watch Face Push Marketplace — Build Guide for Claude Code

> **Purpose:** Build a single native Kotlin Android app that is the developer's permanent
> **watch-face hub**, serving two jobs at once:
>
> 1. **Commercial product** — bundles the whole collection of Watch Face Format (WFF) faces and sells
>    it as a **one-time purchase** (one payment unlocks every face). Faces are pushed to the connected
>    Wear OS watch via the **Watch Face Push (WFP)** API. **No backend, no CDN** — every face ships
>    inside the app.
> 2. **Personal testing harness** — the same app installs on the developer's phone (built from the
>    GitHub repo) and pushes faces to his **Pixel Watch 4** with no laptop, exercising the *real*
>    distribution path (validate → WFP slot install → activate) that customers will use.
>
> **This app is a living hub, not a one-off build.** ALL existing WFF faces in the repo are converted
> into it now, and EVERY future watch face the developer builds is added to this same app going
> forward — both to sell and to test. The "add a new face" routine (§3A) and the debug-only sideload
> tester (§6A) must be clean, repeatable, and seamless, because they'll be used constantly.

---

## 0. START HERE — point me at the source, then read before coding

**My watch faces live here:**

```
GITHUB_REPO_URL = <PASTE YOUR REPO URL HERE>
```

Before writing any code, do this in order:

1. **Clone / inspect `GITHUB_REPO_URL`.** Inventory every watch face project in it. For each face,
   record: current `applicationId`/package name, the declared WFF `format.version`, `minSdk`,
   whether it builds to an `.aab`/`.apk` today, and its preview/thumbnail asset. Produce a table
   named `FACE INVENTORY` and show it to me before proceeding.
2. **Read the official docs** (fetch these — they are the source of truth and override anything here
   if they conflict):
   - Watch Face Push overview: https://developer.android.com/training/wearables/watch-face-push
   - Configure the **Wear OS app** for WFP: https://developer.android.com/training/wearables/watch-face-push/wear-os-app
   - Configure the **phone app** for WFP: https://developer.android.com/training/wearables/watch-face-push/phone-app
   - `WatchFacePushManager` API reference: https://developer.android.com/reference/com/google/wear/services/watchfaces/watchfacepush/WatchFacePushManager
   - Play Billing one-time products: https://developer.android.com/google/play/billing
3. **Clone these official samples to copy patterns from** (do not reinvent):
   - `android/wear-os-samples` → the **WatchFacePush** sample (canonical WFP AndroidX usage).
   - `garanj/wfp-use-cases` (bundled-face + Data Layer examples).
   - `android/androidify` → `wear/.../watchfacepush/WatchFaceOnboardingRepository.kt`
     (real add-or-update-slot flow).
4. **Confirm the moving-target facts in §11 before you pin versions.** Do not hard-code a
   dependency version or fee number you haven't verified against Google's Maven repo / Play docs.
5. **Create and maintain a project context file at the repo root: `CLAUDE.md`.** This app is a
   living hub that will be revisited every time a new face is built, so future sessions need standing
   context. Write `CLAUDE.md` capturing: (a) the app's dual purpose (sell + test), (b) the key
   constraints from §1, (c) the **"add a new face" workflow from §3A** as the canonical routine, (d)
   the **dev/sideload testing loop from §6A**, (e) the current `FaceCatalog` contents, and (f) the
   verified dependency versions from §11 once confirmed. Keep `CLAUDE.md` updated whenever a face is
   added or a version is re-verified — it is the single source of truth for the ongoing workflow.

Then follow the phases in §5. **Stop at each GATE and report status before continuing.**

---

## 1. Non-negotiable constraints (these shape every decision)

These are confirmed from official Google sources. Design around them from the start.

1. **Wear OS 6+ only (API 36).** WFP does not exist below Wear OS 6. The app must detect support and
   degrade gracefully on older watches (disable "install to watch", explain why). Devices: Pixel
   Watch 4, Galaxy Watch 8 series ship with WO6; Pixel Watch 2/3 and Galaxy Watch 6/7 received it by
   update. **This is a small install base today** — build it, but don't expect volume yet.
2. **Slot limit = 1 on Wear OS 6.** The marketplace app can have exactly **one** face installed at a
   time. "Installing" face B while A is present is an **`updateWatchFace`** (full replacement), not a
   second install. There is no multi-face-on-watch management. Do not design for >1 slot.
3. **One WFF face per APK, with a strict package name.** Each pushed face APK must be named
   `<marketplaceAppId>.watchfacepush.<name>` (e.g. `com.mycompany.faces.watchfacepush.aurora`).
   The API rejects non-conforming names.
4. **Every face APK must be validated to get an install token.** Run each APK through the **Watch
   Face Push validation tool** → it emits a **validation token** string that is required by
   `addWatchFace()` / `updateWatchFace()`. Tokens don't expire; only regenerate when the APK changes.
   (This is a *different* tool from the WFF XML validator used when the faces were first built.)
5. **Faces are pure resources — no code.** Each face APK keeps `android:hasCode="false"`, declares
   **no Activity and no Service**, contains only `AndroidManifest.xml`, `resources.arsc`, `res/**`,
   `META-INF/**`, and is signed with a key **different** from the main app (use one shared key across
   all faces). `minSdk >= 33`.
6. **One-time purchase = one non-consumable INAPP product.** Never `consumeAsync` it. Acknowledge
   within 3 days or Play auto-refunds. Entitlement is a single boolean, owned by Play, cached locally.
7. **No backend.** All faces live in the app's `assets/`. Tokens ship as string resources.

---

## 2. Target architecture

Four canonical WFP components, but with no backend the "cloud storage" collapses into `assets/`:

| Canonical WFP piece            | This project                                                        |
|--------------------------------|---------------------------------------------------------------------|
| Cloud storage of validated APKs| **Bundled `assets/`**; tokens as string resources                   |
| Phone app (browse / buy / push)| `:app` — Jetpack Compose storefront + Play Billing + Data Layer sender |
| Wear OS app (WFP bridge)       | `:wear` — `WatchFacePushManager`, permissions, default face, listener |
| Phone↔watch transport          | `CapabilityClient` (detect) + `MessageClient` (commands) + `ChannelClient` (stream APK) |

**The install flow (implement exactly this):**

```
User taps "Install this face" on phone
  → phone checks entitlement (billing). Not owned? → launch purchase flow, stop.
  → phone checks CapabilityClient for a WFP-capable watch. None? → prompt to install wear app, stop.
  → phone opens a ChannelClient channel, streams assets/<face>.apk to the watch
  → phone sends the face's package name + validation token over MessageClient
  → watch WearableListenerService writes APK to cacheDir, wraps in ParcelFileDescriptor
  → watch calls listWatchFaces():
        remainingSlotCount > 0  → addWatchFace(pfd, token)
        else                    → updateWatchFace(existingSlotId, pfd, token)   // slot=1, so usually this
  → watch reports outcome back to phone (installed? active? set-active already used?)
  → phone shows activation guidance if the face isn't the active one
```

**Module layout:**

```
:app                                   // phone
  src/main/assets/                     // aurora.apk, nebula.apk, …  (validated WFP face APKs)
  src/main/res/values/wfp_tokens.xml   // token_aurora, token_nebula, …  (build-generated)
  src/main/res/drawable/               // thumbnails per face
  .../catalog/FaceCatalog.kt           // id, packageName, assetFile, tokenRes, thumbnail, name
  .../billing/BillingManager.kt        // connect, query, buy, acknowledge, restore
  .../data/EntitlementStore.kt         // DataStore-backed cached boolean
  .../wear/WatchConnection.kt          // Capability + Message + Channel sender
  .../ui/StoreScreen.kt, FaceDetailScreen.kt, PaywallScreen.kt
  .../MainViewModel.kt

:wear                                  // watch (contains code — unlike the faces)
  src/main/assets/default_watchface.apk
  .../WfpInstallService.kt             // WearableListenerService: receive channel, install/replace
  .../WatchFacePushRepository.kt       // list / add / update / isActive / setActive wrappers
  .../PackageReplacedReceiver.kt       // MY_PACKAGE_REPLACED → re-push bundled default
  .../ui/                              // minimal Compose: permission prompt, status

:watchface                             // the WFF default face, built to default_watchface.apk
  (resource-only WFF project — no code)
```

Keep a **unified `versionCode`** across `:wear` and `:watchface` so app and face versions stay in
sync. Keep the **watch module dumb** — it only receives an APK+token, updates the single slot,
handles the set-active permission, and reports status. All catalog/UI/billing logic lives on the phone.

---

## 3. Repackaging my existing faces (Phase 0 detail)

For **each** face found in `GITHUB_REPO_URL`:

1. **Rename the package** to `<marketplaceAppId>.watchfacepush.<name>`. Set `applicationId` in that
   face's `build.gradle` accordingly. Keep everything else about the WFF project intact
   (`hasCode="false"`, no Activity/Service, `com.google.wear.watchface.format.version` property,
   `standalone` meta-data, `res/raw/watchface.xml`).
2. **Confirm the WFF face still validates** with the WFF XML validator for its declared version
   (the faces were presumably already valid; re-check after the rename). Keep `minSdk >= 33`.
3. **Build a release APK** for the face, signed with the **shared watch-face key** (create one keystore
   used by all faces; it must differ from the `:app` signing key).
4. **Run the Watch Face Push validation tool** on that APK, passing the **marketplace app's** package
   name (not the face's). Capture the emitted **validation token**.
   - Prefer the **build-time CLI**: obtain `validator-push-cli` from Google's Maven repository and run
     it per face; verify the exact current artifact + version at build time (see §11). Conceptually:
     ```
     java -jar validator-push-cli-<VERSION>.jar \
       --apk_path=aurora.apk \
       --package_name=<marketplaceAppId>
     ```
   - Store each token in `:app` `res/values/wfp_tokens.xml` as `token_<name>`, keyed to the asset file.
5. **Copy the validated APK** into `:app/src/main/assets/<name>.apk`.
6. **Register the face** in `FaceCatalog` (id, package name, asset filename, token resource, thumbnail,
   display name, optional description/price-tier metadata).

Pick one face to also be the **default** (installed at app-install time): copy its validated APK to
`:wear/src/main/assets/default_watchface.apk` and add its token to the wear manifest (see §5, Phase 5).

**Asset size discipline:** APKs travel over Bluetooth. Keep each face lean (respect WFF memory limits:
≤10 MB ambient, ≤100 MB interactive; use WebP, subset fonts). Consider `androidResources { noCompress += "apk" }`
if you use `assets.openFd(...)` directly (see §6 loader).

---

## 3A. The ongoing workflow — adding a new face (THE routine, used forever)

This is the most-used workflow in the project. Every future watch face the developer builds flows into
this app through these exact steps. **Make it a one-command script** so adding a face is trivial, not a
manual chore. Build `scripts/add_face.sh` (or a Gradle task) that automates steps 1–5 below and only
leaves step 6 (thumbnail + display metadata) for a human.

**To add a new face `<name>` (its WFF project is already built and validated as a normal face):**

1. **Rename** its package to `<marketplaceAppId>.watchfacepush.<name>`.
2. **Build** its release APK signed with the **shared watch-face keystore** (same key across all faces;
   different from the `:app` key).
3. **Validate** the APK with the Watch Face Push validation tool (CLI, `--package_name=<marketplaceAppId>`);
   capture the token.
4. **Bundle**: copy the validated APK to `:app/src/main/assets/<name>.apk`.
5. **Register the token**: add `token_<name>` to `:app/res/values/wfp_tokens.xml`.
6. **Register the face**: add a `FaceCatalog` entry (id, package name, asset filename, token resource,
   thumbnail drawable, display name, description). Drop the thumbnail in `res/drawable/`.
7. **Update `CLAUDE.md`** — append the new face to the recorded catalog so context stays current.

`scripts/add_face.sh` contract (implement this): takes a path to a built face project (or APK) and a
`<name>`, performs 1–5 idempotently, prints the token, and echoes the `FaceCatalog` line to paste. Running
it twice for the same face must not duplicate assets or tokens. After running it, the developer rebuilds
the app, installs from GitHub on the phone, and the new face is both **sellable** (in the storefront) and
**testable** (via normal install or dev mode §6A).

> **Design implication:** because faces are bundled, adding one to the *shipping collection* requires an
> app rebuild + reinstall. That's correct for the catalog. For fast *iteration* on a face you're still
> designing, use **dev/sideload mode (§6A)** or ADB instead — don't rebuild the whole app per tweak.

---

## 4. Dependencies, permissions, Gradle

**⚠ Verify the WFP library coordinate before pinning (§11).** Two forms have appeared in sources:
`androidx.wear.watchfacepush:watchfacepush` and `androidx.wear.watchface:watchface-push`. Check the
**official WatchFacePush sample's `build.gradle`** and Google's Maven repo, then use whatever the
current sample uses. Do not guess.

**`:wear` `build.gradle.kts`:**
```kotlin
dependencies {
    implementation("<VERIFIED_WFP_COORDINATE>:<VERIFIED_VERSION>")   // see §11
    implementation("com.google.android.gms:play-services-wearable:<current>")  // Data Layer
    // Compose for Wear as needed for the minimal UI
}
```

**`:wear` `AndroidManifest.xml`:**
```xml
<!-- Required to use Watch Face Push. -->
<uses-permission android:name="com.google.wear.permission.PUSH_WATCH_FACES" />
<!-- Only if you programmatically set the active face (allowed ONCE). -->
<uses-permission android:name="com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE" />

<application ...>
  <!-- Default face installed at app-install time: point at the bundled APK's token. -->
  <meta-data
      android:name="com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN"
      android:value="@string/default_wf_token" />
</application>
```
Reference-doc permission constants: `WatchFacePushManager.PERMISSION_PUSH_WATCH_FACES` and
`PERMISSION_SET_PUSHED_WATCH_FACE_AS_ACTIVE`. **`SET_..._AS_ACTIVE` is a runtime permission with a
max rejection count of 1** — if the user denies it, you cannot ask again; send them to settings.

**`:app` `build.gradle.kts`:** Play Billing (`com.android.billingclient:billing-ktx`, v7+ required for
new apps — verify current major), `play-services-wearable`, Compose, DataStore.

---

## 5. Phased build plan (stop at each GATE)

### Phase 0 — Faces & catalog
Do §3 for every face. Produce `FACE INVENTORY` + populated `assets/`, `wfp_tokens.xml`, `FaceCatalog`.
**GATE:** every face is renamed, validated, tokenised, bundled, and listed in `FaceCatalog`. Show me the table.

### Phase 1 — Phone storefront shell
Compose UI: `StoreScreen` (grid of faces from `FaceCatalog` with thumbnails) → `FaceDetailScreen`
(large preview, name, description, "Install" button — disabled/placeholder for now). No billing, no watch yet.
**GATE:** I can browse the whole collection on the phone.

### Phase 2 — Billing (one-time unlock)
Implement `BillingManager`: connect `BillingClient` with `enablePendingPurchases()`; `queryProductDetailsAsync`
for the single INAPP product (`unlock_all_faces`); `launchBillingFlow`; in `onPurchasesUpdated` handle
`PURCHASED` → **acknowledge** (never consume) → grant; ignore `PENDING` until it flips. On cold start,
`queryPurchasesAsync(INAPP)` to **restore**. Persist a cached boolean in `EntitlementStore` (Play is source
of truth). Add `PaywallScreen`; gate the "Install" action behind entitlement.
**GATE:** test purchase via a license-tester account unlocks the collection; killing+reopening the app keeps it
unlocked; reinstall restores it.

### Phase 3 — Wear bridge (prove WFP on real hardware first)
Create `:wear` with `WatchFacePushRepository` wrapping `WatchFacePushManager` (see §6) and a temporary
test path that installs a face copied from **wear** assets locally (no phone transport yet). Handle the
`PUSH_WATCH_FACES` permission. Run on a **physical Wear OS 6 watch**.
**GATE:** a bundled face installs onto the watch and appears in the picker, end-to-end, on real hardware.
*(De-risk the least-familiar API here before building the transport.)*

### Phase 4 — Phone→watch transport
`WatchConnection` on phone: `CapabilityClient` to detect a WFP-capable watch (the wear app advertises a
capability, e.g. `wfp_install`, only when `WatchFacePushManager` reports supported); `MessageClient` to
send `{packageName, token}`; `ChannelClient` to **stream the chosen `assets/<face>.apk`**. On watch,
`WfpInstallService : WearableListenerService` receives the channel, writes to `cacheDir`, opens a
`ParcelFileDescriptor`, and calls the add-or-update logic. Wire the entitlement-gated "Install" button to this.
**GATE:** tapping Install on the phone installs that specific face on the watch; switching faces replaces
the single slot correctly.

### Phase 4A — Dev / sideload testing mode (debug only) — build it now, use it for everything after
Implement §6A: a debug-only sideload screen with a file picker + **on-device token generation** (runtime
validator) that pushes any chosen APK through the same transport, bypassing the paywall. This is a small add
on top of Phase 4, and once it exists you can use it to test every subsequent face on the real watch without
ADB or a laptop.
**GATE:** in a debug build, pick an arbitrary valid face APK from phone storage → it validates on-device →
installs on the Pixel Watch 4. Confirm it is compiled OUT of the release build.

### Phase 5 — Default face + activation UX + updates
Bundle `default_watchface.apk` in `:wear/assets` with its token in the manifest (§4) so a branded face is
present at install time. Implement `setWatchFaceAsActive` (usable **once**) with the runtime-permission flow;
when unavailable, show phone-side education (touch-and-hold gesture to pick the face). Add
`PackageReplacedReceiver` for `ACTION_MY_PACKAGE_REPLACED` to re-push updated bundled faces (fires only after
the app has been launched once).
**GATE:** fresh install shows the default face in the picker; activation guidance works; updating the app
refreshes bundled faces.

### Phase 6 — Polish, degrade, publish-prep
Graceful degradation on non-WFP watches (feature detection via `isSupported` + `Build.VERSION.SDK_INT >= 36`);
small on-watch APK cache for instant revert; power hygiene (WorkManager `requiresCharging`+`UNMETERED`, no
polling for active face, `setLocalOnly` notifications); robust error/empty states; restore-purchases entry point.
**GATE:** clean behavior on a non-WO6 watch and with no watch connected; no crashes; ready for closed testing.

---

## 6. Key code patterns (confirmed API shape)

**Add-or-update the single slot** (this is the core of `WatchFacePushRepository`):
```kotlin
val wfp = WatchFacePushManagerFactory.createWatchFacePushManager(context)

suspend fun installOrReplace(apkFd: ParcelFileDescriptor, token: String): Result<Unit> = try {
    val state = wfp.listWatchFaces()               // never persist slotId; always re-query first
    if (state.remainingSlotCount > 0) {
        wfp.addWatchFace(apkFd, token)
    } else {
        val slotId = state.installedWatchFaceDetails.first().slotId
        wfp.updateWatchFace(slotId, apkFd, token)  // same pkg = iterative update; different pkg = full replace
    }
    Result.success(Unit)
} catch (e: Exception) {                            // AddWatchFaceException / UpdateWatchFaceException
    Result.failure(e)
}
```
Feature detection: `wfp.isSupported` is false for API < 36. Prefer **replace-with-default over
`removeWatchFace`** so your face stays discoverable. Don't call `isWatchFaceActive()` right after an update
(the swap takes time); confirm active state *before* updating instead.

**Load a bundled APK into a `ParcelFileDescriptor`** (assets are compressed, so copy to cache):
```kotlin
fun assetToPfd(context: Context, assetName: String): ParcelFileDescriptor {
    val out = File(context.cacheDir, assetName)
    context.assets.open(assetName).use { input -> out.outputStream().use { input.copyTo(it) } }
    return ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)
}
// Alternative if stored uncompressed (Gradle: androidResources { noCompress += "apk" }):
//   context.assets.openFd(assetName).parcelFileDescriptor
```

**Billing acknowledge (critical — 3-day rule):**
```kotlin
if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
    if (!purchase.isAcknowledged) {
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { r -> if (r.responseCode == BillingClient.BillingResponseCode.OK) grantEntitlement() }
    } else grantEntitlement()
} // PENDING → do NOT grant yet
```
Never `consumeAsync`. Harden with `setObfuscatedAccountId`. Billing needs Google Play services (real device / Play-enabled emulator).

**Phone→watch transfer:** use `MessageClient` to coordinate, then `ChannelClient` to stream the APK bytes
(this is exactly what Androidify does). On the watch, receive in a `WearableListenerService`.

---

## 6A. Dev / sideload testing mode (DEBUG builds only) — the laptop-free test loop

This is what turns the app into a real testing tool. It lets the developer push **any** face APK to the
Pixel Watch 4 straight from the phone — including faces **not yet bundled** into the collection — with no
laptop and no ADB. It must be **compiled out of release builds** so the runtime validator and file-picker
never ship to customers.

**Build it in the `debug` flavor/build-type only** (guard with `BuildConfig.DEBUG` and/or a `dev`
product flavor; keep the runtime-validator dependency in the debug configuration only).

**What dev mode does:**
1. A hidden **"Dev / Sideload"** screen (reachable only in debug builds — e.g. long-press the app title).
2. **File picker** via the Storage Access Framework (`ACTION_OPEN_DOCUMENT`) to choose any `.apk` from the
   phone (Downloads, a synced folder, etc.).
3. **Generate the validation token ON-DEVICE** using the runtime WFP validator library, so no CLI/laptop
   is needed:
   ```kotlin
   // DEBUG ONLY. Requires the runtime validator dep (verify coordinate/version at build time, §11):
   //   debugImplementation("com.google.android.wearable.watchface.validator:validator-push-android:<VER>")
   // repositories: google() + maven(jitpack, content { includeGroup("com.github.xgouchet") })
   val validator = DwfValidatorFactory.create()
   val result = validator.validate(pickedApkFile, MARKETPLACE_APP_ID)
   val token = if (result.failures().isEmpty()) result.validationToken()
               else { result.failures().forEach { Log.e("WFP", "${it.name()}: ${it.failureMessage()}") }; null }
   ```
4. **Push** the chosen APK + token to the watch through the **same** `ChannelClient`/`MessageClient`
   transport and the **same** `installOrReplace()` slot logic used in production (§6). No entitlement/billing
   gate in dev mode — it bypasses the paywall so you can test freely.
5. Show the round-trip result (installed? active? errors) so you can verify a face end-to-end the way a
   customer would receive it.

**Two important notes for correctness:**
- The pushed APK still must obey the **naming rule** (`<marketplaceAppId>.watchfacepush.<name>`) and the
  **single-face / no-code** rules, or WFP rejects it. Dev mode tests the *distribution path*, not a way
  around the format rules.
- Because the token is generated on-device, this is the piece that lets you sideload a **brand-new** face
  without first running the desktop CLI — ideal for "just built it, want to see it on my wrist now."

**When to use which loop (tell the developer, don't over-collapse them):**
- **ADB (`adb install`)** — fastest loop for *designing* a face (tweak XML → reinstall → glance). Skips
  validation/Bluetooth. Keep using it; the app is not meant to replace it.
- **Dev/sideload mode** — laptop-free push of an arbitrary face, and the **acceptance test** of the true
  customer path (validate → WFP slot install → activate). Use before bundling/shipping a face.
- **Normal storefront install** — the production path; use once a face is bundled into the collection.

---

## 7. Play Console / publishing

- **A WFP marketplace is a normal app + a Wear app**, NOT a "watch face" listing. The individual faces
  are **not** published to Play — your app delivers them via WFP. You still self-validate each face.
- **WFF is mandatory** (since 14 Jan 2026). Your faces are WFF, so you're compliant; legacy faces can't be
  installed/updated/sold.
- **One-time product:** Play Console → Monetize → Products → In-app products → create `unlock_all_faces`
  (one-time), set price, activate. Add **License testers** (Setup → License testing) to test without being charged.
- **Watch face listing-icon policy (WO-G4):** from 15 Jul 2026, watch-face apps need a centered circular
  watch-face icon asset — verify current rule at submission.
- **64-bit:** from 15 Sep 2026 all Wear OS apps must support 64-bit — verify.
- **Closed testing is required for Wear OS before production.** Budget for review time.

Pricing note (my call, not a fact to hard-code): the WO6 install base is premium (Pixel/Galaxy owners in
US/EU). If you want volume via a low ₹299-style price, that segment barely exists on WO6 yet — consider
pricing for the **global premium** buyer and revisiting India/volume as the WO6 base grows. Re-confirm Play
service fees for your region (the June 2026 restructuring to ~10% service + separate ~5% billing is rolling
out region-by-region through 2027).

---

## 8. Testing

- **Physical Wear OS 6 watch is mandatory** for real validation (Bluetooth transfer speed, true slot
  behavior, activation UX). Pixel Watch 4 / Galaxy Watch 8 paired to a phone.
- The **Wear OS 6 emulator** can exercise the API paths but not real transfer/billing behavior.
- **Billing** needs Play services + license-tester accounts; test purchase, acknowledge, restore, reinstall.
- Test: slot replacement when switching faces; uninstall behavior (default face was a real install);
  non-WO6 watch (graceful disable); no watch connected; set-active permission denied (settings fallback).

---

## 9. Gotchas (encode these)

- **Slot = 1**: switching faces loses the previous face's user-style settings (full replacement). Tell the user if relevant.
- **Never persist `slotId`** — external uninstalls invalidate it. Re-`listWatchFaces()` immediately before any slot op.
- **`setWatchFaceAsActive` is one-shot** — after the user picks another developer's face you can't force yours back; fall back to on-phone education.
- **Bundled faces don't auto-update** on app update — re-push via `MY_PACKAGE_REPLACED` (fires only after first launch).
- **Keep the watch module minimal**; do analytics/version checks only on charge + unmetered; never poll active face.
- **Unified `versionCode`** across `:wear` and `:watchface`.

---

## 10. Definition of done

- [ ] Every face from the repo is renamed to the WFP convention, validated, tokenised, and bundled in `:app/assets`.
- [ ] Storefront browses the full collection with correct thumbnails/previews.
- [ ] One-time purchase unlocks everything; acknowledges within 3 days; restores after reinstall; never consumable.
- [ ] Entitlement-gated install pushes the selected face to a physical WO6 watch and replaces the slot on switch.
- [ ] **Dev/sideload mode (debug only)** pushes an arbitrary face APK with on-device validation; verified compiled OUT of release.
- [ ] **`scripts/add_face.sh`** (or Gradle task) automates the add-a-face routine idempotently and prints the token + catalog line.
- [ ] **`CLAUDE.md`** exists at the repo root and reflects the current catalog, workflow, constraints, and verified versions.
- [ ] Branded default face installs at app-install time; activation guidance works.
- [ ] Graceful, non-crashing behavior on non-WO6 watches and with no watch connected.
- [ ] Play Console: app + Wear app configured, one-time product live, closed testing passed.

---

## 11. Re-verify at build time (do NOT trust these blind)

Google moves these; confirm against primary sources before finalizing:

1. **WFP library coordinate + version.** `androidx.wear.watchfacepush:watchfacepush` vs
   `androidx.wear.watchface:watchface-push` — use whatever the **official WatchFacePush sample** currently
   uses; pin the current stable (the library moved alpha → stable across 2025–2026).
2. **Watch Face Push validation tool** artifact + version (was `…validator-push*:1.0.0-alphaNN`) — it's
   updated periodically; re-run occasionally even on unchanged APKs. This has **two forms**: the **CLI**
   (`validator-push-cli`, used by `add_face.sh` at build time) and the **Android runtime library**
   (`validator-push-android`, used by dev/sideload mode §6A; needs Google Maven + the JitPack repo scoped
   to `com.github.xgouchet`). Verify both coordinates/versions.
3. **Play Billing Library** current major version and any new mandates.
4. **Slot count** (1 on WO6 — may change in later OS versions) and the **WFP min OS** (WO6/API 36).
5. **Play policy dates/fees:** WO-G4 icon (15 Jul 2026), 64-bit (15 Sep 2026), and the region-phased
   service-fee restructuring — check Play Console Help for your region.

If any of these can't be confirmed, say so once and cite the primary source rather than hard-coding a value.

---

### Provenance note
Confirmed from official Google docs/samples: WFP architecture, `WatchFacePushManager` surface, permissions,
package-naming rule, validation-token flow, slot = 1, default-face mechanism, billing acknowledge/consume
rules, and Play policy dates. Reasonable engineering recommendations (not verbatim from one sample): exact
module names, file layout, the assets→PFD helper, and the phase ordering. The WFP dependency coordinate and
validator version are the two most likely things to have drifted — verify them first (§11).
