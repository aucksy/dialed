# Dialed — Onboarding Redesign (research → audit → spec)

**Date:** 2026-07-22 · **Status: APPROVED (owner, all 4 items) → IMPLEMENTED same day (Slices 1+2
in one pass; on disk, UNSHIPPED — no commit/tag until owner approves the ship).** Slice 3 (remote
install on the watch via Play) stays gated on Phase 4 as designed; its seam
(`WatchInstallGuideSheet` in `app/.../ui/screens/SetupScreen.kt`) ships as the manual 3-step guide.
Starter faces chosen: Arclight Solstice · Vakt GT · Aurum Guilloché (revisit at the free-face map).
⚠ Wear-side changes REQUIRE on-wrist verification (merged permission chain, NEEDS_SETUP round trip,
pending-face ask, celebration→exit).
**Method:** research-first (platform docs + current UX evidence), then a full audit of the shipped
v0.28.0 onboarding (every claim carries `file:line` evidence), then the redesign. The interactive
walkthrough the owner reviews is `docs/design-demos/onboarding-redesign.html` (open via Chrome).

Companions: `docs/ASSESSMENT.md` (C1–L6), `docs/FIRST-APPLY-SOLUTION.md` (the activation wall),
`docs/WEAR-FIDELITY-AUDIT.md`, `Dialed Watch-Face Store-handoff/.../HANDOFF.md` §1a–1c (the spec
this replaces the onboarding section of).

---

## 0. Executive summary (plain English)

Today, a new user opens Dialed and reads **three pages of marketing**, none of which does anything.
Then they're dropped on the store with no guidance. If their watch doesn't have the Dialed watch app
yet, the phone just says **"No watch connected" — which is wrong and a dead end**. On the watch,
they get **two separate permission/setup screens** back-to-back.

The redesign: **one adaptive setup screen on the phone** that *actually connects the watch* (detects
it live, installs the watch app on it when we're on Play, walks around every failure), **one
setup moment on the watch** instead of two, a **"start with this face" nudge** so the first success
happens in the first minute, and honest paths for every watch state (no watch / old watch / watch
app missing). Premium stays out of onboarding entirely — free faces are the trial; the paywall only
ever appears after a face is already on the wrist (the research says this roughly doubles trial
uptake vs selling upfront).

Nothing in this proposal touches the transfer engine, the WFP install path, or the activation
machinery — those are hardened and stay byte-identical except one **appended** wire response code.

---

## 1. Research findings

### 1.1 Platform (primary sources, July 2026)

| Fact | Source | Consequence for Dialed |
|---|---|---|
| **Watch Face Push is unchanged**: WO6+/API 36 only; `setWatchFaceAsActive` is once-ever; `SET_PUSHED_WATCH_FACE_AS_ACTIVE` **cannot be re-requested after denial**; no watch-face-picker Intent exists. Library still `watchfacepush:1.0.0`. | developer.android.com WFP docs (updated 2026-05); Androidify blog (2025-12); androidx release notes | The v0.28.0 architecture (bundled default face + ownership chain) is still the right — and only — fix for first-apply. The redesign builds *around* it, not against it. The set-active ask must carry maximum context because a denial is permanent. |
| **Google's own prescribed marketplace UX** for activation: track set-active history on the watch, show an **education screen** (long-press coaching) once the one-shot is spent, request the permission with guidance before first use. | WFP phone-app config doc | The shipped `ConciergeScreen`/`GuidedHandoff` already matches Google's prescription. Keep them; do not redesign the post-push watch flow. |
| Google's Aug-2025 WFP exploration recommends a **bundled default watch face** and **user-driven activation** ("user awareness and control over automatic background installation"). | android-developers blog 2025-08 | Validates keeping the default-face install behind an explicit user tap (as shipped) rather than the silent `DEFAULT_WATCHFACE` app-install metadata. |
| **The standard companion-onboarding pattern is Horologist `DataLayerAppHelper`**: `connectedNodes()` reports *per node* whether your app is installed (`appInstallationStatus`); `installOnNode(nodeId)` opens the Play Store **on the watch**; `markSetupComplete()` + `USAGE_STATUS_SETUP_COMPLETE` let the phone track watch-side setup; there is a prebuilt `InstallAppPrompt`. | google.github.io/horologist | The "observed flow" in the brief (detect missing watch app → one-tap install on watch → monitor → continue) is **current, Google-sanctioned practice**, not a legacy pattern. Dialed has none of it. Note: `installOnNode` needs the app to exist on Play — the wiring lands with Phase 4; the *detection and guidance* can land now with the clients Dialed already uses. |
| **Material 3 Expressive** is the current design system on both form factors (46 studies / 18k participants; springs with overshoot for hero moments; edge-hugging buttons and larger scroll affordances on Wear). | android-developers blog 2025-05/2025-08, design.google | Dialed's design system (spring motion tokens, EdgeButtons, `ScreenScaffold`) is already M3E-aligned. The onboarding should *use* the expressive motion the app already has, not invent new vocabulary. |
| Wear OS 7 shipped (WFF v5); **no WFP behaviour changes** surfaced for activation or slots. | releases + What's-new posts | No new escape hatch; design for the WO6 rules. |

### 1.2 UX evidence

- **Permissions:** ask **in context**, benefit-first, and keep onboarding slim; permission dialogs
  that state benefit + scope see ~20 % higher acceptance; degrade gracefully on denial
  (developer.android.com permission best-practices). One upfront ask is defensible only when the
  permission *is* the app's entire function — which `PUSH_WATCH_FACES` is, on the watch side.
- **Paywall timing:** paywalls shown **after a measurable value event** convert ~2.1× better than
  upfront hard paywalls (65 % vs 31 % trial-start in Adapty's 2026 benchmark); yet ~80 % of
  subscriptions happen within minutes of install — so the value event must arrive *fast*, not be
  pushed to day 2. For Dialed the value event is unambiguous: **a face the user chose is on their
  wrist.** The owner's already-locked model (free faces per collection, no Home paywall — plan D4)
  is exactly what the evidence prescribes. Onboarding therefore contains **zero selling**.
- **FTUE psychology:** the strongest patterns are *do, don't tell* (first-run should perform the
  setup, not describe it), *progressive disclosure* (one decision per screen), *endowed progress*
  (show the setup as nearly done when the system state allows it), and a *single activation metric*
  (here: **time to first face on wrist**). Feature-tour paging is the weakest pattern in every
  study — it is read by almost nobody and skipped by everybody.
- **Trust:** Dialed's phone app requests **no runtime permissions, no account, no network** (faces
  are bundled). That is a trust asset worth *saying once* at the setup moment — one line, not a
  dedicated page.

### 1.3 Challenging the observed flow (from the brief)

The observed 8-step flow (detect watch app → guided install → monitor → provider setup → default
face → personalization) survives validation with two corrections for Dialed's reality:

1. **"Guides the user through making the watch app available as a watch face provider"** — for a
   WFP marketplace this step *is* the watch-side permission + default-face moment; there is no
   separate "provider" registration. Collapse it.
2. **"A default watch face is automatically applied"** — *fully automatic* apply is impossible on
   WO6 (once-ever budget, permission gate). The honest version is **one explicit tap on the watch**
   that installs + activates the default in a single stroke. Anything that promises more lies.

---

## 2. Audit of the current onboarding (v0.28.0) — be brutal

### Phone

| # | Finding | Evidence | Verdict |
|---|---|---|---|
| **P1** | **The 3-page pager is a feature tour that does nothing.** No detection, no connection, no action — three screens of reading before the store. Page 2 says "Connect your watch" but connects nothing. | `OnboardingPager.kt:42-46` | **Replace** with one adaptive setup screen. |
| **P2** | **Page 3 explains a permission the phone never asks for.** The phone app has zero runtime permissions; the page describes the *watch's* WFP permission — wrong device, wrong time, and it plants an expectation of a scary dialog that never comes on this screen. | `OnboardingPager.kt:45` | **Delete.** One trust line on the setup screen replaces it. |
| **P3** | **Stale/false premium copy:** "Yours in one purchase" contradicts the owner-locked model (per-collection unlocks + free faces, D1/D4) and sells before any value. | `OnboardingPager.kt:43` | **Delete.** No selling in onboarding. |
| **P4** | **"Watch paired but Dialed watch app missing" is invisible.** Detection is capability-only, so a paired watch without the wear app reads **"No watch connected"** — factually wrong — and there is **no path to install the watch app** from the phone at all. This is the single biggest gap vs. the standard pattern (Horologist detects it and offers one-tap install). | `WatchConnection.kt:109-134` (capability only), `WatchStatusPill.kt:46-48` | **Fix now** (detection + guidance); Play-powered one-tap install lands with Phase 4. |
| **P5** | **"No watch connected" is a dead end** even when true — no next step, no help, no "browse anyway" framing. | `WatchStatusPill.kt:46-48`, `PushToWatchSheet.kt:184-201` | Give it a remediation path + keep the store browsable. |
| **P6** | **Unsupported watch is discovered at the worst moment.** The phone learns Wear OS < 6 only after a push fails (`RESPONSE_UNSUPPORTED` at push time), even though `queryInstalledState()` already reports `supported` on connect — the data existed; the onboarding never looked. | `MainViewModel.kt:177-186` vs `OnboardingPager` (static) | Surface it during setup, gently. |
| **P7** | **No first-face guidance.** After onboarding the user lands on a wall of 10 collection cards with no "start here", no recommended face, no sense of what to do first. The activation moment is left to chance. | `HomeScreen.kt:67-116` | Add a first-face hero row until the first install succeeds. |
| **P8** | Returning/reinstalling users replay the same marketing pager; the pager ignores watch state entirely (a fully-set-up watch still gets "Connect your watch"). | `DialedApp.kt:80-85` (flag only) | The adaptive screen self-fast-paths in <5 s. |
| **P9** | Push-success copy ("Finish setting it on your watch") doesn't say *what the user will see* on the watch — the two devices don't feel like one product at the exact moment they must. | `PushToWatchSheet.kt:128-136` | One added sentence mirrors the watch's words. |

### Watch

| # | Finding | Evidence | Verdict |
|---|---|---|---|
| **W1** | **Two setup screens where one moment suffices.** FirstRun (permission rationale) is immediately followed by MakeDefault (default-face step) — two full-screen decisions back-to-back on a 1.2-inch display. | `WearApp.kt:59-70` | **Merge into one "Make Dialed your watch face" moment.** |
| **W2** | **The once-ever-grantable set-active permission is requested with zero context.** It fires as a *second stacked system dialog* immediately after the push grant, before the user has seen any benefit — and a denial here is **permanent by platform rule**. Highest-stakes dialog in the product, weakest possible framing. | `MainActivity.kt:21-29` (chained launch) | Request it *inside* the make-default tap, where the benefit is on screen. |
| **W3** | **A face pushed before watch-side setup fails with a lie.** The listener answers BUSY when setup fails without permission, so the phone says "Your watch is busy — try again" — retrying can never help; the real fix (open the watch app, tap Allow) is never spoken. In the Play flow (install watch app → push immediately) this is the *common* path, not an edge. | `DialedListenerService.kt:127-135`, `WatchConnection.kt:251-254` | **Append `RESPONSE_NEEDS_SETUP`** + an honest phone state + a context-rich watch ask. |
| **W4** | The setup screens are competent M3E (ScreenScaffold, EdgeButton, no clipping) — the *structure* is the problem, not the craft. FirstRun's copy could carry benefit+scope more sharply. | `FirstRunScreen.kt`, `MakeDefaultScreen.kt` | Keep components; rewrite the moment. |
| **W5** | Post-push flow (Receive → "On your wrist." → Concierge/GuidedHandoff → celebration → exit-to-face) **matches Google's prescribed education pattern and needs no redesign.** | `ReceiveScreen.kt`, `ConciergeScreen.kt` | **Do not touch.** |

### What is deliberately absent (and should stay absent)

No account/sign-in (nothing to sync), no notification permission (nothing notifies), no battery
education (no phone background work), no analytics consent wall (no analytics). Every one of these
is an onboarding screen competitors show and Dialed structurally doesn't need. **Absence is the
premium statement.**

---

## 3. Prioritized improvements

| Pri | Item | Fixes | Why first |
|---|---|---|---|
| **P0-1** | Adaptive **Setup screen** replacing the 3-page pager (live watch detection, per-state guidance, browse-anyway escape) | P1 P2 P3 P5 P6 P8 | Deletes the weakest surface; the first 30 seconds become *doing*, not reading. No parked decisions touched. |
| **P0-2** | **Paired-without-app detection** (`NodeClient` vs `CapabilityClient` diff) + honest pill/setup states | P4 | Turns the most common Play-flow state from a false dead end into a guided step. Uses clients already in the codebase. |
| **P0-3** | **One-moment watch setup** (merge FirstRun + MakeDefault; both permissions requested inside the make-default tap) | W1 W2 | Halves watch decisions; puts the permanent-stakes dialog in its only defensible context. |
| **P0-4** | **`RESPONSE_NEEDS_SETUP` wire code** + phone "Finish setup on your watch" sheet state + watch pending-face ask | W3 | Kills the "busy" lie; makes push-before-setup (the Play-flow norm) self-healing. Ordinal-appended, wire-compatible. |
| **P0-5** | **First-face hero row** on Home until the first successful install | P7 | Direct lever on the activation metric. |
| **P1-1** | One-tap **install watch app on the watch** (`installOnNode`-style remote Play intent) behind the Setup screen's CTA | P4 (full) | Needs Dialed on Play (Phase 4). The Setup screen ships with guidance copy now; the button lights up when the store listing exists. |
| **P1-2** | Push-success sheet mirrors the watch's next-step words; receive screen shows the incoming face thumbnail (assessment M9, plan Phase 8) | P9 | Cross-device continuity at the money moment. |
| **P2** | F6 logo splash sweep; celebration polish; wear Home face-size bump (73→~88 dp, plan Phase 8); Instrument Sans wiring (M8) | — | Perceived-quality layer; sequenced with existing plan phases. |

---

## 4. The ideal end-to-end flow

```
PHONE first launch                                WATCH
─────────────────────                             ─────────────────────
Splash (system, themed icon)
   │
   ▼
SETUP (one screen, live states)
   ├─ READY (app on watch) ──────────────► "Choose your first face" → HOME + hero row
   ├─ WATCH_APP_MISSING ─ [Set up my watch]─► Play opens ON the watch (P1-1; until
   │        │ live monitor: capability         then: guided copy) → user installs
   │        ▼ appears → auto-advance ✓        │
   │     "Now open Dialed on your watch"  ◄───┘
   │              │                            User opens watch app
   │              │                               │
   │              │                               ▼
   │              │                        MAKE DIALED YOUR WATCH FACE (one screen)
   │              │                        [Set up Dialed] → push-perm dialog
   │              │                            → set-active-perm dialog
   │              │                            → default face installs + activates
   │              ▼                            → "Dialed in." → exits to the face
   │        READY reached ────────────────►  (watch now owns the slot: every later
   │                                           push just appears)
   ├─ NO_WATCH ── "Pair a watch first" guidance + [Browse faces anyway]
   └─ UNSUPPORTED ── honest Wear OS 6 note + [Browse faces anyway]

HOME (hero row: "Put your first face on")
   → Detail → [Install to watch] → PushSheet
       ├─ watch not set up → NEEDS_SETUP state: "Open Dialed on your watch and
       │      tap Set up, then push again"     ────►  watch opened → setup moment
       │                                              shows THE PENDING FACE as hero
       └─ success → Done ("It's live" / "One step on your watch")
                                                   WATCH: Receive → On your wrist. →
                                                   auto-applied (owned slot) → exit
```

Returning user / reinstall: Setup screen detects READY (or an installed face) in ≤2 s and shows a
single "You're set" beat with **Continue** — no tour, no re-education. Wear side already resolves
its one-time step silently when a face exists (`WearViewModel.kt:217-219`).

**The activation metric every screen serves: minutes from install → first chosen face live on the
wrist.** Each screen below states its measurable purpose; a screen with no metric was deleted.

---

## 5. Screen-by-screen specification

Design language: existing tokens (`Color.kt`/`Motion.kt`/`Type.kt`, gold `primary`, spring motion),
M3 Expressive. No new component vocabulary. All sizes in dp at default font scale; every text uses
the existing type roles so Dynamic Type scaling is free.

### 5.1 PHONE · Setup (replaces OnboardingPager) — the only pre-Home screen

**Purpose / metric:** get the watch to READY or the user knowingly past it. Measure: % reaching
READY on first launch; median time on screen (target < 40 s in the app-missing path, < 6 s READY).

**Wireframe (portrait, one screen, no paging):**

```
┌──────────────────────────────┐
│                    [Skip ▸]  │   ← quiet text, top-right; label "Browse faces first"
│                              │
│        Dialed.               │   ← wordmark, headlineLarge
│   Real faces for your watch. │   ← one line, bodyLarge, onSurfaceVariant
│                              │
│      ◐  ◓  ◑                │   ← overlapping trio of REAL FaceDials (96/132/96),
│                              │     the same vitrine language as Home cards
│  ┌────────────────────────┐  │
│  │  ● Watch status card   │  │   ← THE live element; state-driven (below)
│  │  line 1: state          │  │
│  │  line 2: what happens   │  │
│  └────────────────────────┘  │
│                              │
│  [        Primary CTA      ] │   ← 52dp DialedButton; label is state-driven
│   Nothing leaves your phone. │   ← labelMedium trust line, one line, always
└──────────────────────────────┘
```

**States of the status card + CTA (live, auto-advancing):**

| State | Detection | Card copy | CTA |
|---|---|---|---|
| CHECKING | first 2 s / probes running | "Looking for your watch…" (+ subtle indeterminate dot pulse) | disabled ghost "One moment…" |
| READY | Dialed capability reachable & `supported` | "**{Pixel Watch 4} is ready.**" / "Faces you pick appear right on it." | **"Choose your first face"** → Home |
| WATCH_APP_MISSING | ≥1 connected node via `NodeClient`, no Dialed capability | "**{watch name} needs the Dialed watch app.**" / "It takes one tap and about a minute." | **"Set up my watch"** → P1-1 remote install; pre-Play: opens an inline how-to (3 short steps) |
| INSTALL_WAIT | after CTA in previous state | "Installing on your watch…" / "This screen will move on by itself." | quiet "Having trouble?" |
| OPEN_ON_WATCH | capability appeared, `queryInstalledState()==null` or watch reports no permission | "**Almost there — open Dialed on your watch** and tap *Set up Dialed*." | "I've done it" (also auto-advances on state) |
| NO_WATCH | no connected nodes | "No watch is paired with this phone yet." / "Pair one in the Watch/Galaxy Wearable app, then come back — Dialed will spot it." | "Browse faces anyway" → Home |
| UNSUPPORTED | node + capability, watch answers `supported=false` | "**{watch} runs an older Wear OS.** Dialed faces need Wear OS 6 — everything on your watch stays as it is." | "Browse faces anyway" → Home |

Rules: state transitions animate with the existing `springStandard` fade-through; **auto-advance
never yanks** — READY reached while the user reads shows a ✓ beat for 600 ms before enabling the
CTA. "Skip ▸ / Browse faces anyway" is always present (the store must never be hostage to
hardware). Once READY has been seen once, the screen never appears again (persisted with the
existing `onboarded` flag); all later states live in the Home pill.

**Deleted:** all three pager pages, the pager dots, "One tap of permission", "Yours in one
purchase". The single trust line "Nothing leaves your phone." carries the privacy story.

### 5.2 PHONE · Home first-visit hero (new, temporary block)

**Purpose / metric:** first push started. Disappears forever after the first successful install
(`installedFaceIds` ever non-empty → persisted flag).

Above the COLLECTIONS label: a full-width card, same vitrine language —
title **"Put your first face on"**, three curated starters (one per flagship collection; free-tier
faces once the 2D map lands — until then any three the owner names), each a 96 dp `FaceDial` that
opens its Detail. No new components: `CollectionCard` variant with tappable dials.

### 5.3 PHONE · Push sheet (copy-only changes)

- Done + needsActivation: title "On your watch" → body **"{Face} is installed. On your watch,
  follow the three steps shown — press and hold, swipe, tap."** (mirrors `GuidedHandoff` verbatim).
- New state **NeedsWatchSetup** (from `RESPONSE_NEEDS_SETUP`): icon watch+gear, title **"Finish
  setting up your watch"**, body **"Open Dialed on your watch and tap *Set up Dialed*. Then push
  {Face} again."**, primary "Done", quiet "Retry". (No false "busy", no dead retry loop.)

### 5.4 WATCH · Set up Dialed (replaces FirstRunScreen + MakeDefaultScreen)

**Purpose / metric:** both permissions granted + default face active in one tap. Measure: grant
rate of set-active (target > 90 % — context does the work), % completing vs "Later".

**Wireframe (round, scrollable ScreenScaffold, EdgeButton primary):**

```
        ╭────────────────╮
       │   ◉ DialMark    │   ← 96dp; WHEN A PUSH IS PENDING: the incoming face's
       │                 │     name replaces the mark's caption (see variant)
       │  Make Dialed    │   ← titleMedium
       │  your watch face│
       │  One tap: Dialed│   ← bodyMedium, onSurfaceVariant
       │  can install the│
       │  faces you pick,│
       │  and your first │
       │  face goes on   │
       │  now.           │
       │     Later       │   ← quiet text button (disabled-colour)
        ╰───[Set up Dialed]───╯   ← filled gold EdgeButton
```

**Tap behaviour ("Set up Dialed"):** request `PUSH_WATCH_FACES` → on grant request
`SET_PUSHED_WATCH_FACE_AS_ACTIVE` → on grant `installDefault()` + `setActive()` → existing
**Celebration ("Dialed in.")** → exit to the face. The two system dialogs arrive in direct
consequence of a tap whose label promised exactly this — benefit + scope on screen, per the
permission research. All plumbing already exists (`WearViewModel.makeDefaultFaceActive`,
`MainActivity` launchers); only the *trigger point* moves.

**Degradation ladder (each state honest, none terminal):**
- Push perm denied → same screen, card note "Dialed can't install faces without this." CTA "Allow"
  (re-request); permanently denied → existing Settings route (`FirstRunScreen` denied state,
  reused verbatim).
- Push granted, set-active denied → default face still installs; Home shows the existing
  "Set as your face" EdgeButton path (`HomeScreen.kt:69`). Nothing re-asks — the platform forbids
  it; the long-press coaching (existing) is the honest fallback.
- **"Later"** → Home no-face state. Not a failure: the ask simply moves to the best possible
  context — the first pushed face (5.5).

**Variant — a push is pending (arrived pre-setup):** hero shows the *pending face's name*:
title **"{Face} is waiting"**, body **"Allow Dialed to install it, and it goes on now."** CTA
**"Allow and install"**. This is the in-context ask at its strongest — the benefit is literally the
face they chose 10 seconds ago on the phone. (Requires the listener to remember the last
NEEDS_SETUP face name — one string in `WfpStateStore`.)

### 5.5 WATCH · everything after setup — unchanged

Receive / "On your wrist." / Concierge / GuidedHandoff / Celebration / Home / Unsupported stay as
shipped (v0.27–v0.28 already match Google's prescribed education flow and the wear design spec).
The only wear-side additions: the NEEDS_SETUP answer (5.6) and the pending-face variant (5.4).

### 5.6 Wire + listener (the one transport touch)

`WearConstants`: append `RESPONSE_NEEDS_SETUP: Byte = 3` (ordinal-append rule respected;
old phones treat 3 as "not proceed" → generic busy copy — degraded, never wrong).
`DialedListenerService.onRequest`: before the session claim, `if (!repo.hasPushPermission())
return NEEDS_SETUP` (+ store pending face name). Phone `WatchBridge.pushFace` maps 3 →
`PushStatus.NeedsWatchSetup`. **Nothing else in the transfer path changes.**

---

## 6. Motion & interaction (M3 Expressive, existing tokens only)

- Setup-state changes: fade-through + `springStandard`; READY ✓ beat uses `springExpressive`
  overshoot once (this is a "hero moment" per M3E — the only bounce on the screen).
- CTA label swaps animate as text crossfade, never layout jumps.
- Face trio on Setup: staggered 40 ms entrance (reduced-motion: none).
- Watch: keep Confirm haptics at grant-success and Celebration (already shipped). No new motion on
  the watch — the celebration sweep is the moment.
- Reduced motion honoured everywhere via the two existing helpers (`isReducedMotion`,
  `isReducedMotionWear`); every animation above has a static end-state twin.

## 7. Accessibility

- All new tap targets ≥ 48 dp (setup CTA 52 dp; "Later"/"Skip" get 48 dp boxes around the text —
  the current MakeDefault "Not now" padding pattern already does this).
- Status card is a TalkBack **live region (polite)** like the pill; state changes announce
  ("Pixel Watch 4 is ready").
- Contrast: status card copy `onSurface`/`onSurfaceVariant` on `surfaceContainerHigh` — existing
  audited pairs; the gold CTA keeps its dark-on-gold label (≥ 4.5:1, already in the design system).
- Dynamic type: all copy in type roles; the setup card allows 2-line wrap at 200 % without clipping
  (fixed heights banned); wear screens stay on scrollable `ScreenScaffold` (nothing can clip).
- Watch: EdgeButton labels are full sentences for screen readers ("Set up Dialed"); the two-dialog
  sequence announces naturally as system dialogs.
- No information is colour-only: every pill/card state pairs colour with words.

## 8. Edge cases & failure states (the matrix the flow must survive)

| Situation | Experience |
|---|---|
| Airplane mode / BT off during setup | NO_WATCH state with "Bluetooth looks off" line when adapter is off (adapter state is readable without permission); recovers live. |
| Watch reboots mid-setup | INSTALL_WAIT/OPEN_ON_WATCH simply re-detect; no error surfaced unless the user asks ("Having trouble?"). |
| Two watches paired, one supported | Capability node wins (existing `select()`); Setup names the watch it chose; the unsupported one is ignored (documented in Settings watch card later). |
| Push mid-transfer + app killed | Existing behaviour preserved (transfer completes on watch; badges reconcile on next query). |
| Wear app updated but phone old (no code 3 mapping) | Old phone shows busy copy — degraded, truthful enough, self-heals on phone update. Wire rule kept. |
| User denies set-active forever | Never re-asked (platform). Every path lands on the long-press coaching; Home's hint already covers it. |
| Reinstall of phone app with face already on watch | Setup detects READY instantly; wear side already silently resolves its one-time step. |
| Face pushed while watch app never opened | NEEDS_SETUP loop (5.3/5.4) — the one new path this spec adds. |
| No Play listing yet (today's GitHub-APK reality) | WATCH_APP_MISSING CTA opens the 3-step manual guide instead of remote-install; the state machine is identical, only the CTA's action differs (single `WatchAppInstaller` seam). |

## 9. Premium experience

- **Onboarding sells nothing.** No paywall, no price, no "PRO" badges anywhere pre-value.
- The trial *is* the free faces (owner model D4). The paywall appears exactly once in the journey:
  tapping a **locked** face — which structurally can only happen after browsing real faces, and in
  practice after the first free face is worn (the value event the 2.1× evidence keys on).
- Craft signals carry the premium story instead: real dials as the first pixels the user sees, the
  restraint of a single trust line, zero permission grabs on the phone, the "Dialed in."
  celebration. (Deliberate echo of the owner's boutique language across HANDOFF specs.)
- When Billing lands (Phase 3), the paywall's restore path stays in Settings + paywall (already
  built); onboarding remains untouched by it.

## 10. Developer implementation notes (for the implementing session)

**Scope guard: transport files that stay byte-identical:** `TransferSession.kt`,
`WatchFacePushRepository.kt`, `FacePreviewExtractor.kt`, phone `WatchBridge` channel path;
`DialedListenerService` gains only the permission pre-check + pending-name store;
`WearConstants` gains only appended constants. All CLAUDE.md WFP rules apply unchanged.

1. **Phone watch-state machine** (`transport/WatchSetupState.kt`, new): combine
   `NodeClient.getConnectedNodes()` (already a dependency) + existing capability flow + existing
   `queryInstalledState().supported` into the 7 states of §5.1. Poll nodes on screen-visible +
   capability events (no new listeners leak — mirror `connectedWatch`'s callbackFlow shape).
2. **SetupScreen.kt** replaces `OnboardingPager.kt`; `viewModel.completeOnboarding()` fires on
   first READY-continue or Skip. Reuse `FaceDial`, `DialedButton`, pill styles. Delete the pager.
3. **`WatchAppInstaller` seam**: `interface { fun install(nodeId) }` — impl A (now): open in-app
   guide sheet; impl B (Phase 4): `RemoteActivityHelper` → `market://details?id=com.dialed.app`
   targeted at the watch node (this is what Horologist's `installOnNode` does; hand-roll it —
   Dialed already ships `RemoteActivityHelper` on the wear side, same artifact works on phone —
   adding the Horologist dependency is NOT required and NOT recommended for one call).
4. **Wire:** `RESPONSE_NEEDS_SETUP = 3`; listener pre-check; `PushStatus.NeedsWatchSetup`;
   sheet content per §5.3. Pending-face name: one `stringPreferencesKey` in `WfpStateStore`,
   written at NEEDS_SETUP answer, cleared on successful setup/install.
5. **Wear merge:** new `SetupScreen` (watch) replacing FirstRun+MakeDefault in `WearApp`'s `when`;
   navigator gate becomes `!pushGranted || needsDefaultSetup` → SetupScreen (with pending-face
   variant when `WfpStateStore.pendingFaceName != null`). Keep `permanentlyDenied` branch (reuse
   FirstRun's denied composable). The chained-permission launcher moves from post-push-grant
   (`MainActivity.kt:28`) into the single tap handler.
6. **Home hero row:** `SettingsStore` boolean `firstInstallDone` (set in
   `MainViewModel.refreshInstalledState` when installed set is non-empty); HomeScreen shows the
   hero card while false. Curated ids: a 3-string list constant next to `FaceCatalog` (owner picks).
7. **Delete list:** `OnboardingPager.kt`, pager strings, `ic_shield` usage if orphaned.
8. Ship loop per CLAUDE.md (compile-review → adversarial review → owner approves → version bump
   both modules → tag next free). ⚠ Wear-side changes need on-wrist verification — especially the
   merged permission sequence and the NEEDS_SETUP round-trip; add both to the release's on-wrist
   checklist. This work does NOT touch `faces/` — keep the uncommitted face-work tree untouched.

**Suggested slicing:** Slice 1 = phone Setup screen + state machine + hero row + copy fixes
(pure `:app`). Slice 2 = watch merge + wire code + sheet state (needs on-wrist test). Slice 3
(Phase-4-gated) = remote install CTA.

## 11. Success metrics (each screen has one)

| Surface | Metric | Target |
|---|---|---|
| Setup (phone) | reach READY on first launch / time on screen | > 80 % of watch-owners; < 40 s |
| Setup (watch) | set-active grant rate; completion vs Later | > 90 %; > 75 % |
| Home hero | first push started within first session | > 70 % |
| Push sheet | NEEDS_SETUP loop completes (setup then successful re-push) | > 80 % |
| End-to-end | **install → first chosen face live on wrist** | median < 3 min |

(No analytics pipeline exists; these are the *design* targets and the owner's manual-test rubric —
instrument only if/when a privacy-clean counter is ever wanted.)

---

# 13. Stabilization audit (2026-07-22, shipped `dialed-v0.30.0`)

**Trigger:** the owner reported the v0.29.0 first-run journey as "heavily broken on real devices".
The device-report field in the brief arrived **empty**, so this pass carries **no reproduction
evidence** — it is a full code-path audit against the scenario matrix below, not a fix of named
symptoms. ⚠ **Every finding here is reasoned from the code; none is confirmed on a wrist.** If the
owner's actual symptoms are not in §13.2, they are still open and need re-reporting with steps.

**Scope guard honoured:** `TransferSession.kt`, `WatchFacePushRepository.kt`,
`FacePreviewExtractor.kt`, `ReceiveScreen.kt`, `ConciergeScreen.kt` and the whole channel path are
**byte-identical**. `DialedListenerService` changed by one `withTimeoutOrNull` (a CLAUDE.md rule it
was violating). `wear-common` gained **one appended path constant** — no ordinal moved, no existing
value changed.

## 13.1 Scenario matrix — spec vs. what the code did

`✗` = defect found (id in §13.2). `✓` = walked and correct as shipped.

| # | Scenario | Spec says | v0.29.0 code actually did |
|---|---|---|---|
| S1 | Fresh install, both sides | Setup → one tap → both dialogs → default installs + activates → "Dialed in." → exit to face | ✗ W4 (screen frozen + re-tappable through the install), ✗ W6 (Home flashes first), ✗ P1 (Setup flashes on phone) |
| S2 | Deny the install permission once | Same screen, note + "Allow" (re-request) | ✗ **W1** — jumped straight to the Settings dead-end, no re-ask anywhere |
| S3 | Deny both / "don't ask again" | Settings route | ✓ route correct, ✗ **W2** (grant in Settings → still stuck on "Permission needed"), ✗ W7b (no way off that screen) |
| S4 | Tap "Later", come back | Ask returns at the first pushed face | ✗ **W3** — phone said "open Dialed on your watch and tap Set up Dialed"; that button existed **nowhere** once Later had resolved the gate |
| S5 | **Push a face BEFORE watch setup** | NEEDS_SETUP → remember name → "{Face} is waiting" → **face goes on** | ✗ **P5** — the watch installed the *bundled default*, cleared the name, and the chosen face was never sent again. The screen's promise was false |
| S6 | Push after setup (ownership chain) | Swaps in place, no tap | ✓ unchanged and correct |
| S7 | set-active one-shot already spent | GuidedHandoff, never a false success | ✓ unchanged and correct |
| S8 | Setup succeeds but nothing activates | (unspecified) | ✗ **W5** — no confirmation at all; two granted permissions looked like a no-op |
| S9 | Phone with no watch paired | NO_WATCH after a ~2 s CHECKING beat | ✗ **P2** — one empty `getConnectedNodes()` at launch races the Data Layer sync → "No watch is paired" over a paired watch |
| S10 | Watch paired, Dialed watch app missing | WATCH_APP_MISSING + guide | ✗ **P3** — capability queried once, listener never re-fires for an already-present capability → stuck on "needs the Dialed watch app" over an installed app |
| S11 | Unsupported watch (no WFP) | Honest note + browse anyway | ✓ correct (`RESPONSE_UNSUPPORTED` + query sentinel) |
| S12 | Open-app-on-watch prompt path | OPEN_ON_WATCH → auto-advance | ✗ **P4** (overlapping RPCs flap the card), ✗ **P6** (stuck on CHECKING forever if the watch never answers) |
| S13 | BT drop / watch reboot mid-transfer | Lock released, phone told | ✓ walked in full: `timeoutJob` + `claimTerminal()` + finalize-in-`finally`; phone 120 s > watch 60 s + 30 s install. **No change made** |
| S14 | Process death / rotation, every screen | State survives | ✓ phone `rememberSaveable` + `ScreenSaver`; watch beats are in-memory by design (a fresh VM starts clean) |
| S15 | Both apps reinstalled after uninstall | Setup detects READY fast | ✓ (helped by P2/P3) |
| S16 | Upgrade from v0.28.0 (perm granted, no default face) | Setup shown once | ✓ gate correct; ✗ W4/W5 apply to it too |
| S17 | Upgrade with the default face already active | Never shown | ✓ `loadHome()` resolves it silently |
| S18 | Old phone ↔ new watch | Degrade safely | ✓ `RESPONSE_NEEDS_SETUP=3` → generic busy copy; `!setup:` line → `mapNotNull` drops it; **new** `PATH_SETUP_COMPLETE` → no listener → ignored |
| S19 | New phone ↔ old watch | Degrade safely | ✓ no flag → `pushGranted=null` → `?: true` (never nag); no nudge → manual Retry |
| S20 | Home starter card lifetime | Retires after the FIRST CHOSEN face | ✗ **P7** — retired by the bundled default face at setup, before the user picked anything |

## 13.2 Defects, root causes, fixes

**W1 · One denial = a Settings dead-end.** `pushDenied` was a single flag set on *any* denial and
wired to a screen named `permanentlyDenied`. Android's first denial is re-askable.
→ `MainActivity` now reads `shouldShowRequestPermissionRationale` **after** the denial and splits
`pushSoftDenied` (offer "Allow" on the same screen) from `pushPermanentlyDenied` (Settings).

**W2 · Granting in Settings didn't un-stick it.** `refresh()` re-read the permission but never
cleared the denial flags. → `refresh()` clears both whenever `hasPushPermission()` is true.

**W3 · "Later" was a one-way door.** `skipDefaultFaceSetup()` set `onboardingComplete`, which made
`needsSetup` false forever, while the phone kept instructing the user to tap a button that no
longer rendered. → wear `HomeScreen` carries **"Set up Dialed"** whenever the install permission is
missing, with an honest caption ("Not set up yet · Dialed needs your OK…").

**W4 · No progress, and the button stayed live.** Between the last dialog and the end of
`installDefault()` (seconds) the setup screen was unchanged; a second tap re-entered the chain and
raced a second install. → `setupBusy` + `SetupWorkingScreen` + a `setupRunning` re-entrancy guard +
a 45 s watchdog (a working state with no exit is the worst failure mode on a watch).

**W5 · Silent success.** `activated == false` (set-active refused, or no bundled default) produced
no beat at all — the screen just became Home's "No face yet". → `SetupSettledScreen` ("You're set.")
— honest, and it never claims an activation that didn't happen.

**W6 · Home flashed before Setup on first run.** `WearUiState`'s defaults deliberately mean "don't
show setup"; rendering before the DataStore answered showed Home for a beat. → a `booted` gate with
a 700 ms ceiling.

**W7 · Celebration cancelled by `onResume`.** `refresh()` cleared `setupCelebrate` on every screen
wake, swapping "Dialed in." for Home and skipping the exit-to-face. → the clear is removed (the flag
is in-memory and its own screen clears it on dismiss). **W7b:** the permanently-denied screen had no
exit at all → "Not now" added, and `skipDefaultFaceSetup()` clears the denial flags (otherwise the
denial *is* the reason the screen shows, so Later was a dead button).

**W8 · Unbounded DataStore write on the binder thread.** The NEEDS_SETUP pending-face write was the
one op in `onRequest` with no timeout, against an explicit CLAUDE.md rule. → `withTimeoutOrNull(3 s)`.

**P5 ⭐ · "{Face} is waiting" never delivered that face.** The watch promised "allow, and it goes on
now", then installed the **bundled default** and cleared the pending name; the phone's sheet told the
user to push again by hand. → the watch sends **`PATH_SETUP_COMPLETE`** (appended, one-way) once
every dialog is closed and the default is installed; the phone re-pushes the held face
automatically, and the watch shows **"Bringing {Face} over…"** instead of celebrating a default the
user never chose.
⭐ **Why a message and not a poll — this is the load-bearing decision.** The phone learns
`pushGranted` from the query flag, which flips true the instant the *first* dialog is granted, i.e.
while the **set-active dialog is still on screen**. A push arriving then calls `wakeAndLaunchUi()`,
which starts `MainActivity` over the system dialog and **cancels it — permanently burning the
once-ever `SET_PUSHED_WATCH_FACE_AS_ACTIVE` request**. Polling `pushGranted` was therefore rejected
outright. The nudge fires exactly once, only after the whole chain is finished.

**P1 · Setup flashed on every cold start.** `onboarded` defaulted to `false` while DataStore loaded.
→ tri-state (`Boolean?`); nothing renders until it is known (the splash background covers it).

**P2 · "No watch is paired" over a paired watch.** One empty `getConnectedNodes()` was treated as
proof. → NO_WATCH needs `EMPTY_PROBES_BEFORE_NONE` consecutive misses **and** a 2.5 s settle window
(both are needed: the probe fires from two places at once at launch).

**P3 · "Needs the Dialed watch app" over an installed watch app.** The capability was queried once;
`OnCapabilityChangedListener` does **not** re-fire for a capability already present at subscribe
time. → `requestCapabilityRefresh()` re-queries on the Setup poll and on `ON_RESUME`.

**P4 · Setup card flapped.** 3 s poll against a 12 s query = up to four overlapping RPCs; a stale
reply landing last reverted a fresh one. → single-flight with a one-deep queue.

**P6 · Stranded on "Looking for your watch…".** A live capability plus an unanswered query left
`watchSetupDone` null forever, which renders as CHECKING with a disabled CTA. → after two misses the
phone assumes OK and lets the user through (pushing is the real test, and the sheet is honest).

**P7 · Starter card retired by the default face.** `installedPackages.isNotEmpty()` counted the
bundled default, which is not a catalog face. → gate on the catalog-mapped set.

**P8 · "Installing on your watch…" latched forever.** → resets when the state moves on, and times
out after 120 s.

**P9 · Dishonest states elsewhere.** Home's pill said "Connected" for a watch that would refuse every
push; Detail said "Connect a Wear OS 6 watch to install" with the watch sitting right there.
→ new `WatchConnection.NEEDS_SETUP`, plus APP_MISSING/NEEDS_SETUP copy in the pill, Detail and
Settings.

## 13.3 Defects found in the fix itself (second adversarial pass)

Six, all fixed before tagging: "Later" trapped the user on the denied variants (the denial was
itself the reason the screen showed); the working state could outlive its chain (watchdog added);
Home flashed for one frame between the working state and the closing beat (outcome flags are now set
*before* the busy flag is released); dismissing a received face re-showed "Bringing {Face} over…"
for the face that had just landed (any transfer now supersedes the one-shot beats); Home's
permission prompt outranked "Set as your face" for an installed-but-inactive face; the nudge was not
timeout-bounded.

## 13.4 Still open / deliberately not done

- **Nothing wakes the watch on a NEEDS_SETUP answer.** A refused push leaves the watch screen dark;
  the phone says to open Dialed. Waking a watch to report a *refusal* was judged intrusive. Revisit
  only if on-wrist testing says the hand-off is missed.
- **The auto re-push needs the phone app alive.** The listener is a live `MessageClient` listener
  (no manifest service, matching the existing finalize listener). If the phone app was killed, the
  sheet's Retry is the fallback. A manifest `WearableListenerService` on the phone would close this
  and is the natural next step if it ever bites.
- **Reliable first auto-apply remains impossible** (researched to ground truth 2026-07-18). Nothing
  here attempts it; every path still degrades to the honest `GuidedHandoff`.
- **On-wrist verification is outstanding for all of it** — see the release's test script.

---

## 12. Kickoff prompt for the implementing session (copy verbatim)

> Implement Slice 1 of `docs/ONBOARDING-REDESIGN.md` in D:\Apps\WearOS Apps\WatchFaces\Dialed App —
> the phone Setup screen (§5.1), watch-state machine (§10.1), first-face hero row (§5.2), and copy
> fixes (§5.3), exactly per the spec's microcopy and the existing design tokens. Read CLAUDE.md
> first (ship loop, standing rules); do NOT touch `faces/`, the transport files named in §10's
> scope guard, or anything uncommitted in the working tree. Compile-review, adversarial review,
> then stop for owner approval before any commit/tag. Slice 2 (watch-side merge + NEEDS_SETUP wire
> code, §5.4–5.6) goes in a separate chat after Slice 1 is approved; flag every wear change for
> on-wrist testing.
