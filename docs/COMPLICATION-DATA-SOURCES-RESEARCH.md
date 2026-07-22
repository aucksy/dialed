# Native complications across Wear OS watches — can the instrument dials show more than battery + steps?

**Question (owner, 2026-07-19):** "Research all native complications / info that leading Wear OS watches
(Pixel, Samsung Galaxy, Huawei…) give you, and what our instrument dials can support. Do we really have
to limit to battery and steps? As long as anything can be shown with a progress bar — why not support all?"

**Short answer: we already DO support "all", and the owner's instinct is right.** Every VAKT register is a
universal instrument. The only thing that is limited is the *first-install default* — and that limit is real
but small. Detail below; **the one thing that needs a decision next session is the heart-rate default (see §5).**

---

## 1. The key fact: the instrument is driven by the complication TYPE, not the data source

A Wear OS watch face never names a specific app. It declares, per slot, a list of **complication types** it
can draw (`supportedTypes`). The user then picks **any data source on their watch** — system or third-party —
that supplies one of those types, and the face renders it generically through `[COMPLICATION.*]` tag
expressions. *"The watch face does NOT need to know the specific data source… it only cares about the data
type, not which app provides it."* ([Android WFF complications docs][wff])

There are **8 complication types**. Two of them are gauges/progress instruments:
- **`RANGED_VALUE`** — a value between a min and a max (needle / arc fill). *"good for showing how much
  battery is left… or how many steps you have left to meet your goal."* ([AndroidX][types])
- **`GOAL_PROGRESS`** — progress toward a goal (min is implicitly 0, the value may exceed the target → an
  overflow lap). A v2+ type. ([AndroidX][types])
- (**`WEIGHTED_ELEMENTS`** — a segmented ring / pie, v2+. Also an "instrument".)
- The other five are text/image: `SHORT_TEXT`, `LONG_TEXT`, `MONOCHROMATIC_IMAGE`, `SMALL_IMAGE`, `PHOTO_IMAGE`.

**Our VAKT registers already advertise all 8 types** (`CIRC_TYPES` in `spec/cat-a2.js`). So the moment a user
opens the editor, **any provider on their watch that emits `RANGED_VALUE` or `GOAL_PROGRESS` can be dropped
onto any register and it renders as the live needle/ring** — calories, distance, active minutes, floors,
water, VO₂max, sleep score, elevation, anything. We are **not** limited to battery and steps. Those are only
the *defaults* (what shows before the user has picked anything). ✅ The design the owner is asking for is
already built.

---

## 2. What "leading watches" actually expose (the metrics a user can assign)

A slot that supports `RANGED_VALUE`/`GOAL_PROGRESS` will accept the gauge-type complications from whatever
health app is installed. What's typically available per platform:

| Platform / app | Instrument-able metrics (RANGED_VALUE / GOAL_PROGRESS complications) |
|---|---|
| **Pixel Watch — Fitbit** | Steps, distance, floors, calories, **Active Zone Minutes**, resting/live heart rate, Daily Readiness (Premium), exercise-day progress ([Google Health help][fitbit], [Tom's Guide][tg]) |
| **Samsung Galaxy Watch — Samsung Health** | Steps, calories, heart rate, **floors**, distance, water intake, active time, exercise goals ([Samsung][shealth]) |
| **Google Fit / Health Connect** | Steps, Move Minutes / Heart Points, calories, distance |
| **Huawei Health** (Huawei's own OS, not Wear OS) | Huawei runs HarmonyOS/Lite, **not Wear OS + WFF** — its faces use Huawei's own complication system, so our WFF faces don't run there. Not a target platform. ([Huawei][huawei]) |
| **System (built-in, every Wear OS 4+ watch)** | Battery %, step count — the only two that give a *guaranteed* gauge with no third-party app (see §4) |
| **Popular 3rd-party** | Weather (temp/precip %), timers, altimeter, compass bearing, sleep, stress, SpO₂, cycle tracking — many ship RANGED_VALUE |

**Takeaway:** the universe of "things a user can point a register at" is large and grows with whatever apps
they install. Our job is done by supporting the *types*, which we do.

---

## 3. The full system-provider list (what CAN be a default)

`DefaultProviderPolicy.defaultSystemProvider` accepts exactly these 14 built-in sources (no app needed)
([Android][dpp]):

`APP_SHORTCUT · DATE · DAY_OF_WEEK · DAY_AND_DATE · FAVORITE_CONTACT · HEART_RATE · NEXT_EVENT · STEP_COUNT ·
SUNRISE_SUNSET · TIME_AND_DATE · UNREAD_NOTIFICATION_COUNT · WATCH_BATTERY · WORLD_CLOCK · EMPTY`

`defaultSystemProviderType` can be `SHORT_TEXT · LONG_TEXT · MONOCHROMATIC_IMAGE · SMALL_IMAGE · PHOTO_IMAGE ·
RANGED_VALUE · EMPTY` — note **`GOAL_PROGRESS` is NOT a legal *default* type** (it works when a provider or the
user supplies it, and STEP_COUNT does supply it, but you can't pin an arbitrary default to it the same way —
confirm on-wrist).

---

## 4. Which built-in sources can drive a gauge *by default* (the real limit)

This is the only genuine limit, and it's about **defaults only** — not what the user can assign.

| Source | Guaranteed gauge default? | Notes |
|---|---|---|
| **`WATCH_BATTERY`** | ✅ **Yes — RANGED_VALUE** | The one rock-solid needle/arc default. ([Samsung/Android examples][dpp]) |
| **`STEP_COUNT`** | ✅ Ring — supplies RANGED_VALUE + SHORT_TEXT; GOAL_PROGRESS on v2 | Our steps default. |
| **`HEART_RATE`** | ⚠️ **No — SHORT_TEXT only** | **Critical finding, see §5.** |
| Everything else (DATE, WORLD_CLOCK, UNREAD, DAY_OF_WEEK, NEXT_EVENT, SUNRISE_SUNSET, APP_SHORTCUT, FAVORITE_CONTACT) | ❌ text/image only | Not gauges. |

So: for a *default* instrument with zero setup, only **battery (needle)** and **steps (ring)** are
guaranteed. That is why the safe defaults look like "just battery and steps" — but again, the *slots* accept
far more; the user unlocks the rest by assigning any health app's gauge complication.

---

## 5. ⚠️ CRITICAL FINDING — the heart-rate needle default is NOT reliable (revise GT + the family)

Google's docs: *"From Android U, `SystemDataSources.DATA_SOURCE_HEART_RATE` … **is only guaranteed to support
SHORT_TEXT** complications, but it's recommended for the ComplicationSlot to accept `SMALL_IMAGE` too because
**OEMs may choose to serve a shortcut to their health app instead of the live value**."* ([search: adding
complications / Wear release notes][types])

**What this means for us:** GT currently sets the HR register to `HEART_RATE / RANGED_VALUE`. Because the HR
*system* source only guarantees `SHORT_TEXT` (and some OEMs return an app shortcut), on many watches the HR
register will **NOT** render our needle on first install — it will fall back to a number, or an icon shortcut,
or blank. The needle we built for HR will only reliably appear when the **user assigns a third-party HR
provider that emits `RANGED_VALUE`** (e.g. a health-app "heart rate" gauge complication).

This corrects NF-2's earlier optimism. NF-2 was right that HR needs *no permission prompt*; but it's the
**type**, not the permission, that's the problem — the built-in HR source gives text, not a gauge.

**Options for next session (owner decision):**
- **(A) HR default = `HEART_RATE / SHORT_TEXT`** — on install the HR dial shows the heart icon + "72" (no
  needle); the needle appears if the user assigns a RANGED_VALUE HR provider. Honest, always works. Slightly
  inconsistent with battery(needle)/steps(ring) on day one.
- **(B) Keep `RANGED_VALUE` default and accept graceful degradation** — needle where the OEM supplies a
  ranged HR, text/blank elsewhere. Risky/inconsistent; not recommended without on-wrist proof.
- **(C) Make all three dials *look* consistent by defaulting the HR dial to a gauge-capable source** (e.g. a
  second battery/steps metric) — rejected: confusing.
- **(D) Ship the register as a universal gauge and let the STORE COPY teach it** — default HR to SHORT_TEXT
  (A), and lean into "assign any health metric to any dial" as a selling point.

**Recommendation:** **(A)** for the default + market the universality (below). The instrument rendering we
built stays exactly as-is; only the HR *default policy* line changes (`SHORT_TEXT`), and the register still
renders the needle beautifully for any RANGED_VALUE provider the user assigns. **Verify on-wrist** what the
Pixel/Galaxy HR system source actually returns before finalising.

---

## 6. Recommendations for next session

1. **We already "support all."** No generator work is needed to accept more metrics — every VAKT register
   renders `RANGED_VALUE`, `GOAL_PROGRESS` and `WEIGHTED_ELEMENTS` for any provider the user assigns. Confirm
   this is communicated (store copy: *"Point any dial at any health stat — steps, calories, distance, active
   minutes, heart rate — and it becomes a live gauge."*).
2. **Fix the HR default** per §5 (likely → `SHORT_TEXT`), then re-validate. One-line change per VAKT face.
3. **Consider a "gauge-friendly default set"** doc for the other collections too (Halo/Terra/Vespera gauges
   in the COMPLICATION-AUDIT): the same "any RANGED_VALUE provider works" logic applies fleet-wide.
4. **On-wrist verification list** (only the wrist can confirm): (a) what `HEART_RATE` system source returns on
   Pixel vs Galaxy; (b) that a user-assigned third-party RANGED_VALUE provider (e.g. Samsung Health calories)
   drives our needle across its own min/max; (c) that `GOAL_PROGRESS` default via `STEP_COUNT` renders the
   ring. Our gauge reads `[COMPLICATION.RANGED_VALUE_MIN/MAX]`, so it **auto-scales to any provider's range**
   — no per-metric config needed. That's the elegant part: one universal instrument, any metric.
5. **WEIGHTED_ELEMENTS** (segmented ring) is already supported — a natural fit for "activity ring"-style
   providers; worth calling out.

**Bottom line for the owner:** you don't have to limit anything. The dials are already universal gauges — any
health stat that a health app exposes as a progress/ranged complication will show as a live needle or ring the
moment the user assigns it. Battery and steps are just the two we can guarantee *out of the box* with no setup.
The only real fix this research surfaced is that **heart rate, by itself, only comes as a number from the
system** — so the heart-rate dial's *default* should show the number, with the needle kicking in for any real
ranged heart-rate provider.

---

## 7. Making the numbers relevant to whatever is assigned (BUILT 2026-07-19)

Owner: *"the numbers inside the chrono may be updated to make it more relevant to the complication…
whatever best we can do without making it look illogical or bad design."* Correct — and the needle/ring
design itself needed **no change at all** (clean ticks, no baked scale numerals ⇒ already metric-agnostic;
the gauge reads `[COMPLICATION.RANGED_VALUE_MIN/MAX]` so it auto-scales to any provider's range).

**XSD facts established this session** (extracted from `wff-validator.jar` — ⭐ it *bundles* `docs.zip`
containing all **467 XSDs for v1–v5**, so there is no need to clone google/watchface; fold into the skill):
- `<Condition>` **is legal inside `<Complication>`** (`2/complication/complicationElement.xsd` allows
  `Group`, `PartElementGroup`, `Condition`), with `Compare expression=…` + optional `Default`.
- ~~**BUT `Condition`/`Expression` are `arithmeticExpressionType` — arithmetic only.** There are **no string
  functions**…~~ ❌ **WRONG — corrected 2026-07-20.** `arithmeticExpressionType` composes `_functionType`,
  whose enum (`1/common/attributes/arithmeticExpressionType.xsd`) DOES include string/format functions:
  **`textLength()` · `subText(,,)` · `numberFormat(,)` · `icuText()` · `icuBestText()`**, alongside
  `round floor ceil fract abs clamp sqrt pow log log2 log10 exp deg rad sin cos tan asin acos atan rand`.
  ⇒ **`textLength([COMPLICATION.TEXT]) > 0 ? … : …` is expressible**, so the "TEXT else numeric" fallback
  IS possible after all, and `numberFormat()` may give the thousands separator we recorded as impossible.
  ⚠ The XSD proves these are *grammatically* legal; only the wrist proves the runtime honours them.
- The XSD does **not** enumerate `COMPLICATION.*` names (only `RANGED_VALUE_COLOR_INTERPOLATE` appears, in
  `primitiveListTypes.xsd`) — re-confirming the standing trap that **the validator cannot catch a wrong
  expression name**; only the wrist can.

**Decision (split by where it actually helps — implemented in `taggedContent`):**
| Type | Centre readout | Why |
|---|---|---|
| **RANGED_VALUE** | **`[COMPLICATION.TEXT]`** (provider's own formatted string) | This is the universal-gauge case: only the provider knows its units/precision. Raw `%.0f` printed "68" for battery (not "68%") and truncated "5.2 km" → "5". `text` is *optional*, but the needle+fill still carry the proportion if a source omits it, so the dial degrades to a working gauge rather than breaking. |
| **GOAL_PROGRESS** | **unchanged — numeric `%.0f` value + "of TARGET"** | `value` and `target` are *definitional* (always present) and goal metrics (steps, calories, active minutes) are whole numbers ⇒ already correct, and carries **zero** blank-risk. Deliberately not switched to TEXT. |

Net effect: assign **any** ranged provider to any VAKT register and it reads correctly in that metric's own
units, with the needle sweeping its own scale — no per-metric configuration, no design change.

**⚠ On-wrist test item:** confirm a few RANGED_VALUE providers (battery, a distance/pace source, a ranged
heart-rate source) actually populate `text`. If any shows a blank centre, flip that readout back to
`%.0f [COMPLICATION.RANGED_VALUE_VALUE]` — a one-line generator change.

---

### Sources
- [Provide useful data through complications — WFF][wff]
- [DefaultProviderPolicy — system provider list][dpp]
- [Wear Watchface / complication types (RANGED_VALUE, GOAL_PROGRESS, WEIGHTED_ELEMENTS)][types]
- [Fitbit on Pixel Watch — tracked metrics][fitbit] · [Tom's Guide Pixel/Fitbit features][tg]
- [Samsung Health on Galaxy Watch][shealth]
- [Huawei Health][huawei]

[wff]: https://developer.android.com/training/wearables/wff/complications
[dpp]: https://developer.android.com/reference/wear-os/wff/complication/default-provider-policy
[types]: https://developer.android.com/jetpack/androidx/releases/wear-watchface
[fitbit]: https://support.google.com/fitbit/answer/14236510
[tg]: https://www.tomsguide.com/news/google-pixel-watch-fitness-tracking-fitbit-features-youll-get-and-what-you-wont
[shealth]: https://play.google.com/store/apps/details?id=com.samsung.android.wear.shealth
[huawei]: https://consumer.huawei.com/en/mobileservices/health/
