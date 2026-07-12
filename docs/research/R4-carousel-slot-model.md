# R4 — Carousel / Favorites Visibility + Watch Face Push Slot Model

**Owner issue #6:** "Pushed faces don't appear in the watch's long-press '+' favorites carousel."
**Scope gated:** Phase 6.
**Status:** Research complete. Findings are primary-source-backed except two clearly-marked `[UNVERIFIED]` items that require an on-watch screenshot / live device to settle.
**Date:** 2026-07-12

---

## TL;DR — the definitive answer

**Q: Does a Watch Face Push ("marketplace") face appear in the Wear OS 6 system watch-face picker / the long-press "+" favorites carousel?**

**A — split into the two things the owner conflated:**

1. **As the *active/current* face: YES.** `setWatchFaceAsActive(slotId)` makes the pushed face the current watch face on the wrist, so it is what you see when you look at the watch and it is the *centered/current* tile when you long-press. That is exactly and only what the API does.

2. **As an *additional, browsable, selectable favorite* in the long-press strip / the "+ Add watch faces" gallery: NOT AUTOMATICALLY, and there is NO API to force it.** No Watch Face Push (WFP) method adds a face to the favorites carousel. The *only* documented way to make a Dialed face **discoverable in the system watch-face gallery/picker** is the **"default watch face"** mechanism (a branded APK bundled at build time into the wear app: `assets/default_watchface.apk` + a manifest `<meta-data>` token). Dialed does **not** currently ship one — which is the direct, sufficient cause of issue #6.

3. **HARD PLATFORM CEILING:** on Wear OS 6 a marketplace app gets **exactly ONE slot**. You can therefore have **at most one Dialed face installed at any instant.** You **cannot** make all 18 (or even 2) Dialed faces appear as separate tiles in the picker. This is a documented platform limit, not a bug and not fixable by any API. Phase 6 must design around it, not "fix" it.

So the honest product answer to the owner: *your Dialed face is set active directly and is the current face; Wear OS gives marketplace apps a single slot, so only one Dialed face lives on the watch at a time and you switch faces from inside the Dialed app — not from the watch's "+" gallery. We can optionally seed one branded "Dialed" tile into that gallery via a default watch face.*

---

## 1. The slot model (primary sources)

### 1.1 Slot count on Wear OS 6 = **1** (confirmed, multiple primary sources)

> "Watch Face Push defines a concept of 'slots' — how many watch faces a given app can have installed at any time. **For Wear OS 6, this value is in fact 1.**"
> — Android Developers Blog, *Bringing Androidify to Wear OS with Watch Face Push*
> https://developer.android.com/blog/posts/bringing-androidify-to-wear-os-with-watch-face-push

> "The system sets a maximum number of slots that a marketplace can have; **with Wear OS 6, the limit is 1.**"
> — *Configure your Wear OS app for Watch Face Push*
> https://developer.android.com/training/wearables/watch-face-push/wear-os-app

### 1.2 What a "slot" is

> "Adds a new watch face to this device, creating a `WatchFaceSlot`. A watch face slot is an identifier used for managing pushed watch faces … There is a cap on the number of watch faces that a client is allowed to add and when this cap is reached, an `AddException` with a status of `ADD_SLOT_LIMIT_REACHED_ERROR` will be received…"
> — `WatchFacePushManager` API reference (`addWatchFace`)
> https://developer.android.com/reference/com/google/wear/services/watchfaces/watchfacepush/WatchFacePushManager

A slot is **not** a first-class, permanently-listed entry in the system picker. It is a private, app-owned install handle. With slot=1, the app owns a single installable "cell" it can add to / update / remove / activate.

> "Don't store `slotId` values or treat them as persistent. The state could change outside of your application; for example, a user might uninstall your watch face through the system UI. Always obtain the current state immediately before any slot-based operation."
> — API reference note (also on `listWatchFaces`/`addWatchFace`).

Dialed already honours this — `WatchFacePushRepository` queries `listWatchFaces()` fresh every op and never persists the slotId (`wear/.../wfp/WatchFacePushRepository.kt`).

### 1.3 The four slot-lifecycle methods (verbatim behavior)

| Method | Documented behavior (quoted / paraphrased from the API reference) |
|---|---|
| `addWatchFace(apk, token)` | "Adds a new watch face to this device, **creating a `WatchFaceSlot`**." Use only when `remainingSlotCount > 0`; else `ADD_SLOT_LIMIT_REACHED_ERROR`. |
| `updateWatchFace(slotId, apk, token)` | "Updates the watch face with the given slot ID." **Same package name → iterative update, user style settings preserved.** **Different package name → full replacement**, and "If the previous watch face was set as active … then the provided watch face will be set as active." |
| `removeWatchFace(slotId)` | "Removes a watch face slot … the underlying watch face is uninstalled." |
| `listWatchFaces()` | "Lists all the watch faces that were added by the app … paired with their associated slot ID, along with the **number of available slots remaining**." |

Because slot=1, "install a *different* Dialed face" is always the `updateWatchFace` **full-replacement** path (different package name per face — Dialed uses `com.<series>.<face>` package ids), which conveniently **carries the active state forward**: replacing the active face makes the replacement active without a second `setWatchFaceAsActive` call. This matches Androidify's canonical logic and Dialed's existing `installOrUpdate`:

```kotlin
if (response.remainingSlotCount > 0) wfp.addWatchFace(apkFd, token)
else wfp.updateWatchFace(response.installedWatchFaceDetails.first().slotId, apkFd, token)
```

---

## 2. `setWatchFaceAsActive` — switches the current face, does NOT register a favorite

> "Sets the watch face with the given `slotId` as the active watch face. The watch face needs to be previously added using the `addWatchFace(...)` method."
> — API reference, `setWatchFaceAsActive`

The reference says **nothing** about the picker, favorites, or a gallery entry. It sets the **active (current)** face — full stop. Two critical constraints govern it:

**(a) It is effectively ONE-TIME.**
> "The active watch face can be set by this means **only once**. Should the user move to a watch face from another developer, calling this API to set the active watch face back to your watch face **throws an exception**. Once this means has been used, your phone app should instead offer guidance on **how to manually set the active watch face**."
> — *Configure your Wear OS app for Watch Face Push*

**(b) Its permission is one-rejection-terminal.**
> `com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE` … "This permission has a maximum rejection count of 1: If the user denies the request, then the request cannot be made again."
> — same page.

Dialed already models both facts (`WatchFaceActivationStrategy`, `WfpStateStore.setActiveApiUsed` / `permissionDenied`). The clause *"offer guidance on how to manually set the active watch face"* is itself primary-source proof that **the installed slot face is reachable/selectable in the watch's own UI** (otherwise "manually set it" would be impossible) — see §3.

**There is no `addToFavorites` / `pinToCarousel` / `setSelectable` API.** Reviewed the full `WatchFacePushManager` surface: `addWatchFace`, `updateWatchFace`, `removeWatchFace`, `listWatchFaces`, `setWatchFaceAsActive`, `isWatchFaceActive`. None touches the favorites strip.

---

## 3. The ONLY documented picker/gallery path: the **default watch face**

This is the load-bearing section for Phase 6.

> **"Supply a default watch face** — Watch Face Push offers the ability to install a default watch face when your marketplace app is installed. **This doesn't, by itself, set that default watch face as active … but makes your watch face available in the system watch face picker.**"
> — *Configure your Wear OS app for Watch Face Push*

> "A default watch face is a great way to help your users **discover and use your marketplace**: The watch face is installed when your marketplace is, **so users can find it in the watch face gallery.** … Don't use `removeWatchFace` if the user chooses to uninstall a watch face from your marketplace app. Instead … **revert the watch face back to the default watch face using `updateWatchFace`. This helps users locate your watch face and set it from the gallery.**"
> — same page, "Representative default watch face."

**Mechanism (verbatim):**
1. Bundle the default face APK at path `assets/default_watchface.apk` in the **wear** app build.
2. Add to the wear `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN"
    android:value="@string/default_wf_token" />
```
> "A typical build pattern includes building your `default_watchface.apk` as part of your overall app build as a pre-build step. You can … populate the validation token manifest value by having the default watch face build process output an additional Android resources XML file and include that resources path as an additional resources path within your main app."

**What this buys you:** exactly ONE branded "Dialed" tile that is present in the system watch-face **gallery/picker** from the moment the app is installed — even before any push — and that the user can select manually. The docs frame it as the discovery + fallback surface, and the "revert to default instead of `removeWatchFace`" guidance keeps a Dialed tile in the gallery permanently.

**What it does NOT buy you:** it does not add each pushed face as its own gallery tile, does not lift the 1-slot ceiling, and (per the quote) does not set anything active.

`[UNVERIFIED]` Whether the bundled default face **consumes the single slot** (i.e. `remainingSlotCount == 0` on a fresh device once a default is shipped, forcing every user push down the `updateWatchFace` full-replace path). The docs don't state it. **Best-evidence read: YES it consumes the slot** — Androidify's `remainingSlotCount > 0 ? add : update` pattern only makes sense if a pre-seeded default occupies the slot, and slot=1 leaves no room for a separate default + a pushed face. Treat "one Dialed tile at a time" as the working assumption; confirm on-device in the Phase 6 build.

---

## 4. Reconciling the owner's observation

- Dialed today ships **no** default watch face (no `assets/default_watchface.apk`, no `DEFAULT_WATCHFACE_VALIDATION_TOKEN` in `wear-common`/wear manifest — confirmed by grep; `WearConstants.kt` defines only `PUSH_WATCH_FACES` + `SET_PUSHED_WATCH_FACE_AS_ACTIVE`). Therefore nothing seeds the gallery, and there is no browsable "Dialed" entry in the "+" picker. **This alone fully explains issue #6.**
- When a push succeeds and `setWatchFaceAsActive` runs, the face **is** the current face (shows on-wrist, is the centered tile on long-press). If the owner's screenshot shows the pushed face on the wrist but absent from the *"+ Add watch faces"* browse list, that is expected today and is fixed (as far as the platform allows) by §3.
- Secondary corroboration (not primary, treat as journalistic): *Android Central* — "Wear OS 6 gives approved 3rd-party watch face apps **one 'slot' in your smartwatch's favorites menu**, visible by tapping and holding … Pick a watch face in the mobile app, and it pushes onto that slot." (https://www.androidcentral.com/apps-software/wear-os/finding-the-best-custom-watch-faces-wear-os-6-facer-watchmaker). Consistent with §1–§3: one slot, surfaced via long-press when active.

`[UNVERIFIED — screenshot-gated]` The precise rendering difference on a **Pixel Watch 4** between (a) the long-press **favorites strip** and (b) the fuller **"+ Add watch faces" gallery**, and whether a default-seeded Dialed tile lands in one, the other, or both. Primary docs say "system watch face picker" / "gallery" without disambiguating the two Pixel surfaces, and this is launcher/OEM-dependent. The owner's promised screenshot of the exact screen he's looking at is required to close this. The documented, reliable surface is the **gallery** reached via "+ Add watch faces"; do not promise the quick favorites strip.

---

## 5. Definitive conclusion → Phase 6 scope

**Can pushed Dialed faces be made to appear as browsable tiles in the watch's "+" carousel?**
**Partially, and only within a hard 1-slot ceiling. There is no API to inject arbitrary faces into the favorites carousel.**

Phase 6 should therefore ship **both** of the following (they are complementary, not either/or):

- **A) Platform-limit-honest UX copy (must-do, cheap, unblocks the complaint immediately).** In the Dialed phone + wear apps, state plainly: *"Your Dialed face is applied directly and becomes your active watch face. Wear OS lets marketplace apps keep one face on the watch at a time, so switching faces here replaces the current Dialed face — you won't see every Dialed face in the watch's own '+' gallery."* This converts a perceived bug into understood behavior.
- **B) Ship a branded default watch face (the one lever that genuinely puts "Dialed" into the system gallery).** Bundle `assets/default_watchface.apk` + the `DEFAULT_WATCHFACE_VALIDATION_TOKEN` meta-data, and switch the "user removed the face" path from `removeWatchFace` to `updateWatchFace(slot, defaultApk)` so a recognizable Dialed tile is always locatable in the gallery. Accept that only ONE Dialed tile can exist at a time.

**What Phase 6 must NOT promise:** all 18 faces as separate picker tiles; adding a pushed face to the quick favorites strip via any API; re-activating your face after the user switched to another developer's face (the one-time rule).

---

## RECIPE (execution-ready for Phase 6 — no re-research required)

**Goal:** eliminate the "not in the '+' carousel" surprise and put a persistent, discoverable "Dialed" tile in the system watch-face gallery, within the 1-slot limit.

1. **Build a branded default face APK** (a simple, instantly-recognizable "Dialed" WFF face — logo + theming; per docs "make it simple and instantly recognizable"). It is a normal WFF watch face APK with its own package id (e.g. `com.dialed.defaultface`).
2. **Validate it** with the WFP validator (same CLI/lib path used for the 18 faces) to produce its **validation token**. Emit that token as an Android resource `@string/default_wf_token` (write an XML resources file in the default-face pre-build step and add it as an extra resources path to the wear app — do NOT hand-edit the manifest at build time; follow the doc's "additional resources XML" pattern).
3. **Bundle** the APK at exactly `wear/src/main/assets/default_watchface.apk` (pre-build copy step in the wear module's Gradle).
4. **Add the meta-data** to the **wear** `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN"
       android:value="@string/default_wf_token" />
   ```
5. **Change uninstall/remove semantics in `WatchFacePushRepository`:** when the user "removes" the current Dialed face in-app, call `updateWatchFace(slotId, defaultApk, defaultToken)` (revert to default) **instead of** `removeWatchFace(slotId)`, so the slot always holds a locatable Dialed tile. (Add a `revertToDefault()` method mirroring `installOrUpdate`.)
6. **Verify on-device (Pixel Watch 4, WO6):** after install, confirm the Dialed default tile appears in the "+ Add watch faces" gallery; after a push, confirm the pushed face becomes active/current; confirm `remainingSlotCount` behavior (settles the §3 `[UNVERIFIED]` slot-consumption question). Capture screenshots of both the favorites strip and the "+" gallery to close the §4 `[UNVERIFIED]`.
7. **Add the honest UX copy** (§5-A) to the phone ConciergeScreen success state and the wear HomeScreen: one face at a time, applied directly, switch from the app.
8. **Do NOT** attempt to enumerate all faces into the picker, add to the favorites strip, or re-arm `setWatchFaceAsActive` after a foreign face — all three are platform-blocked.

**Wire-protocol note (ties to R5):** no new Data Layer path is needed for the default face (it is bundled in the wear APK, not pushed). A `revertToDefault` is a local wear-side op. If Phase 1's `PATH_UNINSTALL` (per grounding facts) is added, its handler should call the new `revertToDefault()` rather than `removeWatchFace` to preserve gallery discoverability.

---

## Sources (all primary unless noted)

- Configure your Wear OS app for Watch Face Push (slot=1, `setWatchFaceAsActive` one-time, permission rejection rule, **default watch face** section incl. asset path + meta-data): https://developer.android.com/training/wearables/watch-face-push/wear-os-app
- `WatchFacePushManager` API reference (per-method behavior, `ADD_SLOT_LIMIT_REACHED_ERROR`, slot non-persistence): https://developer.android.com/reference/com/google/wear/services/watchfaces/watchfacepush/WatchFacePushManager
- Watch Face Push overview: https://developer.android.com/training/wearables/watch-face-push
- Android Developers Blog — Bringing Androidify to Wear OS with Watch Face Push (slot value = 1; add-vs-update strategy; `setWatchFaceAsActive` one-time): https://developer.android.com/blog/posts/bringing-androidify-to-wear-os-with-watch-face-push
- What's new in Watch Faces (WFP intro, active-face permission): https://android-developers.googleblog.com/2025/05/whats-new-in-watch-faces.html
- wear-os-samples WatchFacePush sample (canonical add/update/setActive usage): https://github.com/android/wear-os-samples/tree/main/WatchFacePush
- [Secondary/journalistic] Android Central — custom faces on WO6 (one slot via long-press favorites menu): https://www.androidcentral.com/apps-software/wear-os/finding-the-best-custom-watch-faces-wear-os-6-facer-watchmaker
- Dialed repo cross-refs: `wear/src/main/java/com/dialed/app/wear/wfp/WatchFacePushRepository.kt`, `wear-common/src/main/java/com/dialed/app/wear/common/WearConstants.kt` (confirmed: no default-face token/asset shipped today).
