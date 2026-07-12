# R5 — Installed / Active-Face Query Protocol + the "Distorted Install" Preview (#2 / #5)

Research doc only. No XML edits, no builds, no tags. Primary sources cited inline; anything
not confirmable from a primary source is marked `[UNVERIFIED]`.

---

## Headline

- **PART A is fully feasible with the shipped WFP API.** `WatchFacePushManager.listWatchFaces()`
  returns `installedWatchFaceDetails[]` (each a slot with `slotId`, `packageName`, `versionCode`)
  plus `remainingSlotCount`; `isWatchFaceActive(packageName)` returns a `Boolean`. Because the
  Wear OS 6 **slot limit is 1**, at most one Dialed face is ever installed, so the pair
  "which Dialed face is installed" (= the single `packageName`) **and** "is it active" (=
  `isWatchFaceActive(thatPackage)`) is answerable with exactly one `listWatchFaces()` + one
  `isWatchFaceActive()` call. There is **no** API that returns the active package directly — you
  must probe it — but for our single-slot marketplace that is sufficient.
- **PART B: there is no app-side aspect-ratio bug on any Compose path we control.** Previews are
  **450×450 square**, and both `FaceDial` composables use `ContentScale.Crop` + `CircleShape`,
  which cannot distort a square source. The "distorted dimensions while WearOS is setting the
  face" is therefore the **platform's own WFP activation preview UI**, which renders outside our
  Compose tree. **✅ SCREENSHOT RECEIVED 2026-07-12 — it CONFIRMS candidate (a).** The stretched
  oval is smaller than any `FaceDial` (≈45×34 dp vs 58–97 dp), and geometry proves no wear
  composable can render an oval (rigid square box + `Crop` → circle), so it is the system
  "applying watch face" thumbnail caught mid-transition behind Dialed's "Connected" chrome. Full
  analysis: **`R5-screenshot-evidence.md`.** No app-side fix; the real #5 remedy is a Phase-5
  concern (cover the system moment with Dialed's own circular Concierge where the strategy allows).

---

# PART A — Installed / Active-Face Query Protocol (Phase 1)

## A.1 What the WFP API surfaces (confirmed)

From the WFP guide and API reference:

- `listWatchFaces()` → response with:
  - `remainingSlotCount: Int`
  - `installedWatchFaceDetails: List<…>` — each element (a "watch face slot") exposes
    **`slotId: String`**, **`packageName: String`**, **`versionCode`**.
- `isWatchFaceActive(watchFacePackageName: String): Boolean` — you must already know the package
  name; returns only a boolean for *that* package. **No method returns the active package name.**
- `setWatchFaceAsActive(slotId)` — **callable only once** (permission-gated); after that the user
  sets the active face manually.
- `removeWatchFace(slotId)` — throws `RemoveWatchFaceException` on failure.
- **Slot limit on Wear OS 6 = 1.** Slot IDs are **not persistent** — always re-query immediately
  before any slot-based op (the repo already does this; `WatchFacePushRepository` never persists
  a slotId).

Sources:
- Watch Face Push guide — https://developer.android.com/training/wearables/watch-face-push
- Configure your Wear OS app for WFP (listWatchFaces / installedWatchFaceDetails / isWatchFaceActive
  / removeWatchFace snippets) — https://developer.android.com/training/wearables/watch-face-push/wear-os-app
- WatchFacePushManager reference — https://developer.android.com/reference/com/google/wear/services/watchfaces/watchfacepush/WatchFacePushManager
- androidify `WatchFaceOnboardingRepository.kt` (real `hasActiveWatchFace` = `listWatchFaces()
  .installedWatchFaceDetails.any { isWatchFaceActive(it.packageName) }`) —
  https://github.com/android/androidify/blob/main/wear/src/main/java/com/android/developers/androidify/watchfacepush/WatchFaceOnboardingRepository.kt

**Verdict — "which Dialed face is installed AND is it active" is answerable:** yes. The single
installed detail's `packageName` names the face; `isWatchFaceActive(packageName)` says whether it
is the live face. Our repo already has both primitives (`hasInstalledFace()`, `hasActiveWatchFace()`);
Phase 1 only needs a combined `installedState()` that returns the *package name* rather than a bare
boolean, plus `removeWatchFace(slotId)`.

## A.2 Repo additions (watch side — `WatchFacePushRepository`)

```kotlin
/** Slot-1 marketplace snapshot: which Dialed face is installed + whether it is the live face. */
data class InstalledState(
    val installedPackages: List<String>,   // 0..1 on Wear OS 6 (slot limit = 1)
    val activePackage: String?,            // the installed pkg iff it is currently active, else null
)

suspend fun installedState(): InstalledState = try {
    val wfp = manager()
    val details = wfp.listWatchFaces().installedWatchFaceDetails
    val installed = details.map { it.packageName }
    val active = details.firstOrNull { wfp.isWatchFaceActive(it.packageName) }?.packageName
    InstalledState(installed, active)
} catch (e: Exception) {
    Log.w(TAG, "installedState ${e.javaClass.simpleName}: ${e.message}", e)
    InstalledState(emptyList(), null)   // never throws — same guard style as the rest of the repo
}

suspend fun removeWatchFace(slotId: String): Boolean = try {
    manager().removeWatchFace(slotId)
    true
} catch (e: WatchFacePushManager.RemoveWatchFaceException) {
    Log.w(TAG, "removeWatchFace failed: ${e.message}", e); false
} catch (e: Exception) {
    Log.w(TAG, "removeWatchFace unexpected ${e.javaClass.simpleName}: ${e.message}", e); false
}
```

To uninstall a face by **package name** (the phone sends a package, not a slotId — slotIds are
non-persistent and unknown to the phone), resolve pkg → slotId at call time:

```kotlin
suspend fun removeByPackage(packageName: String): WatchFaceUninstallResult = try {
    val details = manager().listWatchFaces().installedWatchFaceDetails
    val slot = details.firstOrNull { it.packageName == packageName }
        ?: return WatchFaceUninstallResult.NOT_FOUND
    if (removeWatchFace(slot.slotId)) WatchFaceUninstallResult.REMOVED
    else WatchFaceUninstallResult.FAILED
} catch (e: Exception) {
    WatchFaceUninstallResult.FAILED
}
```

## A.3 Wire protocol — matching the existing dep-free `WearConstants` style

The existing contract (see `wear-common/.../WearConstants.kt`) is: **no kotlinx-serialization**;
control payloads are newline-separated UTF-8 (`encodeInitiate`/`decodeInitiate`) or a single result
byte = enum ordinal (`encodeResult`/`decodeResult`); the transferId travels in the message PATH.
Phase 1 stays in that style — two new request paths, two new codec pairs.

### A.3.1 Query state — `PATH_QUERY_STATE`

- **Path:** `const val PATH_QUERY_STATE = "/dialed/query_state"`
- **Transport:** phone → watch `MessageClient.sendRequest(nodeId, PATH_QUERY_STATE, EMPTY)`.
  Request has **no payload** (`ByteArray(0)`); it is a pure "report your marketplace state" RPC.
  Use `sendRequest` (not `sendMessage`) so the reply rides the same round-trip, mirroring the
  existing `RESPONSE_PROCEED/RESPONSE_BUSY` initiate handshake.
- **Reply payload (watch → phone), newline-separated UTF-8:**

  ```
  <activePackageOrEmpty>\n
  <installedPkg1>\n
  <installedPkg2>            (0..N further lines; N = 0 or 1 on Wear OS 6)
  ```

  Line 0 = the active Dialed package (empty string if none of our faces is active).
  Lines 1..N = every installed Dialed package (empty list ⇒ only the blank line 0 remains).
  Encoding matches `encodeInitiate` (a `joinToString("\n")` of UTF-8 strings).

```kotlin
// wear-common/.../WearConstants.kt additions
const val PATH_QUERY_STATE = "/dialed/query_state"

fun encodeQueryState(activePackage: String?, installedPackages: List<String>): ByteArray =
    (listOf(activePackage.orEmpty()) + installedPackages)
        .joinToString(SEP).toByteArray(Charsets.UTF_8)

fun decodeQueryState(bytes: ByteArray): QueryStateResult {
    val lines = String(bytes, Charsets.UTF_8).split(SEP)
    val active = lines.getOrElse(0) { "" }.ifEmpty { null }
    val installed = lines.drop(1).filter { it.isNotEmpty() }
    return QueryStateResult(activePackage = active, installedPackages = installed)
}

data class QueryStateResult(
    val activePackage: String?,
    val installedPackages: List<String>,
)
```

> Design note: encoding a **list** (rather than a single "the one face") future-proofs against a
> Wear OS release that raises the slot limit above 1, at zero cost today. `decodeQueryState` filters
> blank installed lines so an all-empty state decodes to `(null, [])` cleanly.

### A.3.2 Uninstall — `PATH_UNINSTALL`

- **Path:** `const val PATH_UNINSTALL = "/dialed/uninstall"`
- **Transport:** phone → watch `sendRequest(nodeId, PATH_UNINSTALL, <targetPackage UTF-8>)`.
  Request payload = the target **package name** (phone knows the package from its catalog; it does
  **not** know the volatile slotId). Watch resolves pkg → slotId then `removeWatchFace`.
- **Reply payload:** single byte = `WatchFaceUninstallResult.ordinal`, identical shape to
  `encodeResult`/`decodeResult`.

```kotlin
const val PATH_UNINSTALL = "/dialed/uninstall"

fun encodeUninstallRequest(packageName: String): ByteArray =
    packageName.toByteArray(Charsets.UTF_8)

fun decodeUninstallRequest(bytes: ByteArray): String =
    String(bytes, Charsets.UTF_8).trim()

fun encodeUninstallResult(result: WatchFaceUninstallResult): ByteArray =
    byteArrayOf(result.ordinal.toByte())

fun decodeUninstallResult(bytes: ByteArray): WatchFaceUninstallResult {
    val idx = bytes.firstOrNull()?.toInt() ?: return WatchFaceUninstallResult.FAILED
    return WatchFaceUninstallResult.values().getOrNull(idx) ?: WatchFaceUninstallResult.FAILED
}

/** Ordinal-stable — APPEND new states only, never reorder (byte wire = ordinal). */
enum class WatchFaceUninstallResult { REMOVED, NOT_FOUND, FAILED }
```

**Ordinal-stability caveat** (applies to both `WatchFaceInstallResult` and the new
`WatchFaceUninstallResult`): the wire value is the enum ordinal, so entries may only be **appended**;
reordering silently corrupts cross-version messages. Call this out in the enum KDoc.

### A.3.3 Watch-side listener wiring

`DialedListenerService` (already handles `onRequest` for initiate) adds two `onRequest` branches:

```kotlin
override fun onRequest(node: String, path: String, data: ByteArray): ByteArray? = when (path) {
    WearConstants.PATH_QUERY_STATE -> {
        val s = repo.installedState()          // suspend → run on the service's scope, block for reply
        WearConstants.encodeQueryState(s.activePackage, s.installedPackages)
    }
    WearConstants.PATH_UNINSTALL -> {
        val target = WearConstants.decodeUninstallRequest(data)
        WearConstants.encodeUninstallResult(repo.removeByPackage(target))
    }
    else -> null   // existing initiate/transfer paths handled elsewhere
}
```

(`onRequest` is synchronous; use the same `runBlocking`/service-scope pattern the initiate path
already uses to await the suspend repo calls before returning the reply bytes.)

## A.4 Mapping the returned WFP package back to a catalog id (`:app`)

The catalog already stores the exact WFP package on every `Face`:
`Face.packageName = "com.dialed.app.watchfacepush.<series>.<face>"` (all lowercase, e.g.
`com.dialed.app.watchfacepush.kinetik.orrery`; verified across all 18 entries in
`app/.../catalog/FaceCatalog.kt` and the facepack `applicationId`s). The mapping is therefore a
**pure lookup** — no string parsing, no heuristics:

```kotlin
// app/.../catalog — build once (18 entries)
private val byPackage: Map<String, Face> = FaceCatalog.faces.associateBy { it.packageName }

fun faceForPackage(pkg: String): Face? = byPackage[pkg]
fun catalogIdForPackage(pkg: String): String? = byPackage[pkg]?.id
```

So a `QueryStateResult` decodes to UI state as:

```kotlin
val active: Face? = result.activePackage?.let { faceForPackage(it) }
val installed: List<Face> = result.installedPackages.mapNotNull { faceForPackage(it) }
```

A package that isn't in `byPackage` (a non-Dialed face, or a face the user side-loaded) simply maps
to `null` and is ignored — the marketplace UI only reasons about faces it shipped.

## A.5 Part-A spec summary (execution-ready)

1. **`wear-common/WearConstants.kt`**: add `PATH_QUERY_STATE`, `PATH_UNINSTALL`,
   `encodeQueryState`/`decodeQueryState` + `QueryStateResult`, `encodeUninstallRequest`/`decode…`,
   `encodeUninstallResult`/`decode…` + `enum WatchFaceUninstallResult { REMOVED, NOT_FOUND, FAILED }`.
   Keep newline/byte-ordinal encoding identical to `encodeInitiate`/`encodeResult`. Add the
   ordinal-stability caveat to both result enums.
2. **`wear/.../WatchFacePushRepository.kt`**: add `installedState(): InstalledState`
   (`listWatchFaces()` + `isWatchFaceActive` probe, guarded, never throws) and
   `removeWatchFace(slotId)` + `removeByPackage(packageName)` (re-query slotId at call time).
3. **`wear/.../DialedListenerService.kt`**: handle `PATH_QUERY_STATE` (reply
   `encodeQueryState`) and `PATH_UNINSTALL` (reply `encodeUninstallResult`) in `onRequest`, awaiting
   the suspend repo calls with the existing service-scope pattern.
4. **`app` (phone)**: `sendRequest(PATH_QUERY_STATE)` → `decodeQueryState` → map via
   `FaceCatalog.faces.associateBy { it.packageName }` to render "Installed"/"Active" badges;
   `sendRequest(PATH_UNINSTALL, pkg.toByteArray())` → `decodeUninstallResult` for a "Remove" action.
5. **Permissions:** query needs none beyond the WFP install permission the app already holds;
   `removeWatchFace` is covered by `PERMISSION_PUSH`. `setWatchFaceAsActive` is still one-shot and
   unchanged.

---

# PART B — the #5 "distorted dimensions while setting the face"

**Established facts (from GROUNDING + source read):**
- Every `preview.png` is exactly **450×450 — square**. `FacePreviewExtractor.extract()` calls
  `drawable.toBitmap()`, which uses the drawable's **intrinsic** size (= 450×450), producing a
  **square** bitmap. PNG round-trip via `cache()`/`loadCached()` preserves dimensions.
- Both Compose previews use **`ContentScale.Crop` + `CircleShape`**. `Crop` preserves the source
  aspect ratio, scales to fill, and center-crops the overflow; on a square source into a square/
  circular box **there is no overflow and no distortion**. `ContentScale.Crop` never stretches an axis.
- The in-app install animation (`ReceiveScreen`: Receiving / Installing / Failed) always passes
  `preview = null` → it draws the vector `DialMark` placeholder, **not** a bitmap. So during *our*
  install screen there is no bitmap that could distort.

## B.1 Candidate-cause table

| # | Candidate | Can it distort a 450×450 square preview? | Fix / verdict |
|---|-----------|------------------------------------------|---------------|
| a | **Platform WFP set-active / apply / picker preview UI** (the system chrome shown by Wear OS while `setWatchFaceAsActive` runs, or the system watch-face picker thumbnail) | **Yes — and it is out of our control.** This UI is rendered by Wear OS / Watch Face Push services, not by our Compose tree. If it letterboxes, stretches, or crops the face's own preview asset, that is platform behaviour. **This is the most likely culprit** given (b)–(d) below are all clean. | Not an app bug. Mitigation options if confirmed: (i) accept it (cosmetic, system-owned); (ii) verify each *face APK's* embedded `preview` asset is itself square 450×450 and center-safe (it is — same asset we extract); (iii) file/track as a platform limitation. **Screenshot required to confirm this is the surface shown.** |
| b | **`FacePreviewExtractor.toBitmap()` intrinsic-size** | **No.** `toBitmap()` with no width/height args uses the drawable's intrinsic 450×450 → square bitmap. It cannot introduce a non-square bitmap from a square source. | No change. (Only risk: if a *future* face shipped a non-square `preview` drawable — all 18 today are 450×450. Optional hardening: `toBitmap(width=450, height=450)` to force square regardless of source.) |
| c | **`ConciergeScreen` / wear `HomeScreen` `FaceDial` `ContentScale`** | **No.** `FaceDial(preview=…)` uses `ContentScale.Crop` inside a `.size(size).clip(CircleShape)` box with a `fillMaxSize().clip(CircleShape)` image. Square→circle via Crop = undistorted. | No change. `Crop` (not `FillBounds`) is already correct — do **not** switch to `FillBounds`/`FillWidth`, which *would* distort. |
| d | **Phone-app `FaceDial(face=…)` path** | **No.** Different composable, but same recipe: `painterResource(previewRes)` + `ContentScale.Crop` + `.clip(CircleShape)`. Square asset, Crop, circle — undistorted. | No change. |

## B.2 Conclusion

There is **no aspect-ratio bug on any Dialed-controlled code path** (b, c, d are all provably
non-distorting for a 450×450 square source; the install-animation screen shows a vector placeholder,
not a bitmap). By elimination, the "distorted dimensions while WearOS is setting the face" is
**almost certainly the platform's own activation / picker preview UI (candidate a), which renders
outside our Compose and which we cannot restyle.**

**✅ RESOLVED by the screenshot (received 2026-07-12) — candidate (a) confirmed.** The photo shows a
stretched oval thumbnail that is (i) *smaller* than any Dialed `FaceDial` (≈45×34 dp vs the 58–97 dp
call sites) and (ii) impossible for any wear composable to draw — every `FaceDial` is a rigid
`size(dp)` **square** box → `CircleShape` → `Crop`, which yields a circle from the 450×450 square
`homePreview`, never an oval. It is the **Wear OS system "applying watch face" preview** shown during
`setWatchFaceAsActive`, caught mid-transition while Dialed's "Connected" `HomeScreen` chrome was still
on screen. **Confidence: HIGH that it is not a Dialed FaceDial/extractor bug (geometric proof);
MODERATE that the exact surface is the system apply-preview** — a 10-second screen recording / `adb
logcat` at the set-active moment would make it 100%. Full write-up + the cheap 300-vs-450 A/B check:
**`R5-screenshot-evidence.md`.**

Item #5 is therefore **closed as a platform surface, not an app bug.** No Phase-2 `FaceDial` fix is
needed (the B.3 hardening below is optional belt-and-suspenders). The user-visible remedy moves to
**Phase 5**: where the activation strategy allows (`NO_ACTION_NEEDED` / `CALL_SET_ACTIVE_NO_USER_ACTION`
already show a circular Concierge), keep the user on Dialed's own screen so they never see the raw
system thumbnail; where the system UI is unavoidable (`FOLLOW_PROMPT_ON_WATCH` / gesture), set
expectation with copy.

## B.3 Optional zero-risk hardening (only if we want belt-and-suspenders before the screenshot)

- Force the extracted bitmap square regardless of source: `drawable.toBitmap(width = 450, height = 450)`
  in `FacePreviewExtractor.extract()`. No visible change today (assets already 450×450); guarantees a
  square bitmap even if a future face ships a non-square `preview`.
- Leave both `FaceDial`s exactly as-is — `ContentScale.Crop + CircleShape` is the correct,
  non-distorting recipe and must not be changed to any `Fill*` variant.

---

## Sources

- Watch Face Push guide — https://developer.android.com/training/wearables/watch-face-push
- Configure your Wear OS app for WFP — https://developer.android.com/training/wearables/watch-face-push/wear-os-app
- WatchFacePushManager API reference — https://developer.android.com/reference/com/google/wear/services/watchfaces/watchfacepush/WatchFacePushManager
- androidx.wear.watchfacepush release notes — https://developer.android.com/jetpack/androidx/releases/wear-watchfacepush
- androidify `WatchFaceOnboardingRepository.kt` — https://github.com/android/androidify/blob/main/wear/src/main/java/com/android/developers/androidify/watchfacepush/WatchFaceOnboardingRepository.kt
- What's new in Watch Faces (slot model, WFP) — https://android-developers.googleblog.com/2025/05/whats-new-in-watch-faces.html

Repo files referenced (all absolute):
- `D:\Apps\WearOS Apps\WatchFaces\Dialed App\wear-common\src\main\java\com\dialed\app\wear\common\WearConstants.kt`
- `D:\Apps\WearOS Apps\WatchFaces\Dialed App\wear\src\main\java\com\dialed\app\wear\wfp\WatchFacePushRepository.kt`
- `D:\Apps\WearOS Apps\WatchFaces\Dialed App\wear\src\main\java\com\dialed\app\wear\wfp\FacePreviewExtractor.kt`
- `D:\Apps\WearOS Apps\WatchFaces\Dialed App\wear\src\main\java\com\dialed\app\wear\ui\components\FaceDial.kt`
- `D:\Apps\WearOS Apps\WatchFaces\Dialed App\app\src\main\java\com\dialed\app\ui\components\FaceDial.kt`
- `D:\Apps\WearOS Apps\WatchFaces\Dialed App\app\src\main\java\com\dialed\app\catalog\Face.kt`
- `D:\Apps\WearOS Apps\WatchFaces\Dialed App\app\src\main\java\com\dialed\app\catalog\FaceCatalog.kt`
