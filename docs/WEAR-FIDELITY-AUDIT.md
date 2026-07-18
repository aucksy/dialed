# Wear OS — Design Fidelity Audit

> Design source (read in full, CSS included): `Dialed Watch - Design Spec.dc.html` + `HANDOFF-WATCH.md`.
> Implementation: `wear/src/main/java/com/dialed/app/wear/**`.
> ⚠️ **Transport is off-limits** — `wfp/` (listener, repo, `TransferSession`, `WfpStateStore`),
> `WearViewModel` transfer logic and `wear-common` must stay byte-identical. All fixes are UI-only
> (`ui/screens`, `ui/components`, `ui/theme`, `ui/WearApp` navigation).

Screens are drawn in the spec at 2× on a 192dp round canvas (384px) → **dp = px ÷ 2**.

---

## ✅ Already faithful (leave alone)

| Element | Status |
|---|---|
| Tokens/theme (`Theme.kt`) | true-black `#000`, gold `#D8BC7A`, every color token exact ✅ |
| `FaceDial` | guaranteed perfect circle (`requiredSize` + center-crop), frozen hands, no ticking ✅ |
| `DialMark` | ring + gold hand at 1 o'clock (30°) ✅ |
| Home 1a/1b/1c | fractional layout, `ConnectionStatus` dot+label, 73dp face, `ACTIVE` overline, one EdgeButton (open-on-phone), no button when unreachable ✅ |
| Concierge 1k `OneTapApply` / 1l `Celebration` | face + "Set as my face"; "Dialed in." with gold sheen sweep + Confirm haptic + auto-exit (W2) ✅ (v0.24.0) |
| Concierge 1m `GuidedHandoff` | a platform-honest W3 (numbered step list + gentle highlight; the animated carousel is deliberately replaced — the real device path is the system long-press) ✅ |
| Unsupported 1n | dim dial mark + copy + tonal "OK" ✅ |
| `EdgeButton` / `BezelIndeterminate` (4.5dp gold) / `BezelSegments` (12) | present and on-token ✅ |

---

## ❌ Gaps to fix (UI-only)

### 1. HIGH — the "On your wrist." success moment (spec 1i / W1 beat 5) is **missing entirely**
The receive flow jumps **Installing… → concierge**, skipping the catch's payoff: the big check
path-draws (52dp), a gold arc sweeps the bezel, "On your wrist." rises, "{name} installed", Confirm
haptic, ~1.2s, then auto-advance. `WearApp.kt` routes `Success` straight to `ConciergeScreen`.
**Fix:** a brief success confirmation (own composable) shown for ~1.2s on `Success` before the
concierge. `Success` already carries `faceName` + `preview`, so no transport change. Reduced-motion →
static check + fade.

> Note: "Dialed in." (1l) is a *different* beat — it fires after the face is **set active**. 1i fires
> when the **install** completes. The flow should show 1i (catch done) → concierge (activation).

### 2. MED — the error state (spec 1j) drops the bezel entirely
`ReceiveState.Failed` renders a plain text screen. Spec 1j: the **bezel arc freezes where it stopped
and tints error red**, the face dims/desaturates, "Interrupted" + reassurance. The
`BezelIndeterminate(error = true)` component already exists but is unused here — dropping it breaks
the receive flow's whole visual language on the one screen a user is most anxious on.
**Fix:** render the frozen error bezel + dimmed face in the Failed state (reuse the bezel hero shell).
Copy: keep "push it again from your phone" honest (the watch cannot re-initiate a phone push, so the
action stays a dismiss, not a fake "Retry" — documented deviation from the spec's "Retry" label).

### 3. MED (systemic) — no branded type scale; screens use the default Wear M3 typography
`DialedWearTheme` sets `colorScheme` but **no `typography`**, so every `titleMedium` / `bodyLarge` /
`labelSmall` renders at the platform default, not the spec §3 Wear scale (displaySmall 30/34,
titleLarge 24/28, titleMedium 22/26, bodyLarge 17/23, bodyMedium 15/20, labelLarge 15/20, labelSmall
12/16 **+2.5 caps tracking**, arcMedium 15). This under-sizes headings and loses the `ACTIVE`/
`RECEIVING` overline letter-spacing across **every** screen — likely the biggest "feels off" driver.
**Fix:** add `DialedWearTypography` matching §3 and pass it to `MaterialTheme`. One change, all screens
tighten. (Instrument Sans stays a later swap, same as phone — sizes/weights/tracking now match.)

### 4. LOW–MED — First-run polish (1d/1e/1f)
- Rationale copy uses `bodyMedium` (15sp); spec rationale is **`bodyLarge`** (17/23) — undersized.
- 1f "denied" is missing the **info-in-circle icon** the spec shows above the copy.
- 1e "You're set." granted moment is skipped (routes straight to Home) — **optional**; nice-to-have.
**Fix:** bodyLarge rationale + the denied info icon. (Granted moment: propose, owner's call.)

### 5. Documented deviations (NOT bugs — keep, note them)
- **Transfer % readout (1g "62%")** — the Wear Data-Layer channel gives **no byte progress**
  (`Receiving` carries none), so a determinate arc + "62%" would be *fabricated*. The honest choice is
  the indeterminate sweep we already show. Left as-is on purpose.
- **1m carousel animation** — replaced by a clearer numbered-step guide because the real activation
  path is the system long-press (platform wall; see `ConciergeScreen` header). Deliberate.
- **Tile (1p)** — spec says "optional, not v1". Skipped. Correct.

---

## Fix plan (this pass)
1. `DialedWearTypography` → `DialedWearTheme` (systemic). **(3)**
2. New `ReceiveSuccess` confirmation ("On your wrist.") + route `Success` through it before concierge. **(1)**
3. Error state → frozen error bezel + dimmed face. **(2)**
4. First-run → bodyLarge rationale + denied info icon. **(4)**

Ship gate: push main (CI `:app:assembleDebug` + `:wear:assembleDebug`) green, adversarial review,
confirm the transport files show **zero diff**, then tag. Bump app+wear in lockstep.
