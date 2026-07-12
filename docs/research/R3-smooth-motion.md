# R3 — Smooth WFF Motion (owner issue #7: "motion faces janky / not smooth")

Research doc only. No XML/build/tag changes. Maps to **Phase 4**.
Scope: the 18 Fable Collection faces bundled in `Dialed App/faces/*/app/src/main/res/raw/watchface.xml`.

---

## 0. TL;DR (headline)

WFF gives exactly **one** frame-rate-managed continuous-motion primitive: a **`<SecondHand>` (inside `<AnalogClock>`) with a `<Sweep frequency=…>`** child. Everything else that "moves" is either (a) a per-**second** or per-**minute** `<Transform>` re-evaluation (a *tick*, not a sweep — steps once per time unit), or (b) a **`<SequenceImages>` sprite loop** driven by `<AnimationController>` at a fixed `frameRate`, decoupled from the clock.

Across the 18 faces the real jank sources are only three:
1. **Kinetik-Metronome** — 24-frame sprite pendulum at **`frameRate="12"`** (12 fps → visibly steppy) and *not time-synced* (free 2 s loop). **Primary offender.**
2. **Arclight-Pulsar** — seconds ring is a `<Transform target="endAngle" value="[SECOND] * 6">` **tick** (steps once/sec), not a sweep.
3. **Kinetik-Turbine** and **Kinetik-Escapement** — large rotating sprites driven by `<Sweep frequency="5">` (5 Hz = the *lowest* smooth rate → mildly steppy on a big disc). All the Vespera faces + Orrery + Solstice already use `frequency="15"` (the smoothest fixed option) and are fine.

The de-jank levers are all small XML edits: **raise Sweep `frequency` to `15` (or `SYNC_TO_DEVICE`)**, **convert per-second `[SECOND]` Transforms to a real `SecondHand`+`Sweep` where the shape allows**, and **re-author or accept the Metronome sprite loop** (sprite is the only WFF option for a partial-arc pendulum; smoothing means *more frames*, not a higher `frameRate` alone).

---

## 1. WFF motion primitives (primary sources)

### 1a. `<AnalogClock>` hands + `<Sweep>` — the only managed continuous motion
- `<AnalogClock>` holds `<HourHand>`, `<MinuteHand>`, `<SecondHand>` child image/hand elements. Each rotates about `pivotX`/`pivotY` (0–1, scaled). ([Represent the time](https://developer.android.com/training/wearables/wff/time))
- A `<SecondHand>` **must** contain either a `<Sweep>` (continuous) or a `<Tick>` (discrete bursts). ([SecondHand reference](https://developer.android.com/reference/wear-os/wff/clock/second-hand))
- **`<Sweep frequency="…">`**: "Allows watch face creators to define the behavior of `SecondHand` elements, such that the seconds increase in a **smooth continuous motion**." Introduced Wear OS 4. `frequency` = "the frequency, in **Hz**, at which the hand is redrawn," and **must be one of `2`, `5`, `10`, `15`, or `SYNC_TO_DEVICE`**. `SYNC_TO_DEVICE` = "the hand redraws as quickly as the device allows while keeping the sweeping motion smooth." ([Sweep reference](https://developer.android.com/reference/wear-os/wff/clock/sweep))
- Hour/Minute hands are **not** swept — they step (hour once/min via the minute fraction, minute once/min). That is correct and never reads as jank.
- **`SYNC_TO_DEVICE` is only valid on `<Sweep>`** (the SecondHand path). There is no equivalent for arbitrary `<Transform>` rotations.

### 1b. `<Transform>` interpolation — evaluated per time-unit, i.e. a *tick*
- `<Transform target="angle|endAngle|alpha|…" value="<expression>">` recomputes a property from an expression built on data sources (`[SECOND]`, `[MINUTE]`, `[HOUR_0_23]`, `[BATTERY_PERCENT]`, `[COMPLICATION.*]`, `[MOON_PHASE_TYPE]`, `[CONFIGURATION.*]`, …).
- **Redraw granularity follows the coarsest data source the expression reads.** An expression on `[SECOND]` re-evaluates **once per second** → the property *snaps* in 1 Hz steps. An expression on `[MINUTE]`/`[HOUR_*]` snaps once per minute. There is no interpolation *between* those values — `<Transform>` is a re-evaluation, not a tween.
- The WFF time data sources include a **millisecond** unit (index 7 in the WFF time-unit set). ([WFF overview](https://developer.android.com/training/wearables/wff)) So a sub-second expression *can* be written, but see §2 for the battery/behaviour caveat — for continuous rotation the sanctioned tool is `<Sweep>`, not a millisecond `<Transform>`.

### 1c. `<PartAnimatedImage>` / `<SequenceImages>` + `<AnimationController>` — sprite loops
- **`<SequenceImages frameRate="N">`**: `frameRate` = frames **per second**. "If the frameRate of SequenceImages with 30 images is set to 15, it is animated to show 30 frames in 2 seconds. If the frameRate is set to 30, 30 frames are played in 1 second." **The device may skip frames if it can't sustain the rate** (e.g. a 15 fps-capped device showing every other frame of a 30 fps sequence). ([SequenceImages reference](https://developer.android.com/reference/wear-os/wff/group/part/animated-image/sequence-image), [Create animated images](https://developer.android.com/training/wearables/wff/transform/images))
- **`<AnimationController>`** attributes: `play="TAP | ON_VISIBLE | ON_NEXT_SECOND | ON_NEXT_MINUTE | ON_NEXT_HOUR"`, plus `repeat` (bool), `loopCount` (int), `delayPlay`, `delayRepeat`, `resumePlayBack`, `beforePlaying`/`afterPlaying` ∈ `DO_NOTHING | FIRST_FRAME | THUMBNAIL | HIDE`. ([AnimationController reference](https://developer.android.com/reference/wear-os/wff/group/part/animated-image/animation-controller))
- **Key limitation:** a sprite loop is **free-running**, not phase-locked to the clock. `play="ON_VISIBLE" repeat="TRUE"` just loops the sequence forever at `frameRate`; it does *not* know what second it is. So a "pendulum" or "metronome" built this way drifts relative to real seconds — which reads as "decorative but not a real mechanism."

### 1d. Frame / update-rate model (interactive vs AOD)
- **Interactive:** the runtime redraws when a referenced data source changes. A face with **no** sub-minute animation is essentially static between minute ticks. A `<Sweep>` SecondHand pins the redraw cadence to its `frequency` Hz (2/5/10/15/SYNC). A running `<SequenceImages>` pins it to `frameRate`. Google's own guidance: **"Most animations look fluid at 30 fps, so avoid running your animations at a higher frame rate"** and use dynamic/burst frame rates. ([Watch-face performance](https://developer.android.com/training/wearables/watch-faces/performance)) Note WFF `<Sweep>` **caps at 15 Hz** for fixed values — `15` (or `SYNC_TO_DEVICE`) is therefore the smoothest deterministic setting.
- **Memory ceilings** (affect how heavy a sprite sequence can be): **10 MB in ambient, 100 MB in interactive.** ([Watch-face performance](https://developer.android.com/training/wearables/watch-faces/performance))
- **AOD / ambient:** the display refreshes roughly **once per minute**; all continuous motion **must freeze**. Correct WFF pattern = give the moving element a `<Variant mode="AMBIENT" target="alpha" value="0">` (hide it) and render a separate frozen/dimmed AOD element. Sprite sequences also stop when not visible; `beforePlaying="FIRST_FRAME"` parks the sprite if the platform declines to loop.

---

## 2. Motion inventory — all 18 faces

Legend: **Sweep-15** = smooth (good) · **Sweep-5** = continuous but low-rate · **Tick** = per-second/minute `<Transform>` snap · **Sprite** = `<SequenceImages>` loop · **Snap** = digit/state image chosen by alpha `<Transform>` (no motion) · **Static** = no sub-minute motion.

| # | Face | Canvas | Moving element(s) | Primitive | Rate | Jank? |
|---|------|:------:|-------------------|-----------|------|:-----:|
| 1 | Kinetik-**Metronome** | 300 | pendulum wand (`wand_00..23`) | **Sprite** `frameRate=12`, `ON_VISIBLE repeat` | 12 fps, free 2 s loop | **YES — primary** |
| 2 | Arclight-**Pulsar** | 450 | seconds ring | **Tick** `endAngle=[SECOND]*6` | 1 Hz step | **YES** |
| 3 | Kinetik-**Turbine** | 300 | rotor disc (216 u, `SecondHand`) | **Sweep** `frequency=5` | 5 Hz | mild |
| 4 | Kinetik-**Escapement** | 300 | small gear (`SecondHand`) + large gear (`MinuteHand`) | **Sweep** `frequency=5` / minute-step | 5 Hz | mild |
| 5 | Arclight-**Solstice** | 450 | orbiting `second_dot` (`SecondHand`, optional) + sun `angle` Transform | **Sweep-15** + per-min Tick | 15 Hz / 1 min | no |
| 6 | Kinetik-**Orrery** | 300 | comet (`SecondHand`), planet hour/min hands | **Sweep-15** + minute-step | 15 Hz | no |
| 7 | Vespera-**Aurum** | 300 | `second` hand (+ hour/min); ranged-value gauge `endAngle` | **Sweep-15** + complication Tick | 15 Hz | no |
| 8 | Vespera-**Meteorite** | 300 | `second` hand (+ hour/min) | **Sweep-15** | 15 Hz | no |
| 9 | Vespera-**Opaline** | 300 | `second` hand (+ hour/min) | **Sweep-15** | 15 Hz | no |
| 10 | Aether-**Horizon** | 300 | sun/moon arc position | **Tick** `angle=f(HOUR+MIN/60)` | 1 min step | no (slow celestial arc) |
| 11 | Vespera-**Noir** | 300 | hour/min hands; `moon_0..7` phase | **Static hands** + **Snap** (moon by `MOON_PHASE_TYPE`) | minute-step | no |
| 12 | Vespera-**Salon** | 300 | hour/min hands only (no seconds) | minute-step | 1 min | no |
| 13 | Kinetik-**Odometer** | 300 | `drum_0..9` digits by alpha `<Transform>` | **Snap** (digit swap, no roll) | on digit change | no* |
| 14 | Settype-**Halftone** | 300 | `ht_*` digit sprites by alpha | **Snap** | on digit change | no |
| 15 | Settype-**Marquee** | 300 | `bulb` marks (static) + digital text | **Static** | — | no |
| 16 | Settype-**Counterform** | 300 | digital text | **Static** | — | no |
| 17 | Settype-**Masthead** | 300 | digital text | **Static** | — | no |
| 18 | Aether-**Ember** | 300 | coal-bed `glow` (static sprite) + digital text | **Static** | — | no |

`*` Odometer name implies a rolling drum; it actually **snaps** the digit (alpha select). Not jank — but a missing-animation *design gap* (the "roll" the owner may expect). Out of R3's smoothness scope; flag for a later design pass, not Phase 4.

**Verified facts used:** Metronome `frameRate="12"`, 24 frames, `AnimationController play="ON_VISIBLE" repeat="TRUE" beforePlaying="FIRST_FRAME"` (lines 98–128). Turbine rotor `SecondHand … <Sweep frequency="5"/>`, AOD-hidden + frozen `rotor_aod` at alpha 23 (lines 37–48, **AOD handling is correct**). Escapement gear_small `<Sweep frequency="5"/>` (line 51). Pulsar seconds ring `<Transform target="endAngle" value="[SECOND] * 6"/>` (line 92). All Vespera + Orrery + Solstice SecondHands `<Sweep frequency="15"/>`.

---

## 3. Jank diagnosis (root causes)

1. **Low sprite frame rate + no clock phase-lock (Metronome).** 12 fps is below the ~30 fps "fluid" threshold, so each of the 24 poses holds ~83 ms → the swing visibly *stutters*. Worse, `ON_VISIBLE repeat` is a free loop: it neither represents seconds nor stays in phase, so it looks like a decoration glitching rather than a mechanism keeping time. **This is the face the owner most likely means by "janky."**

2. **Per-second `<Transform>` = a tick, never a sweep (Pulsar).** `endAngle=[SECOND]*6` re-evaluates only when `[SECOND]` changes → the ring jumps 6° once a second. That is *correct* as a ticking second indicator, but next to the Vespera faces' 15 Hz sweeps it reads as "the smooth ones are smooth and this one isn't."

3. **`<Sweep frequency="5">` on large/high-detail rotors (Turbine, Escapement).** 5 Hz redraw of a 216-unit disc (Turbine) or a toothed gear (Escapement) means the sprite jumps in ~1.2°/frame increments; on a big radius the *edge* travel per frame is large enough to shimmer. `15` (or `SYNC_TO_DEVICE`) quarters the step size. (The Vespera *hands* are thin, so 5 Hz would have been borderline for them too — they already use 15.)

4. **Not a cause here, but watch for it:** heavy per-frame expressions in the hot path. In these faces the only per-**second** expressions are Pulsar's single `[SECOND]*6` and complication `endAngle` math (evaluated on complication update, not per frame). No face has an expensive expression re-run at Sweep frequency. **Frame-budget is not currently the bottleneck** — the jank is *primitive choice + rate*, not expression cost.

5. **AOD freeze correctness (audit, mostly clean).** Turbine, Metronome, and the Vespera faces correctly hide the moving element in AMBIENT (`<Variant mode="AMBIENT" alpha="0">`) and show a static/dim AOD twin. **Phase-4 must not break this** — every rate/primitive change has to keep the `AMBIENT` variant that parks motion, or AOD will try to animate (burn-in + battery + platform rejection).

---

## 4. De-jank recipe (by motion type)

**Preference order for any continuous motion:** native `<SecondHand>`+`<Sweep frequency="15">` ▶ `<Sweep frequency="10/5">` ▶ per-second `<Transform>` tick ▶ `<SequenceImages>` sprite (last resort — only for shapes a rotating hand can't express, e.g. a partial-arc pendulum or a non-rotational motion).

### Type A — Sweep hands (Turbine, Escapement, and all already-good faces)
- Set **`frequency="15"`** for any hand whose sprite is large, high-detail, or near the bezel. Use **`SYNC_TO_DEVICE`** if you want max smoothness and accept "as fast as the device allows."
- Cost: higher redraw Hz = more interactive battery. 15 Hz is the accepted smooth ceiling; do **not** exceed it (no higher fixed value exists, and >30 fps is discouraged anyway).
- Keep the existing `<Variant mode="AMBIENT" target="alpha" value="0">` on the hand + the frozen AOD twin. **Do not** add a Sweep behaviour that renders in ambient.

### Type B — per-second `<Transform>` tick you want to look swept (Pulsar ring)
- **Option B1 (recommended, lowest risk): leave it as an honest tick.** A ticking seconds ring is a legitimate, battery-cheap aesthetic. If keeping it, consider it *resolved* and document intent.
- **Option B2 (smooth, higher cost): drive the angle from a sub-second source.** Rewrite `value="[SECOND] * 6"` to interpolate within the second using the millisecond unit (WFF time-unit index 7). This forces sub-second redraws → battery cost like a Sweep but **without** Sweep's frame-rate management, so it is strictly worse than a real Sweep. Only use if the shape can't be a hand. **[UNVERIFIED]** exact millisecond token name/availability in a `<Transform>` expression on this WFF version — confirm against the WFF expression reference before authoring.
- **Option B3 (best smoothness, if geometry allows): make it a `SecondHand`.** A full-circle seconds *ring/dot* can be a `<SecondHand>` sprite (like Solstice's `second_dot`) with `<Sweep frequency="15">`, instead of an arc `endAngle`. A *partial* arc gauge cannot. Pulsar's ring is a full 360° track, so a swept dot/marker riding the ring is feasible and would match Solstice.

### Type C — sprite sequences (Metronome; also the pattern to avoid for new motion)
The pendulum swings a partial ±24° arc, so it **cannot** be a `<SecondHand>` (which does 360°/min). Sprite is the only WFF tool. To de-jank:
- **Raise perceived smoothness by adding frames, not just `frameRate`.** `frameRate` sets frames/sec, so bumping `12`→`24` on the *same* 24 frames halves the loop to 1 s (faster swing) — usually not desired. To keep a 2 s swing *and* hit ~30 fps you need ~**48–60 frames** at `frameRate="24"`–`30`. Trade-off: more PNGs = more APK size + interactive memory (budget 100 MB, ample) and the device may frame-skip on low-fps hardware.
- **Accept device caps:** if a target device caps at 15 fps, authoring 30 fps just drops every other frame — so ~15–24 fps with enough frames is the pragmatic sweet spot.
- **Phase-lock isn't possible** for a free sprite loop; if "not a real mechanism" is the complaint, the honest fix is to (a) make the swing subtle/ambient decoration, or (b) replace the metronome concept with a swept element. Design call, not a pure code fix.
- Keep `beforePlaying="FIRST_FRAME"` (parks gracefully) and the `AMBIENT` alpha-0 + `wand_aod` twin.

### Type D — snaps & slow ticks (Odometer/Halftone digits, Horizon sun arc, Noir moon) — **leave alone**
Digit/state swaps and once-per-minute celestial arcs are *supposed* to step; they are not jank. Only revisit Odometer if the owner explicitly wants a rolling-drum animation (a sprite-sequence add, separate design task).

### Frame-budget guidance (all faces)
- Target **≤30 fps**; for Sweep that means `frequency="15"` max. Don't chase higher.
- Keep per-second/ per-frame expressions trivial (these faces already do).
- Never animate in AMBIENT; always ship a frozen AOD twin.
- Watch interactive memory only if adding large sprite sequences (100 MB cap).

---

## 5. Per-face fix list (Phase 4 worklist)

| Face | Change | Effort | Risk | Note |
|------|--------|:-----:|:----:|------|
| **Kinetik-Metronome** | Re-author swing as ~48–60 frames @ `frameRate="24"` for a smooth 2 s loop **or** accept as subtle decoration; keep `FIRST_FRAME` + AOD twin | high (new sprites) | med | Biggest visible win; needs asset regen, not just XML |
| **Arclight-Pulsar** | Preferred: convert seconds ring to a swept `SecondHand` marker (`<Sweep frequency="15">`) à la Solstice; else keep as intentional tick (B1) | med | low–med | 450 canvas; verify pivot/registration |
| **Kinetik-Turbine** | `<Sweep frequency="5">` → **`15`** (or `SYNC_TO_DEVICE`) | trivial (1 attr) | low | Large detailed rotor benefits most |
| **Kinetik-Escapement** | gear_small `<Sweep frequency="5">` → **`15`** | trivial | low | Toothed edge shimmers at 5 Hz |
| Solstice / Orrery / Aurum / Meteorite / Opaline | none | — | — | Already `frequency="15"` |
| Horizon / Noir / Salon / Odometer / Halftone / Marquee / Counterform / Masthead / Ember | none | — | — | No continuous motion by design |

**Regression guard for every edit:** after changing a `frequency` or sprite, re-confirm the element still has its `<Variant mode="AMBIENT" target="alpha" value="0">` and that the frozen AOD twin is untouched. Run the repo's `wff-validator` gate (already in CI) before tagging.

---

## 6. Execution-ready RECIPE (Phase 4, no re-research needed)

1. **Quick wins first (XML-only, ~5 min, low risk):**
   - `faces/Kinetik-Turbine/app/src/main/res/raw/watchface.xml` line ~41: `<Sweep frequency="5" />` → `<Sweep frequency="15" />`.
   - `faces/Kinetik-Escapement/…/watchface.xml` line ~51: `<Sweep frequency="5" />` → `<Sweep frequency="15" />`.
   - (Optional, max smoothness) use `frequency="SYNC_TO_DEVICE"` instead of `15` on either; measure battery on the Pixel Watch 4 before committing.
2. **Pulsar seconds ring — pick one:**
   - B1: keep the `[SECOND]*6` tick, mark #7 "resolved (intentional tick)" for Pulsar. **Zero code.**
   - B3: replace the `<PartDraw>` arc whose `<Transform target="endAngle" value="[SECOND]*6">` (line ~92) with an `<AnalogClock>`/`<SecondHand>` marker sprite on the ring radius + `<Sweep frequency="15">`, mirroring `Arclight-Solstice`'s `second_dot` block. Preserve the battery/master-gauge arcs unchanged.
3. **Metronome — decide design intent, then execute:**
   - If keeping the mechanism: regenerate the wand sprite at ~48 frames (or 60), set `<SequenceImages frameRate="24">`, keep `AnimationController play="ON_VISIBLE" repeat="TRUE" beforePlaying="FIRST_FRAME"`, keep `wand_aod` + AMBIENT alpha-0. Verify APK size + 100 MB interactive-memory budget.
   - If simplifying: reduce amplitude / make it a faint ambient-of-interactive decoration, or drop the animated wand for a static pose. (Owner design call.)
4. **AOD audit (every touched face):** confirm the moving element keeps `<Variant mode="AMBIENT" target="alpha" value="0">` and a separate frozen AOD twin. Turbine/Metronome already correct — don't regress.
5. **Do NOT** raise any `<Sweep>` above `15` (no higher fixed value exists) and **do not** exceed ~30 fps on sprites (device will frame-skip; battery cost with no visual gain).
6. **Validate & ship** per repo norms: run `wff-validator`, adversarial review, then tag per-face release. One APK per face (Fable Collection rule).

---

## 7. Gaps / [UNVERIFIED] / screenshot-gated

- **[UNVERIFIED]** exact millisecond data-source token usable inside a `<Transform>` expression (Option B2). WFF exposes a millisecond time unit, but confirm the precise `[…]` name and that it forces sub-second redraw before authoring. Primary fix paths (B1/B3) don't need it.
- **[UNVERIFIED]** practical max sustainable `<SequenceImages frameRate>` on Pixel Watch 4 (device may cap ~15 fps and frame-skip). Bench on-device before committing 24–30 fps Metronome sprites.
- **No screenshot needed for R3 diagnosis** — jank causes were confirmed directly from the XML (frame rates, Sweep frequencies, Transform expressions). An owner screenshot/video would only help *rank* which face he means by "janky"; the code evidence points to Metronome first, Pulsar second, Turbine/Escapement third.
- The Metronome smoothing that best satisfies "not smooth" is **asset regeneration** (more frames), which is a design+pipeline task, not a one-line XML edit — flag scope to the owner.
