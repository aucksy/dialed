# Dialed — Wear OS Companion · Design Handoff for Claude Code

Native Kotlin · Wear Compose Material 3 (Material 3 Expressive) · Wear OS 6+ · dark only, true-black.
Visual spec (round mockups + states): `Dialed Watch - Design Spec.dc.html`. Section letters below match it.
Phone-side sibling: `HANDOFF.md` — springs, brand, and FaceDial API are shared.

---

## 0. Philosophy (decided)

A thin, delightful **concierge, not a store**. Three jobs: permissions, the catch (receive → install → success), activation. The watch holds one Dialed face at a time; browsing/paywall stay on the phone. Fewer elements, more polish; the face is the star.

## 1. Brand

- Wordmark "Dialed" — trailing period ALWAYS `primary` gold, even in dynamic-color themes.
- Dial mark: ring + center dot in `onBackground`, gold hand resting at 1 o'clock (30° abs). Launcher icon: mark at 60% of safe zone on flat `#0B0B0D`, circular mask; monochrome layer for themed icons (spec 1o).
- Type: Instrument Sans (downloadable fonts API), same as phone.

## 2. Color tokens (Wear — true black)

```kotlin
object DialedWearColors {
    val background        = Color(0xFF000000) // AMOLED true black
    val onBackground      = Color(0xFFF4F2ED)
    val surfaceContainer  = Color(0xFF16161A)
    val surfaceContainerHigh = Color(0xFF1D1D22)
    val onSurface         = Color(0xFFF4F2ED)
    val onSurfaceVariant  = Color(0xFFA6A29A)
    val outline           = Color(0xFF34343B)
    val outlineVariant    = Color(0xFF232328)
    val primary           = Color(0xFFD8BC7A)  // champagne gold
    val onPrimary         = Color(0xFF221A06)
    val primaryContainer  = Color(0xFF2A2517)
    val onPrimaryContainer= Color(0xFFEFD9A2)
    val success           = Color(0xFF7ECFA0)  // connected
    val error             = Color(0xFFE5776C)
    val disabled          = Color(0xFF8B857A)
    val progressTrack     = Color(0x17FFFFFF)  // rgba(255,255,255,.09)
}
```

**Dynamic color (optional delighter, not v1-blocking):** `primary`/`primaryContainer` may derive from the active face's dominant accent (e.g. Nocturne → #7FB4FF, Lume → #59E698). The wordmark period, celebration sheen, and "Dialed in." moment stay brand gold always.

Contrast: onBackground on #000 = 18.9:1; all body text ≥ 4.5:1; gold on black 10.4:1.

## 3. Type scale (Wear)

| Role | size/line | weight | tracking | use |
|---|---|---|---|---|
| displaySmall | 30/34 | 700 | −0.5% | hero moments ("Dialed in.", "On your wrist.") |
| titleLarge | 24/28 | 700 | 0 | confirmations ("You're set.") |
| titleMedium | 22/26 | 600 | 0 | screen titles, face names |
| bodyLarge | 17/23 | 400 | 0 | rationale copy (permission, empty) |
| bodyMedium | 15/20 | 400 | 0 | secondary copy |
| labelLarge | 15/20 | 600 | +0.1 | EdgeButton labels |
| labelSmall | 12/16 | 600 | +2.5 caps | overlines (ACTIVE, RECEIVING) |
| arcMedium | 15 | 600 | +0.5 | TimeText, curved captions |

Copy discipline: max ~2 short lines of body per screen. Line length ≤ ~26 chars at bodyLarge on 192dp.

## 4. Shape · layout

- Face previews: perfect circles, always. Sizes (dp): 28 (tile) · 50 (coaching) · 73 (home/receive) · 97 (celebration).
- Cards/coaching surfaces: 26dp radius. Pills/chips: full.
- EdgeButton: platform shape (bottom follows bezel). ONE EdgeButton per screen, ever. Filled = primary/onPrimary; tonal = primaryContainer + 1dp gold@35% border; disabled = surfaceContainer + 32% content.
- Canvas: design at 192dp round; verify at 225dp XL. TimeText via ScreenScaffold on every screen except celebrations/confirmations.
- BezelProgress: CircularProgressIndicator full-bezel, 4.5dp stroke, inset 3.5dp, gold on progressTrack. Segmented (12) variant for install.

## 5. Motion tokens

```kotlin
object DialedWearMotion {
    val springFast     = spring<Float>(dampingRatio = 0.9f,  stiffness = 1400f) // presses, shape-morph
    val springStandard = spring<Float>(dampingRatio = 0.9f,  stiffness = 700f)  // enter/exit, carousel
    val springSettle   = spring<Float>(dampingRatio = 0.65f, stiffness = 400f)  // landings, overshoot
    const val bezelSweepMs = 900   // indeterminate arc, one rotation
    const val durFast = 150; const val durStd = 250; const val durEmph = 350
    const val coachLoopMs = 6000   // ×3 then pause → Replay
}
```

### Signature moments (spec §G)

- **W1 The catch** — wake fade 150ms → indeterminate 120° arc (900ms/turn) → determinate arc tracks bytes, face opacity .4→1 / scale .8→1 → install: 12 segments fill clockwise → land: springSettle 1.06→1 + ring pulse (alpha 0→.4→0, 500ms) + `HapticFeedbackType.Confirm` → check path-draw 300ms, "On your wrist." rises 250ms → auto-advance to concierge 1.2s. Post-transfer budget ≤ 1.6s. Custom Canvas/Lottie allowed here.
- **W2 One-tap apply** — press shape-morph 26→18dp + scale .97 (springFast); apply instant (no spinner < 400ms); gold conic sheen sweeps ring once 700ms alpha .25; "Dialed in." rises 250ms + Confirm haptic; auto-exit 1.2s.
- **W3 Coaching loop** — 6s: dot in 150ms → hold 1.3s (dot .8 + gold ring grows) → carousel slide springStandard → tap pulse → gold select ring 250ms in → rest. ×3 then pause with Replay. Custom Canvas/Lottie allowed here.
- **W4 Micro** — connect dot pulses once 600ms; EdgeButton shape-morph press; screen transitions slide along a 90° bezel arc (springStandard); open-on-phone uses the platform ConfirmationDialog open-on-phone animation.
- **W5 Ambient/reduced motion** — never draw animated content in ambient (transfer continues, notify on completion); reduced motion: arcs → static ring + % text, coaching → 3 static step cards, sheen/settle → 250ms fades. Haptics stay. All loops stop when screen dims.

## 6. Components (spec §H)

- `FaceDial(face, size)` — shared API with phone; hands FROZEN at 10:09:36 on watch (no ticking — battery).
- `EdgeButton` — filled / tonal / disabled.
- `BezelProgress` — indeterminate / determinate / segmented / error (arc tints error and freezes in place).
- `ConnectionStatus` — dot + label: Connected (success + glow) / Connecting (outline dot) / Not reachable (disabled). Pulse once on connect. TalkBack live region.
- `CoachingCard` — the W3 loop + caption cycler + "Got it".
- Platform: ScreenScaffold/AppScaffold + TimeText, ConfirmationDialog (success + open-on-phone variants), AlertDialog, TransformingLazyColumn (only if a scroll ever appears).

## 7. Screens (spec §A–E)

| Screen | Spec | States |
|---|---|---|
| FirstRunScreen | 1d/1e/1f | needs-permission → system dialog · granted · denied (→ Settings). Set-active permission is ONE-SHOT: never request here; spend it at first install (1k) |
| HomeScreen | 1a/1b/1c | connected+active · connected+empty · phone unreachable (no action — reconnection is automatic) |
| ReceiveFlow | 1g/1h/1i/1j | transferring · installing · success (ConfirmationDialog) · error+retry. Launched by push, full-screen |
| ActivationConcierge | 1k/1l/1m | one-tap (only while set-active available) · applied celebration · gesture coaching loop |
| UnsupportedScreen | 1n | Wear OS < 6 gate; phone push sheet mirrors it |
| DialedTile | 1p | OPTIONAL, not v1: label · active face (28dp… 3-slot) · Open Dialed chip |

## 8. Accessibility

- Tap targets ≥ 48dp (EdgeButton exceeds; "Got it" pill 48dp target).
- TalkBack: face preview = "«Name» watch face"; announce transfer progress at 25% steps; connection changes via live region; coaching loop has a text alternative.
- Dynamic type to 1.3×; text in sp; screens tolerate two-line titles.
- Reduced motion per W5; haptics preserved.

## 9. Build order

Theme/Tokens → FaceDial (port) → HomeScreen → FirstRun → ReceiveFlow + W1 → Concierge (W2, then W3 loop) → Unsupported → (optional) Tile + dynamic color.
