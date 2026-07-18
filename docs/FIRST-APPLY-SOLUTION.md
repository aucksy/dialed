# Solving the first-apply experience — researched solution (2026-07-18)

> Owner: "solve the broken UX when a user applies a face for the first time. If we can't auto-apply,
> walk them through it. Also the first-install screen still shows the old small-preview-pushed-up
> layout — replace it with the new experience." This is the focused spec for a fresh chat.
>
> **Researched against Google's official Watch Face Push docs + the androidify sample (not memory).**
> Sources at the bottom.

---

## 1. Ground truth (verified online, July 2026)

- `setWatchFaceAsActive(slotId)` can be used **once**, and **only while the currently-active face is
  NOT already ours**. Once used — or if the user later switches to another developer's face —
  calling it again **throws** `SetWatchFaceAsActiveException`. This is a hard platform anti-hijack
  rule. (developer.android.com/training/wearables/watch-face-push/wear-os-app)
- The `SET_PUSHED_WATCH_FACE_AS_ACTIVE` runtime permission has a **max rejection count of 1** — deny
  once and it can never be re-requested; you must send the user to Settings.
- **There is NO public Intent / deep link to open the Wear OS watch-face picker.** Confirmed in the
  docs and the samples — an app cannot launch the picker. So when auto-apply is unavailable, a
  **manual walkthrough is the only path** (and we must make it excellent).
- ⭐ **Google's documented solution = the "default watch face" ownership chain** (this is the fix):
  1. **Bundle a watch face in the app** (WFP supports an APK in the app's assets, validated at
     install) and get our marketplace to **own the active slot**.
  2. Once we own the active face, **`updateWatchFace()` replaces the slot content and keeps it
     active** — no `setWatchFaceAsActive`, no permission, no user action. Every future push
     **auto-applies**.
  3. **On uninstall, do NOT `removeWatchFace()`** (that surrenders the slot and breaks the chain) —
     **`updateWatchFace()` back to the bundled default face instead.** Google says this verbatim.
  4. **Make the default face instantly recognizable (logo/theming)** so if the chain ever breaks the
     user can find "Dialed" in the system gallery, and add a `<Launch>` element on it to open the
     phone app.

**Conclusion:** the first apply CAN be made to "just work" for every face after ownership is
established — we were solving the wrong half. The one-shot is not the product; **owning the slot is.**

---

## 2. The fix (two parts)

### Part A — establish & keep ownership (the functional fix; makes auto-apply real)

1. **Bundle a Dialed default WFF face** (APK + token) in the `:wear` assets. Use a clean,
   Dialed-branded face from the fleet (recognizable in the gallery). Build it with the WFF skills
   (`wff-watchface.skill` + polish), validator PASS.
2. **Establish ownership at onboarding** — after the push permission is granted, show one warm
   "**Make Dialed your watch face**" screen (big face, one action). On confirm: install the bundled
   default via the existing `installOrUpdate`, then spend the one-shot `setWatchFaceAsActive`
   **here, in context** (best possible moment). Now Dialed owns the active slot.
   - If the user pushes a real face before onboarding finishes, that first push establishes ownership
     the same way (one-shot on the first install) — either path is fine; ownership just needs to be
     claimed once.
3. **Every subsequent push → `updateWatchFace` (already how `installOrUpdate` behaves when the slot
   is full) → stays active automatically.** The concierge for these becomes the **celebration only**
   (`NO_ACTION_NEEDED` → "Dialed in.") — no "Set as my face" tap, no long-press. ✅
4. **Change uninstall to revert-to-default:** `WatchFacePushRepository.removeByPackage` →
   `updateWatchFace(slotId, defaultFaceApk, token)` instead of `removeWatchFace`. Preserves ownership
   so the NEXT push still auto-applies. (This touches the repo — the one deliberate transport change,
   per Google's documented pattern; keep the Data-Layer/listener/channel code untouched.)

### Part B — the manual walkthrough, done right (for when ownership genuinely can't be claimed)

Only reachable if the one-shot was already spent AND a foreign face is active (rare once Part A
ships). Redesign it from the current cramped step-list into the **big, unmistakable experience** the
owner is asking for:

- **Big centered face** (≥ the spec's 1k 80dp / celebration 97dp), not a 50dp preview pushed to the
  top of a scroll.
- The spec's **W3 coaching** done properly: an animated hold → carousel → tap demo (or a bold
  3-step visual), the face named, one "Take me there" action that drops onto the watch face.
- Because a Dialed default face exists and is recognizable, the copy can say "find **Dialed** and tap
  it" and the user actually can.

---

## 3. Replace the "old small-preview-pushed-up" first-install screen

The owner is seeing the concierge **`OneTapApply`** (80dp face) / **`GuidedHandoff`** (50dp,
scroll-column, pushed up) — `wear/.../ui/screens/ConciergeScreen.kt`. These predate the fidelity
pass and are the cramped screens. Redesign both to the boutique, big-face experience (spec §D 1k/1l/1m,
true sizes), matching the new phone/wear polish. This is the visible half of the owner's ask.

---

## 4. Scope for the fresh chat

1. Build the bundled default Dialed WFF face → `:wear` assets (APK + token), validator PASS. (WFF skills.)
2. Onboarding "Make Dialed your watch face" step → install default + spend the one-shot.
3. Uninstall → revert-to-default (`updateWatchFace`), not `removeWatchFace`.
4. Redesign `ConciergeScreen` (OneTapApply + GuidedHandoff) to the big new experience.
5. Keep the Data-Layer transport (listener/channel/`TransferSession`) untouched; the only repo change
   is remove→revert. Adversarial review; **owner on-wrist test is mandatory** (no watch here).
6. Ship as the next free tag; paste APK links + a precise on-wrist checklist.

**Acceptance (owner verifies on-wrist):** after onboarding, pushing ANY face makes it the live face
with **no tap and no long-press**; uninstalling reverts to the Dialed default (still owned); only a
deliberately-foreign-face edge shows the (now big) manual walkthrough.

---

## Sources
- Configure your Wear OS app for Watch Face Push — https://developer.android.com/training/wearables/watch-face-push/wear-os-app
- WatchFacePushManager API — https://developer.android.com/reference/com/google/wear/services/watchfaces/watchfacepush/WatchFacePushManager
- Watch Face Push overview — https://developer.android.com/training/wearables/watch-face-push
- androidify WatchFaceOnboardingRepository — https://github.com/android/androidify/blob/main/wear/src/main/java/com/android/developers/androidify/watchfacepush/WatchFaceOnboardingRepository.kt
