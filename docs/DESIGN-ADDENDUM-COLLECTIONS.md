# Design Addendum — Collections Home

> **Status:** visual IA layer only (the "browse spine"). Written for the Phase-2D *slice* that
> renders a **Collections Home** against the real 43 bundled faces, grouped by `Face.series`.
> The commercial layer (free/paid split, per-collection product ids, coming-soon tiles,
> `config/catalog.json`, entitlement v2) is **deliberately out of scope** — it is blocked on the
> owner's parked collection MAP (`docs/CATALOG-AUDIT.md` §11). See **§6 Deferred** below.
>
> This addendum extends the imported handoff (`Dialed - Design Spec.dc.html` + `HANDOFF.md`),
> which predates collections. It invents **no new design language** — every value below is an
> existing token (`Color.kt` / `Type.kt` / `Dimens.kt` / `Shape.kt` / `Motion.kt`) or the vitrine
> card language from spec §1e.

---

## 1. What changes, in one line

The store spine goes from **one flat grid of 43 faces** to **Home = a list of collection cards →
a Collection screen = that collection's faces**. The face detail / push / install path is untouched.

```
Home (collection cards)  →  Collection (scoped face grid)  →  Face detail  →  Push to watch
        NEW                          NEW                        unchanged        unchanged
```

## 2. The collection model (derived, not configured)

There is **no config file** in this slice. Collections are derived at runtime, purely from the
bundled catalog, so the grouping is always honest to what actually ships:

- **Group by `Face.series`** → 10 collections, in catalog order:

  | Collection | Subtitle (from the face `tag`) | Faces |
  |---|---|---:|
  | Aether | Atmospheric | 2 |
  | Arclight | Solar | 2 |
  | Aurum | Haute horlogerie | 5 |
  | Halo | Data | 5 |
  | Kinetik | Mechanical | 5 |
  | Meridian | Dress | 5 |
  | Settype | Typographic | 4 |
  | Terra | Field | 5 |
  | Vakt | Instrument | 5 |
  | Vespera | Evening | 5 |

- **`id`** = the series string verbatim (`"Aether"`, `"Vakt"` …) — unique, stable, save-able.
- **`title`** = the series string.
- **`subtitle`** = the descriptor after `·` in any member's `tag` (e.g. `"Aether · Atmospheric"` →
  `"Atmospheric"`). All members of a series share one tag, so this is well-defined; if a tag has no
  `·`, the subtitle is empty and simply omitted.
- **`cover`** = the collection's first three faces (fewer if the collection has fewer).
- **Order** = first-appearance order in `FaceCatalog.faces` (already alphabetical). Deterministic.

> ⚠️ This grouping is the **current honest state**, NOT the launch map. The parked map (§11 of the
> audit) may merge/rename series into sellable collections (e.g. Vakt+Terra). We do **not** pre-empt
> that here — when the map is signed off, this derivation is replaced by `config/catalog.json`
> (Phase 2D proper) and the screens below feed off the config instead. The UI does not change; only
> the data source does.

## 3. Home — collection cards · **Variant A "vitrine hero cards"** (owner-chosen 2026-07-18)

> ⚠️ **Fidelity lesson (why this section was rewritten).** The first attempt (v0.25.0) used a
> cramped horizontal card with a 60dp cover trio — built from the *plan's prose*, not the spec's
> actual pixels. The design shows faces **large** (150dp in the §1d grid, 224dp featured in §1e,
> ~116dp overlapping trio in the paywall). The owner rejected it as too small. The corrected card
> below uses those real sizes. **Read design `.dc.html` files in full (CSS included) — never
> text-scrape them; the sizes live in the CSS.** A true-scale mock (real face art) is signed off
> before coding.

Reuses the **vitrine/paywall cover language** at its real size: one collection per full-width card,
a large overlapping trio of real faces over the collection name / style / count. Scrolls like a
boutique.

**Structure (top → bottom), inside a `LazyColumn`, screen margin 24dp:**

1. **Header row** — the `Wordmark` ("Dialed" + gold period) on the left; the settings gear inside a
   48dp target on the right. *Unchanged.*
2. **`WatchStatusPill`** — unchanged, directly under the wordmark.
3. **Section label** — `labelMedium`, uppercase, `onSurfaceVariant`: **"COLLECTIONS"**.
4. **Collection cards** — one per collection, `lg` (16dp) between them.

**Collection card (vertical, centred):**

| Property | Token / value |
|---|---|
| Container | `surfaceContainerHigh`, radius `DialedRadius.lg` (20dp) |
| Elevation | lvl1 card shadow — `shadow(6dp)`, subtle |
| Padding | `lg` sides (16), `xl` top+bottom (24) |
| Border | 1dp hairline `outlineVariant` |
| Press | scale .96 via `springFast` (F5 micro) |

Card content, top → bottom, centre-aligned:

- **Cover trio** — the collection's first three faces as circular `FaceDial`s, **overlapping**,
  drawn back-to-front so the centre sits on top (via `Modifier.zIndex`):
  - **3+ faces:** flanks **96dp** (zIndex 1) · centre **132dp** (zIndex 2) · overlap −18dp
    (mirrors the paywall's 92/116/92 trio at a slightly larger size).
  - **2 faces** (Aether, Arclight): **120dp** + **108dp**, overlap −16dp.
  - **1 face:** a single **150dp** hero (matches the §1d grid dial).
  - `locked = false`, `status = NONE` — clean previews, never badged (badges are watch-state and
    belong on the face grid, not the cover).
- **Title** — collection name, `headlineSmall` (24/700), `onSurface`. No gold period (that's the
  wordmark's alone).
- **Subtitle** — `labelMedium`, uppercase, **gold** (`primary`) — omitted if empty.
- **Count** — `bodyMedium`, `onSurfaceVariant`: **"N faces"** (or "1 face").

**No paywall, no price, no "N free" pill, no OWNED badge on Home** (design decision D4 + parked map).
Whole card is one tap target → `Collection(id)`.

**Motion:** F5 press on the card; the screen transition into the collection is the F2-flavoured
expand (§5). **States:** the catalog is a bundled constant, so Home never loads async or empties.

## 4. Collection screen (new)

Header + **the existing showroom grid (spec §1d) verbatim**, scoped to one collection.

- **Top app row** — back affordance (48dp target, `ic_chevron`/back arrow) + collection **title**
  (`titleLarge`) with **subtitle** (`labelMedium` uppercase, `onSurfaceVariant`) beneath, and the
  **count** ("N faces"). `WatchStatusPill` under the header (same as Home had) so the user still sees
  their connection while browsing a collection.
- **Face grid** — the **exact** 2-column grid item from today's Home: `FaceDial(size = grid)` +
  `displayName` + `tag` + install-status row (Active / On watch + compact uninstall when installed).
  Tap a dial → `Detail(faceId, fromCollection = id)`.
  - `locked = false` on every dial in this browse surface — see §6. Install **status** badges
    (INSTALLED / ACTIVE) DO show: they reflect real watch state, not entitlement.
- **Footer copy** (expectation-setting, spec/assessment §1, honest, no paywall):
  *"Your watch holds one Dialed face at a time — installing another replaces it."*
  `bodyMedium`, `onSurfaceVariant`, centered, below the grid.

## 5. Navigation & back

`Screen` gains `Collection(collectionId)` and `Detail` gains an optional `fromCollectionId`:

```
Home  --tap card-->        Collection(id)
Collection(id) --tap face--> Detail(faceId, fromCollectionId = id)
```

Back routing (system back + on-screen back both use the same parent rule):

| Current screen | Back goes to |
|---|---|
| Home | (exits app) |
| Collection(id) | Home |
| Detail(faceId, from=id) | Collection(id) |
| Detail(faceId, from=null) | Home *(e.g. a restored deep link, or future entry points)* |
| Paywall / Settings | Home *(unchanged)* |

`ScreenSaver` encodes: `"collection:<id>"` and `"detail:<faceId>"` /
`"detail:<faceId>|from:<id>"`. A restored `Collection` id (or `Detail from`) that no longer matches
any bundled collection falls back to Home — same crash-safety class as the existing `Detail`
`firstOrNull` guard (`DialedApp.kt:114`).

**Transition — F2-flavoured expand.** `AnimatedContent` is direction-aware via `Screen.depth()`
(Home 0 · Collection/Settings 1 · Detail/Paywall 2): going **deeper** the incoming screen
`scaleIn(0.90 → 1, springExpressive) + fadeIn(springStandard)`; going **back** it settles down
(`1.06 → 1`). Reduced motion (`ANIMATOR_DURATION_SCALE == 0`) → a plain 200ms crossfade. This gives
the "expand into the collection / into the face" feel using only screen-level transforms — **no
duplicate shared-element keys**, so it is safe across the Home-cover → Collection-grid → Detail
chain (the same face id can appear as a cover AND in the grid). The **true F2 circular
shared-element morph** (`SharedTransitionLayout`, key `"face/{id}"`) stays in **Phase 2E** because
it needs on-device verification to avoid duplicate-key conflicts — shipping it blind is exactly the
wasted-build risk this process avoids.

## 6. Deferred — owned by the parked map / later phases

These are **intentionally absent** in this slice. Each is stubbed/omitted, and this is the honest
statement of what's missing:

- **Free/paid split, lock badges, per-collection unlock CTA, prices, "N free" pill, OWNED badge.**
  The browse surfaces (Home cards, Collection grid) are **entitlement-agnostic** — no locks, no
  banners, no prices. Which faces are free is a parked decision (audit §11 q3/q4). *The existing
  face-detail entitlement gate is left completely untouched* — release still gates install exactly as
  it does on v0.24.0; debug is still all-unlocked. **Zero new entitlement/paywall code is written.**
- **Coming-soon tiles** (designed-but-unbuilt faces as members) — needs the map + the render pipeline.
- **`config/catalog.json` + `CatalogConfigRepository`** — the runtime data source replaces §2's
  derivation once the map lands; the UI stays as specified here.
- **Entitlement v2** (per-collection set) and **Play Billing** — Phase 3.
- **F2 shared-element motion, collection covers with parallax, unlock celebration** — Phase 2E.

If any decision in this addendum turns out to belong to the parked map, **ask the owner** rather than
guessing — do not invent the map.

## 7. Fidelity checklist (eyeball on the first CI build)

- [ ] Home is a scroll of 10 collection cards; each shows a real cover trio of that collection's faces.
- [ ] Card names/subtitles/counts match §2's table; covers are clean circles (no lock/status badges).
- [ ] No price / unlock / paywall element anywhere on Home or the Collection screen.
- [ ] Tap a card → its faces; tap a face → detail; back returns to the collection, not Home.
- [ ] Install-status badges (Active / On watch) still show on the collection grid and work.
- [ ] Nothing in `:wear` changed except the lockstep version bump.
