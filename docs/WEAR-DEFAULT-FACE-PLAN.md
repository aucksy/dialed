# Plan — the bundled default face (the real "first install just works" fix)

> Owner steer 2026-07-18: "the first install never works properly." Diagnosis below, then the fix.
> This is the functional counterpart to the v0.27.0 wear **visual** fixes (`WEAR-FIDELITY-AUDIT.md`).

## The diagnosis (verified from the code, not memory)

The install itself works — the face lands on the watch. What fails is **auto-activation**:

- Before installing, the watch computes a `WatchFaceActivationStrategy` from three facts
  (`WatchFacePushRepository.activationStrategy`): do we already own the **active** face,
  is the one-shot `SET_PUSHED_WATCH_FACE_AS_ACTIVE` permission granted, can we still request it.
- On a **first** face, we do **not** own the active face (it's the OEM default), so the only lever is
  the platform's **unattended set-active allowance** — which is once-ever and, once spent or when the
  live face belongs to another app, throws `ERROR_MAXIMUM_ATTEMPTS`
  (`WatchFacePushRepository.setActive`). Google's own androidify hits the identical wall; there is no
  picker Intent an app can launch. When it's refused the flow correctly falls back to the manual
  "set it by hand" guide (`ConciergeScreen.GuidedHandoff`).
- Net: **install succeeds → auto-activate is refused → user is asked to long-press.** That reads as
  "the first install didn't work," even though the face is on the watch.

The v0.27.0 visual fixes make this legible (the "On your wrist." confirmation now proves the install
succeeded), but they do not remove the wall.

## The fix — ship a Dialed face that is the watch's default/active face

If a Dialed face is **already the active face**, then every future push is
`hasActiveWatchFace = true → NO_ACTION_NEEDED`: installing face B **replaces the slot and stays
active** with no set-active call, no permission, no long-press. The whole wall disappears **for the
second face onward**, and the first face becomes the only manual step — which we can make a one-time
guided setup.

**Options (pick with the owner):**

1. **Bundle one Dialed WFF face inside the wear APK as a normal, user-selectable watch face**
   (not via WFP — a plain `WatchFaceService`/WFF the user picks once from the system carousel).
   Once it's active, every WFP push swaps in place and stays active. **Lowest-risk, no platform
   fight.** Cost: build/package one face into `:wear` (the fleet already has 43 WFF faces to draw from);
   a one-time "make Dialed your face to finish setup" coaching step (we already have the copy).

2. **First-push guided activation, then frictionless forever** — keep pushing via WFP but, on the
   very first install, drive the user through the one long-press with the existing `GuidedHandoff`,
   and rely on ownership-chaining thereafter. No new face to build, but the first face still needs the
   manual step (status quo, just better-signposted).

3. **Hybrid (recommended):** ship option 1's bundled face **and** keep the guided hand-off as the
   fallback for users who haven't set the Dialed face yet. Best of both — a true "just works" path once
   set, honest guidance until then.

## Acceptance (needs the owner's watch — cannot be verified here)

- With a Dialed face active, push a second face → it becomes active with **no** prompt, **no**
  long-press (strategy `NO_ACTION_NEEDED` → "Dialed in." celebration).
- Fresh install, first face → one clearly-guided long-press (or the bundled-face setup), never a
  dead "Set as my face" tap.
- On-wrist logs (`DialedWfpRepo` / `DialedListener` tags) confirm the strategy taken and any
  `SetWatchFaceAsActiveException` code, so we learn whether the allowance ever resets.

## Constraints

- Building a bundled WFF face + packaging it into `:wear` touches the wear module but **not** the
  transport (listener/repo/`TransferSession`). Load `wff-watchface.skill` + polish skill (standing
  rule). Cloud-build only. Own release + on-wrist sign-off before calling it fixed.
