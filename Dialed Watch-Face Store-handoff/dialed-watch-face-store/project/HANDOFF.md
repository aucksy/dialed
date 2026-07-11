# Dialed — Design Handoff for Claude Code

Native Android · Kotlin · Jetpack Compose · Material 3. Dark theme is primary.
Visual spec (mockups + states): `Dialed — Design Spec.dc.html`. Section letters below match it.

---

## 1. Brand

- **Name:** Dialed. The trailing period is part of the wordmark and is ALWAYS `primary` gold.
- **Logo (recommended, option 1c "Sweep"):** thin circular ring + center dot in `onSurface`, single gold hand resting at 1 o'clock (30° from 12). Launch animation: hand sweeps from 12 → 1 with `springSettle` (§5, F6).
- **Adaptive icon:** foreground = mark at 60% of safe zone; background flat `#0B0B0D` (dark) / `#F2EFE8` (light); monochrome layer = mark in single color for Android 13+ themed icons.
- **Wordmark:** Instrument Sans 700, letter-spacing −2%, gold period. Tagline: "Your wrist, dialed."

## 2. Color tokens

```kotlin
object DialedColorsDark {
    val background        = Color(0xFF0B0B0D)
    val surface           = Color(0xFF131316)
    val surfaceContainer  = Color(0xFF17171B)
    val surfaceContainerHigh = Color(0xFF1D1D22)
    val onSurface         = Color(0xFFF4F2ED)
    val onSurfaceVariant  = Color(0xFFA6A29A)
    val outline           = Color(0xFF34343B)
    val outlineVariant    = Color(0xFF232328)
    val primary           = Color(0xFFD8BC7A)  // champagne gold
    val onPrimary         = Color(0xFF221A06)
    val primaryContainer  = Color(0xFF2A2517)
    val onPrimaryContainer= Color(0xFFEFD9A2)
    // semantic
    val success           = Color(0xFF7ECFA0)  // also watchConnected
    val error             = Color(0xFFE5776C)
    val locked            = Color(0xFF8B857A)
    val scrim             = Color(0x99000000)  // 60%
}

object DialedColorsLight {
    val background        = Color(0xFFF7F5F0)
    val surface           = Color(0xFFFFFFFF)
    val surfaceContainer  = Color(0xFFF1EEE7)
    val surfaceContainerHigh = Color(0xFFEBE7DD)
    val onSurface         = Color(0xFF1B1A17)
    val onSurfaceVariant  = Color(0xFF6E6A61)
    val outline           = Color(0xFFDCD8CC)
    val outlineVariant    = Color(0xFFE9E5DA)
    val primary           = Color(0xFF8F7326)  // darkened gold for text/contrast
    val onPrimary         = Color(0xFFFFFFFF)
    val primaryContainer  = Color(0xFFF0E3BE)
    val onPrimaryContainer= Color(0xFF4A3A0C)
    val success           = Color(0xFF2F8F5B)
    val error             = Color(0xFFC0453B)
    val locked            = Color(0xFF9A937F)
}

// IMPORTANT: the filled CTA is the SAME in both themes:
// container #D8BC7A, content #221A06. Only gold TEXT/links switch to
// #8F7326 in light theme for contrast.
```

Contrast notes: onSurface on background = 16.9:1 (dark); onPrimary #221A06 on #D8BC7A = 8.6:1. All body text ≥ 4.5:1.

## 3. Typography — Instrument Sans (Google Fonts, downloadable fonts API)

| Role | size/line | weight | tracking | use |
|---|---|---|---|---|
| displayLarge | 44/50 | 700 | −1% | paywall price, splash wordmark |
| headlineLarge | 30/36 | 700 | −0.5% | face name on detail |
| headlineSmall | 24/30 | 700 | 0 | onboarding titles, sheet titles |
| titleLarge | 20/26 | 600 | 0 | app bar titles |
| titleMedium | 16/22 | 600 | 0 | list item titles |
| bodyLarge | 16/24 | 400 | 0 | descriptions |
| bodyMedium | 14/20 | 400 | 0 | secondary copy |
| labelLarge | 14/20 | 600 | +0.1 | buttons |
| labelMedium | 12/16 | 600 | +0.4 | pills, section headers (often uppercase) |
| labelSmall | 11/14 | 600 | +0.5 | chips, captions |

## 4. Spacing · shape · elevation

- Spacing scale (dp): 4, 8, 12, 16, 24, 32, 48. Screen margin 24 · grid gutter 20 · card padding 16 · section gap 32.
- Grid: Home = 2 columns of 150dp dials (responsive: `GridCells.Adaptive(minSize = 150.dp)`); vitrine small grid 3 × 96dp.
- Shape (dp radius): sm 12 · md 16 · lg 20 (cards) · sheet 28 (top corners) · buttons/pills/chips = full. **Face previews are always perfect circles.**
- Elevation: lvl0 flat (surfaceContainer) · lvl1 card (surfaceContainerHigh + shadow 0/4/14 @ 35%) · lvl2 sheet (0/12/32 @ 50%). Keep shadows subtle; dark theme relies on surface tone more than shadow.

## 5. Motion tokens

```kotlin
object DialedMotion {
    val springFast       = spring<Float>(dampingRatio = 0.9f,  stiffness = 1400f) // presses
    val springStandard   = spring<Float>(dampingRatio = 0.9f,  stiffness = 700f)  // enter/exit
    val springExpressive = spring<Float>(dampingRatio = 0.8f,  stiffness = 380f)  // shared element
    val springSettle     = spring<Float>(dampingRatio = 0.65f, stiffness = 400f)  // transfer landing
    const val durFast = 150; const val durStd = 250; const val durEmph = 350 // ms
}
```

### Signature moments (spec section F)

- **F1 Living gallery:** one `withFrameNanos` loop per screen drives `secondsAngle = (epochMs % 60000) / 60000f * 360f` (continuous sweep). Each `FaceDial` Canvas applies `rotate(angle)` — transform only, no per-frame recomposition of the tree. Pause when off-screen / lifecycle STOPPED. Reduced motion: freeze at 10:09:36.
- **F2 Shared-element expand:** `SharedTransitionLayout`, key `"face/{id}"`, `boundsTransform = springExpressive`, clip `CircleShape` throughout. Scrim + detail text fade in `durStd` delayed 80ms. Predictive back scales with gesture.
- **F3 Push-to-watch transfer (the money moment):** beat 1 sheet enters (springStandard slide + scrim 250ms) → beat 2 face scales 1→0.55 traveling a quadratic arc to the watch silhouette, 550ms, progress bar tracks real transfer → beat 3 lands with springSettle overshoot 1.06→1, ring pulse alpha 0→.4→0 (500ms), `HapticFeedbackType.Confirm` → beat 4 check path-draws 300ms, "On your wrist." rises 250ms, auto-dismiss after 1.2s. Lottie acceptable for beats 2–4.
- **F4 Unlock celebration:** 45° gold-white gradient band (alpha .12) translates −30%→130% across the grid over 900ms (FastOutSlowIn, once); lock badges fade+scale-to-.8 180ms each, staggered 40ms by grid index; one Confirm haptic.
- **F5 Micro:** press scale .96 (springFast) · status pill pulses once on connect (scale 1→1.06→1 + dot glow, 600ms) · vitrine featured face parallax `ty = scroll * .15`, scale 1→.92 over first 200dp · filter chip crossfade 150ms.
- **F6 Logo launch:** gold hand rotates −30°→0° (rest = 30° absolute) with springSettle, starts 100ms after splash, ~450ms, then crossfade to Home (250ms).
- **Reduced motion:** hands frozen; shared element → 200ms crossfade; transfer → progress bar only; sheen → 250ms badge fade; no pulse/parallax. Haptics stay.

## 6. Components (section G)

- `FaceDial(face, size, locked = false, ticking = false)` — circular Canvas: dial background, 12 rim ticks, hour/minute/second hands, center cap; optional lock badge (bottom-right, 24% of size, gold-bordered dark circle). Sizes used: 44 (list row), 72 (sheet), 96 (grid-sm), 150 (grid), 224 (vitrine featured), 268 (detail hero).
- `DialedButton` — filled (primary CTA, height 56 / 48 in sheets), tonal (primaryContainer + gold border), text. Pressed = scale .96. Disabled = 32–35% alpha content.
- `InstallButton` — state machine: `Locked → Ready → Installing(progress) → Installed/Active → Error(retry)`. Visuals per spec 1g.
- `WatchStatusPill` — Connected (success tint + glowing dot) / Disconnected (outline, gray dot) / Connecting (spinner dot) / Unsupported (error tint). Pulse once on connect.
- `FilterChip` selected = primaryContainer bg + gold border; `FeatureChip` = outline only.
- `UnlockBanner` — gold-tinted gradient card, price + Unlock button (Home, pre-purchase only).
- `SettingsGroup` — surface card radius 20, hairline dividers inset 16.
- `ThemeSelector` — segmented, full-radius track.
- `PushToWatchSheet` — ModalBottomSheet radius 28, handle 36×4, watch silhouette + beats above.
- Progress: linear 5dp track `outlineVariant` / fill `primary`; circular 2.5dp indeterminate.

## 7. Screens (sections A, D, E)

| Screen | Composable | Notes / states |
|---|---|---|
| Onboarding | `OnboardingPager` | 3 pages, HorizontalPager, skippable, gold page indicator |
| Home | `HomeScreen` | Layout options: 1d showroom grid (default) or 1e vitrine. States: loading (shimmer circles, 1400ms loop), loaded, empty, no-watch. Pre-purchase shows UnlockBanner + lock badges |
| Face detail | `FaceDetailScreen` | Shared element from grid. InstallButton 5 states. Chips = complications |
| Paywall | `PaywallScreen` | One price ($11.99 placeholder), 3 value bullets, restore link. No tiers, no dark patterns |
| Push to watch | `PushToWatchSheet` | 4 beats (F3) + no-watch state + unsupported state (Wear OS 6 requirement, graceful copy) |
| Settings | `SettingsScreen` | Watch info, Restore purchase, Theme (System/Dark/Light), About |
| Dev sideload | `DevSideloadScreen` | debug build flavor ONLY. Plain/monospace, APK picker + target + push + log. Dark-only |

## 8. Accessibility

- Touch targets ≥ 48dp (gallery dials 150dp; icon buttons 42dp visual inside 48dp target).
- Dynamic type: all text in sp; layouts tolerate 1.3× scale.
- Content descriptions: FaceDial = "«Name» watch face, «tag»" (+ ", locked"); status pill announces connection changes via live region.
- Respect `Settings.Global.ANIMATOR_DURATION_SCALE` / reduce-motion per §5.

## 9. Placeholder assets

The 8 faces (Meridian, Nocturne, Lume, Tangerine, Sector, Helio, Graphite, Riviera) are procedurally rendered placeholders — dial gradient + ticks + hands, definitions in the spec's logic. Swap with real face previews (circular PNGs or live renderers) without changing FaceDial's API. Price, purchase date, and watch IP are placeholders.
