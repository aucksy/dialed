# Dialed — Implementation Plan (post-v0.18.0)

**Written:** 2026-07-15, from `docs/ASSESSMENT.md` (read it first — findings C1–L6 are referenced
by id throughout). This plan is written so a **fresh Opus session can execute it cold**, one phase
per chat, without re-deriving context.

---

## 0. Context primer (read once, verbatim facts)

- **What Dialed is:** a native Kotlin/Compose **Watch Face Push marketplace** — phone `:app`
  (storefront + Data-Layer sender) + watch `:wear` (WFP install concierge) + `:wear-common`
  (wire protocol) + 18 generated `facepacks/*` face APK modules built from the `faces/` submodule
  (github.com/aucksy/fablecollection). Repo: **github.com/aucksy/dialed**, local
  `D:\Apps\WearOS Apps\WatchFaces\Dialed App`. Currently at tag **`dialed-v0.22.0`, versionCode 22**
  (both `app/build.gradle.kts` and `wear/build.gradle.kts` — bump BOTH, in lockstep, every phase).
  ⚠ The 18→**43** face count below is the 2C-era number; the facepack modules are now 43, not 18.
- **Platform hard limits (never fight these):** WFP is Wear OS 6 / API 36 only; **slot = 1** (one
  Dialed face on the watch at a time; different-package install = full replace); unattended
  `setWatchFaceAsActive` is a **once-ever platform budget** — always attempt, let the platform
  refuse, never fake success; there is **no API** to put faces in the watch's favorites strip — the
  only gallery lever is a bundled **default watch face**.
- **Read before any UI work (design-fidelity gate):**
  phone — `Dialed Watch-Face Store-handoff/dialed-watch-face-store/project/{HANDOFF.md, Dialed - Design Spec.dc.html}`;
  wear — `Dialed Wear OS app Designs/dialed-watch-face-store/project/{HANDOFF-WATCH.md, Dialed Watch - Design Spec.dc.html}`.
  New screens that the spec predates (collections IA) get a written addendum
  (`docs/DESIGN-ADDENDUM-COLLECTIONS.md`, produced in Phase 2) built from the existing token/
  component system — do not improvise off-system.
- **Read before any WFP/transport work:**
  `Resources/wff-watchface.skill → references/companion-app.md` (the app-layer playbook: set-active
  budget & ownership chain, timeout-every-binder-call, single-transfer lock, exit-to-face, genuine
  Home state, RemoteActivityHelper, signing, wear-compose traps).

### Standing rules (non-negotiable, every phase)

1. **NO local Android builds** (~5.9 GB RAM machine). CI (GitHub Actions) is the only compiler and
   the WFP-token verifier. You CAN validate CI-built face APKs locally (JDK17 + validator jar —
   CLAUDE.md "Local WFP validation loop").
2. **Ship loop per phase:** edit → compile-review (read your own diff as a compiler) →
   **adversarial logic review** → fix → commit → bump versionCode/versionName in BOTH app+wear →
   push main → tag `dialed-vX.Y.Z` → push the tag explicitly (`git push origin <tag>` — lightweight
   tags don't ride `--follow-tags`) → poll CI green → paste BOTH direct APK links
   (`https://github.com/aucksy/dialed/releases/download/<tag>/<tag>-phone.apk` and `…-wear.apk`)
   + a "what to re-test on device" list. Review BEFORE tagging, never after.
3. Git author `simpleapps108@gmail.com`. No `gh` CLI — poll CI via the Actions API using
   `git credential fill` (never print the token).
4. **AGP 9 built-in Kotlin:** never apply `org.jetbrains.kotlin.android` (fatal). Only
   `android-application`/`library` + `compose-compiler`.
5. `:app`+`:wear` sign with the committed `dialed-app-debug.keystore` (stable key = in-place
   upgrades; shared cert pairs them over the Data Layer). Faces sign with `dialed-faces.keystore`
   (MUST stay a different key — WFP rule). `:wear` applicationId MUST stay `com.dialed.app`
   (matches the token `--package_name`).
6. Faces are WFF v2 — **no `isAutoSize`** (v3-only, fails validation). New faces authored at
   **480×480**. Regenerate facepacks with `node tools/gen-facepacks.mjs "<ABSOLUTE-REPO-ROOT>"`
   (must pass the root explicitly — the space in "Dialed App" breaks the script's own resolution).
7. Wire protocol enums are **ordinal-stable — append only, never reorder**
   (`wear-common/.../WearConstants.kt`).
8. **Wrap every WFP binder call in `withTimeoutOrNull` and rethrow `CancellationException`**
   before any broad catch. Never downgrade a real install to FAILED. Never show a false "applied".
9. End every phase by updating `CLAUDE.md`'s phase-status + this plan's Progress table, then hand
   off a fresh-chat kickoff prompt for the next phase.
10. One phase per chat. If a phase turns out to be >1 tag of work, split it and tag each slice.

### Decisions taken as defaults (owner: veto by replying, otherwise these stand)

| # | Decision | Rationale |
|---|---|---|
| D1 | **Per-collection one-time INAPP products** (`unlock_<collection>`, e.g. `unlock_kinetik`) + an optional **all-access bundle** (`unlock_all_faces`, kept from the original design as an upsell). No subscriptions. | Owner: "price of collection"; one-time matches the brand promise already in the paywall copy. |
| D2 | **Prices live in Play Console** (per-product, localized). The app always displays the real `ProductDetails` price. The "admin panel" does NOT set prices — Play is the only place prices can actually be set for INAPP products, so pretending otherwise would be a lie in the UI. | Play Billing constraint; also gives owner per-country pricing for free. |
| D3 | **Remote catalog config = a JSON file in this repo** (`config/catalog.json`), served via the raw GitHub URL (repo is public), fetched by the app with a 24 h cache + the same file **bundled as the offline/first-run fallback**. It controls: which faces are FREE, face→collection grouping, collection→productId, ordering, "new" badges, and kill-switches. **Admin flow v1 = edit the JSON + commit** (from phone: GitHub web editor works). Admin v2 (optional, Phase 2 stretch): a single static `admin.html` in the repo that edits the file via the GitHub API with a PAT. No server, no new infra, versioned + reviewable. | Owner asked for "an easy way, backend admin panel of sorts"; a repo JSON is the cheapest thing that is genuinely easy, auditable, and free. A Cloudflare Worker/KV panel can replace it later without app changes if the URL is kept stable behind one constant. |
| D4 | **Free faces are fully installable without any purchase; Home carries no paywall.** Locked faces open their detail with a per-collection unlock CTA. Debug builds stay all-unlocked. | Owner's explicit trial-first funnel. |
| D5 | **Entitlements are client-enforced** (Play purchase cache + DataStore). No server-side receipt validation at this scale; revisit only if piracy is observed. | Proportionate; the faces are also extractable from any APK regardless. |
| D6 | iOS: out of scope. On-demand asset delivery (Play Asset Delivery): out of scope until APK size demands it (watch it in Phase 7). | Keep the machine simple. |

---

## Phase map (dependency-sliced)

> ## ⚠ TAG NUMBERS ARE NEVER RESERVED — a phase claims its tag at SHIP time = the next free number.
>
> **This rule is written in blood; it has now bitten three times.** The plan originally *reserved* a
> tag per phase. Reality does not respect reservations:
> - `v0.20.0` was held for 2B and never tagged. It is now **permanently unusable** — versionCode is
>   past 20, and an APK at a lower code cannot install over a higher one. The slot is simply dead.
> - `v0.22.0` was reserved for 2D and got spent by an out-of-band faces-only ship (VAKT complications).
> - `v0.23.0` was then reassigned to 2D on 2026-07-17 and spent **the same day** by another
>   out-of-band face fix (Terra-Compass).
>
> So the unshipped phases below **deliberately carry no tag number.** Only shipped rows name a tag.
> Sequence the phases; number them when they ship. An out-of-band fix can then never strand a
> reservation again — which is the only reason this kept happening.

| Phase | Tag | Theme | Depends on |
|---|---|---|---|
| 1 | `dialed-v0.19.0` ✅ | Correctness & hygiene batch (C1 guard, H2, H3, H4, M1–M6, docs refresh) | — |
| **2A** | — (docs) | **Catalog census & curation plan** — every face we have built vs only designed; collections of ≥10; renames/collisions; colour audit. **Owner sign-off gate — still open.** | 1 |
| **2C** | `dialed-v0.21.0` ✅ | **Bundle the 25 built-but-unbundled faces** (Aurum/Halo/Meridian/Terra/Vakt) — 18 → 43 | 2A |
| — | `dialed-v0.22.0` ✅ | **VAKT any-provider complications** (out-of-band, faces-only) | — |
| — | `dialed-v0.23.0` ✅ | **Terra-Compass leaked-note fix** (out-of-band, art-only) | — |
| **2D** | *next free* | **Collections IA + `config/catalog.json` + free faces + coming-soon tiles** (app). ⚠ **BLOCKED** — needs the 2A map signed off (audit §11 q1/q4). | 2A, 2C |
| **2B** | *next free* | **Colour parity → 5+ options on every face** (faces repo) | 2A |
| **2E** | *next free* | **Showcase & motion** — the collection experience (design skills, animation) | 2D |
| **2F** | *rolling* | **Gap builds** — build the designed-but-unbuilt faces each collection still needs | 2A, rolling |
| 3 | *next free* | Play Billing per-collection + entitlement v2 + paywall v2 (code-complete) | 2D |
| 4 | *next free* | Store readiness: release lane, AAB, privacy policy, listing kit (billing e2e here) | 3 |
| 5 | *next free* | Default watch face + system-gallery discoverability (R4 recipe) | 1 (independent) |
| 6 | *next free* | Living gallery — real face animation (R7 E-LITE + WebP delivery) | 1 |
| 8 | *next free* | Wear polish: Home face size, W3 coaching motion, receive thumbnail, optional tile | 1 |

> **Owner steer, 2026-07-17:** pricing and the commercial questions are explicitly **parked to last**
> ("this will be last state") — the priority is **testing and perfecting the existing faces**. Phases 3
> and 4 were already last in the order, so nothing moves; but 2D is now *blocked* (its shelves need the
> parked map), and face-quality work (2B, defect fixes) comes forward.

**Phase 7 (Collection-3 scale-out) is absorbed into 2C** — it was always the same work.
3→4 order is fixed (billing e2e needs 4's Play upload, so 3 ships code-complete and 4 lights it up).
2B is independent of 2C and 2D and can ship whenever 2A is signed off — it is sequenced after 2D
only because 2D is the owner's priority, not because anything blocks it.

---

# PHASE 2 — the catalog program (2A → 2F)

> **Why this is a program, not a phase.** The original Phase 2 assumed "wrap the 18 bundled faces in
> a collections IA". A survey on 2026-07-16 (evidence below, all verified from the working tree) found
> the real job is much larger: 25 built faces aren't in the app at all, ~50+ designed faces aren't
> built, no collection has 10 faces, series names collide, and the colour-option story is broken in a
> way the owner spotted. Doing the IA before the catalog is understood would mean building the shelves
> before knowing the stock.

## Verified facts (2026-07-16 — re-verify, but these are read from disk, not memory)

**The faces submodule (`faces/` → aucksy/fablecollection) holds 43 built faces in 10 series:**

| Series | Built | In the Dialed app? |
|---|---|---|
| Aether | 2 (Ember, Horizon) | ✅ bundled |
| Arclight | 2 (Pulsar, Solstice) | ✅ bundled |
| Kinetik | 5 (Escapement, Metronome, Odometer, Orrery, Turbine) | ✅ bundled |
| Settype | 4 (Counterform, Halftone, Marquee, Masthead) | ✅ bundled |
| Vespera | 5 (Aurum, Meteorite, Noir, Opaline, Salon) | ✅ bundled |
| Aurum | 5 (Baguette, Eclat, Guilloche, Soir, Squelette) | ❌ **built, never bundled** |
| Halo | 5 (Beacon, Ledger, Orbit, Quadrant, Stack) | ❌ **built, never bundled** |
| Meridian | 5 (Calendrier, Classic, PetiteSeconde, Roman, Sector) | ❌ **built, never bundled** |
| Terra | 5 (Altimeter, Compass, Field24, MeridianLine, Solstice) | ❌ **built, never bundled** |
| Vakt | 5 (GT, Meridian, NightWatch, One, Ti) | ❌ **built, never bundled** |

18 bundled + 25 built-but-unbundled = 43. **No series has 10 faces.** The 25 are Collection 3
([[premiumwatchfaces]]); `tools/gen-facepacks.mjs` has an 18-name `BUNDLED_FACES` allowlist that
deliberately excludes them.

**⭐ The colour audit — the owner's suspicion is confirmed, and it's worse than "only 3":**

| Faces | Mechanism | Options |
|---|---|---|
| 16 of the 18 bundled (Aether ×2, Kinetik ×5, Settype ×4, Vespera ×5) | `<ColorConfiguration id="accent">` + `<ColorOption>` | **exactly 3** ❌ |
| Arclight-Solstice | `<ColorConfiguration id="themeColor">` | **5** ✅ |
| **Arclight-Pulsar** | — | **ZERO — no `<UserConfigurations>` at all** ❌❌ |
| All 25 Collection-3 faces | `<ListConfiguration id="theme">` → `t0..t4`, baked per-theme dial art | **5 themes** ✅ |

So the two halves of the catalog use *different theming systems*: Fable series = a colour swatch
picker; Collection 3 = 5 structurally-baked themes. 2A must decide whether they converge (and 2B
must not naively "add 2 more swatches" to a face whose art is baked per theme).

**Designed but NOT built** (all are HTML/spec mockups — **there are no PNG/SVG previews anywhere**):

| Source | Path (under `D:\Apps\WearOS Apps\WatchFaces\`) | Faces |
|---|---|---|
| Fable series showcases | `Fable Collection/FABLE-{Ledger,Armature,Wilder,Afterglow}-Showcase.html` | 4 × 5 = **20** (Ledger: Canon/Paragon/Pica/Brevier/Nonpareil · Armature: Caliper/Sector/Odometer/Orrery/Regulator · Wilder: Ridgeline/Almanac/Planisphere/Bloom/Contour · Afterglow: Arcade/Cathode/Neon/Standby/Segment) |
| Fable proposals | `Fable Collection/proposals/PROPOSAL-{06..10}-*.html` | **5** (Passage, Amplitude, Charter, Patience, Avant) |
| Collection 4 "Atelier IV" | `WatchFaces Collection 4/{faces,series}/*.html` + `handoff/` | **6** (Podium, Airframe, Sextant, Monolith, Strata, Counterpoise) |
| Collection 6 **"The Art of Motion"** | `WatchFace Collection 6 Art of Motion/The Art Of Motion/handoff/` | **15** — ⭐ has a machine-readable `manifest.json` (name/id/territory/mechanic/signature/**palettes** per face), `specs/motion-specs.json`, and JS renderers. WFF v2, 450 canvas. The richest unbuilt source. |
| Collection 5 "Meridian" | `WatchFace Collection 5 Meridian/Premium Wear OS Watch Face Collection-handoff/` | TBD — census it |
| "1st Designs by Fable 5" | `1st Designs by Fable 5/# Premium Smartwatch Face Collection/design_handoff_watch_faces` | TBD — likely the origin of Aether/Kinetik/Settype/Vespera |
| TwelveSixty | `TwelveSixty/` (own repo aucksy/twelvesixty) | **25** — ⚠ a SEPARATE product line (5 collection apps). **Owner decision in 2A: fold into Dialed or leave alone?** |

**⭐ Name collisions that force the rename/recategorise work the owner asked for:**
- **Meridian** = a built series (5 faces) **and** a face (`Vakt-Meridian`) **and** a whole design folder (Collection 5).
- **Aurum** = a built series (5) **and** a face (`Vespera-Aurum`).
- **Ledger** = a designed Fable series **and** a built face (`Halo-Ledger`).
- **Odometer / Orrery** = built `Kinetik-*` faces **and** designed `Armature-*` faces.
- **Solstice** = `Arclight-Solstice` **and** `Terra-Solstice`.
- **Sector** = `Meridian-Sector` **and** a designed `Armature-Sector`.

**Feasibility note for "coming soon" art:** the unbuilt designs are HTML that renders via SVG/CSS/JS.
No exportable image exists. Previews must be **rendered from the HTML with headless Chrome** (this
machine has Chrome; Puppeteer/Playwright browser auto-download is known to fail here — point at the
installed Chrome binary, the [[kirana-promo-reel]] lesson).

---

## Phase 2A — Catalog census & curation plan · docs only, no code · **OWNER SIGN-OFF GATE**

**Goal:** one authoritative, machine-readable inventory of every face we own — built or designed —
plus a curation proposal that turns them into collections of **≥10** each. Nothing else in Phase 2
can be scoped correctly until this exists.

**Deliverables (in the Dialed repo):**
1. **`docs/CATALOG-AUDIT.md`** — the human read: what we have, what's missing, what collides, what
   each collection needs to reach 10, and the recommended structure with reasoning.
2. **`config/catalog-inventory.json`** — the machine read, one record per face:
   `{id, currentSeries, currentName, proposedCollection, proposedName, state: built|designed|planned,
   source: <repo path>, bundled: bool, wffVersion, canvas, themeMechanism: color|list|none,
   themeCount, complicationSlots, motionType, previewAsset|null, designSource: <html path>, notes}`.
   This file becomes the input to 2B/2C/2D — hand-maintained after, generated once here.
3. **A curation proposal** (inside the audit doc) the owner signs off before any code moves:
   - the collection map: **which faces group into which collection, each ≥10** (built + coming-soon
     both count toward the 10 — the owner accepts coming-soon tiles as members);
   - **renames** that resolve every collision above, with the old→new mapping (⚠ a rename of a BUILT
     face changes its package id → new WFP token → treat as a new face; the audit must say so);
   - **gap flags**: per collection, how many faces short of 10 and which designed faces fill it;
   - the **theming decision**: do Fable-series swatch faces and Collection-3 list-theme faces
     converge on one mechanism, and what "5 colour options" means for each (2B depends on this);
   - the **TwelveSixty scope call** and the **Collection 5 / 1st-Designs** census result.

**Method:** this is a fan-out reading job — run it as a Workflow (ultracode is on). One agent per
source folder + one per built series, each returning structured records; then a merge/dedupe pass;
then a curation agent; then an adversarial pass that attacks the proposal (are these collections
coherent? do the renames break tokens? is any face double-counted?). **Do not trust this plan's
tables — verify every count from the files.**

**Acceptance:** every one of the 43 built faces appears exactly once with real per-face facts read
from its `watchface.xml`; every designed face is traced to a real source file; no collection in the
proposal has <10 members; every collision above is resolved; the owner has approved the map.
No version bump (docs only).

**Risk:** scope creep into "let's redesign the collections". The job is to *organise what exists* and
*name the gaps*, not to invent new design languages.

---

## Phase 2B — *next free tag* · Colour parity → 5+ on every face

**Scope (faces submodule):** bring every bundled face to **≥5** theme options, per 2A's theming
decision. Known work: 16 faces at 3 `<ColorOption>`s → 5; **Arclight-Pulsar has no user config at
all** → give it a real theme set; Arclight-Solstice already has 5 (leave). Collection-3's 25 already
have 5 baked themes — **do not touch them** unless 2A says converge.

⚠ **The trap:** a `<ColorConfiguration>` swatch only recolours elements that reference
`[CONFIGURATION.<id>]`. Adding two hex values is cheap; making them *look designed* is not. Each new
swatch must be chosen against the face's actual art (and its AOD twin), not sampled at random —
read `Resources/WFF-Design-Guidelines.md` + the polish skill's §7 review pass, and check every
element that binds the config ref.

**Ship loop:** edit XML in the submodule → validate locally (`java -jar wff-validator.jar 2 <xml>`)
→ push fablecollection → bump the pointer in Dialed → `node tools/gen-facepacks.mjs "<ABS-ROOT>"`
→ CI re-mints all tokens. **No `isAutoSize`** (WFF v3 attr; these are v2).

**Acceptance:** every bundled face exposes ≥5 themes on-wrist; CI validates 18/18 (or 43/43 if 2C
landed first); owner spot-checks 2–3 faces per series for taste, not just count.

---

## Phase 2C — `dialed-v0.21.0` · Bundle the 25 built faces (18 → 43) — ✅ SHIPPED 2026-07-16

> **Outcome.** Store is 18 → **43**. Two of the five scoped items dissolved once verified against the
> files, and two unscoped defects were found and fixed. Full detail in `CLAUDE.md` → "Phase 2C findings
> that contradict the plan". Summary:
>
> | # | Scoped work | What actually happened |
> |---|---|---|
> | 1 | Icon-label rollout to 18 of the 25 | **NO-OP.** Those faces have no hardcoded label of any kind — they render `[COMPLICATION.TEXT]` only and are already swap-safe. Adding icons would be a redesign of 450-canvas art in 34×26–64×26 slots × 6 theme groups, which audit §4 explicitly rules out. Nothing changed in the submodule; its pointer is untouched. |
> | 2 | `BUNDLED_FACES` 18 → 43 + `SERIES_META` ×5 | Done. Also added `FACE_META` (per-face display name + blurb) sourced from the handoff spec — without it the store would have read "Eclat", "MeridianLine", "PetiteSeconde". Verified: all 25 storefront names now match each face's own on-watch `app_name`. |
> | 3 | M2 — derive chips from real slots | Done, and the old guesses were not merely vague but **false** (Aether advertised "Weather" with no weather provider; Kinetik-Orrery advertised "Steps" while having Battery + Sunrise). Chips are now each slot's real `defaultSystemProvider` + a slot count + a derived Always-on. |
> | 4 | Apply the 2A renames | **SKIPPED — the silkscreen check failed for both.** "TERRA SOLSTICE" / "TERRA · MERIDIAN LINE" are painted into every theme's dial art; the spec and the on-watch label agree with the current names. Renaming would contradict the watch on the wrist. |
> | 5 | Watch APK size | Done — CI logs `assets/faces`, tokens, previews and both APK sizes to the job summary. Family B is ~2.1 MB/face vs family A's ~0.36 MB and dominates the payload. |
>
> **Unscoped defects found and fixed:** (a) Home's filter row was a non-scrolling `Row`; at 11 chips half the
> series would have been permanently unreachable — now scrollable (Phase 2D replaces the surface). (b) CI could
> ship a stale generated catalog silently — it now re-runs the generator and fails on drift, and asserts one
> bundled APK + token per facepack, so "43/43" is enforced rather than assumed.
>
> **~~Still open (owner)~~ → CLOSED:** Terra-Compass's leaked `COMPASS · ROSE IS STATIC` dial note
> (audit §11-q5) was approved and **fixed in `dialed-v0.23.0`** (2026-07-17).

**Scope:** the fastest large win in the whole program — 25 faces that already exist, already
validate, and simply aren't in the app.
1. **Icon-label rollout to the 25 first** (owner's standing rule: fix faces to the current recipe
   before shipping them). Reuse the v0.12.0 scripted transform verbatim — scope strictly inside
   `<Complication>` blocks, handle both label shapes, layout-aware STACKED/SIDE placement, the
   value-box per-element match, the tint rules. See [[dialed-app]] for the recipe.
2. Extend `BUNDLED_FACES` in `tools/gen-facepacks.mjs` to the 43 (apply 2A's renames), add
   `SERIES_META` for the 5 new series, regenerate facepacks + previews + `FaceCatalog.kt`.
3. **Fix M2 while here:** `features` chips are series-level guesses (`SERIES_META`) — derive them
   from each face's real `<ComplicationSlot>`s in the generator.
4. Watch the APK size (43 face APKs bundled; log the assets dir size in CI) and the CI validator
   runtime (linear, ~43 × a few seconds).

**Acceptance:** CI validates + tokenises **43/43**; all 43 appear in the app; a Collection-3 face
pushes and installs on-wrist; APK size recorded in the plan.

---

## Phase 2D — *next free tag* · Collections IA + config + free faces + coming-soon

**Scope:** the owner's product model, now with real stock behind it.
- **Home = collection cards, no paywall anywhere on Home.** Tap a card → that collection's faces.
- Per collection: a configured subset is **FREE** and fully installable; the rest show a lock that
  routes to a per-collection unlock CTA (still a debug stub until Phase 3).
- **Coming-soon tiles:** designed-but-unbuilt faces appear as real members with rendered art and a
  "Coming soon" treatment — not installable, never a dead control (no Install button at all).
- `config/catalog.json` + `CatalogConfigRepository` (bundled asset → raw-GitHub refresh, 24 h cache,
  last-good fallback) exactly as the original Phase 2 spec below describes, now also carrying
  `state: built|coming_soon` per face and the collection→product mapping.
- Entitlement v2: boolean → per-collection set (schema only; billing is Phase 3).
- **Coming-soon art pipeline:** `tools/render-designs.mjs` — headless **installed Chrome** →
  screenshot each design HTML → square 480 PNG → `app/src/main/res/drawable-nodpi/coming_<id>.png`.
  Committed as generated assets (deterministic, re-runnable), not fetched at runtime.

**Design gate:** the `.dc.html` specs predate collections → write `docs/DESIGN-ADDENDUM-COLLECTIONS.md`
FIRST, built from the existing tokens/components (see the original Phase-2 detail below).

**Acceptance:** every collection shows ≥10 members (built + coming-soon); free faces install with no
unlock; locked faces route to the CTA; coming-soon tiles are honest and inert; airplane-mode fresh
install renders the bundled config; flipping a face free↔paid in `config/catalog.json` on main
reaches the app after a refresh.

---

## Phase 2E — *next free tag* · Showcase & motion

**Goal (owner's words): "these collections should be showcased with nice design and interactive with
nice animations."** This is the phase where Dialed stops looking like a grid and starts looking like
a boutique.

**Method:** load the design skills — **`frontend-design`** for aesthetic direction and
**`impeccable`** / **`ui-ux-pro-max`** for the interaction/critique pass — and treat the existing
`Dialed - Design Spec.dc.html` tokens + `HANDOFF.md` §5 motion tokens as the constraint, not a
starting-over licence. The brand already has springs (`springExpressive`, `springSettle`), a gold
accent, and named signature moments (F1 living gallery, F2 shared-element expand, F4 unlock sheen).
**Build the collection experience out of those**, don't invent a second design language.

**Candidate scope (pick with the owner; each is independently shippable):**
- **F2 shared-element expand** — collection card → collection screen → face detail, one continuous
  motion (`SharedTransitionLayout`, key `"face/{id}"` / `"collection/{id}"`, `boundsTransform =
  springExpressive`, circular clip throughout). This is the single biggest perceived-quality lever
  and is already specced but never built (assessment M8).
- **Collection cover** — a hero treatment per collection (the cover trio parallax, F5).
- **Coming-soon treatment** — a tasteful "in the workshop" state, not a grey box.
- **Unlock celebration (F4)** — the gold sheen band, ready for Phase 3 to fire.
- Reduced-motion parity for every one of them (`ANIMATOR_DURATION_SCALE == 0` → snap to rest).

**Acceptance:** motion runs at 60fps on the owner's phone (transform-only, no per-frame
recomposition — the `rememberSecondsAngle` rule); reduced-motion honoured; owner signs off on feel.

---

## Phase 2F — *rolling, next free tag per slice* · Gap builds

**Goal:** turn coming-soon into shipped, collection by collection, until each genuinely has 10 built
faces. Ordered by 2A's gap flags and the owner's priority.

**Start with "The Art of Motion" (15 faces)** — it is the best-prepared unbuilt source in the repo
(machine-readable `manifest.json` with per-face palettes + `motion-specs.json` + JS renderers), and
15 faces is a complete collection in one go.

**Per face:** author WFF v2 at **480 canvas**, ≥5 themes (2B's standard), the icon-label complication
recipe from the start, AOD twin, `wff-watchface-polish.skill` §7 review pass before showing it, local
validator PASS, then bundle via 2C's pipeline. **Load `Resources/wff-watchface.skill` +
`wff-watchface-polish.skill` + `WFF-Design-Guidelines.md` every session** (standing rule).

**Acceptance per slice:** validator PASS; owner confirms on-wrist; the collection's coming-soon count
drops. This phase runs alongside 3/4 — it never blocks billing or the store.

---

## Phase 1 — `dialed-v0.19.0` · Correctness & hygiene batch

**Goal:** close every assessment finding that is small, sharp, and protects later phases. Both APKs.
No visual redesign.

**Scope / exact changes:**

1. **C1 guard** — `app/.../MainViewModel.kt`: make `debugToggleEntitlement()` a no-op unless
   `BuildConfig.DEBUG`; in `DialedApp.kt` route Paywall `onPurchase`/`onRestore` and Settings
   `onRestore` through it unchanged (they become inert in release until Phase 3 replaces them).
   Also fix the restore-toggles-OFF quirk: restore should only ever set `true` in debug.
2. **H2** — `MainViewModel`: hold `private var pushJob: Job?`; `startPush` returns early if a push
   is active (or cancels it first — choose cancel-then-start, matching user intent); `dismissPush`
   cancels `pushJob`; ignore late `emit`s by capturing the pushing face id and dropping callbacks
   that no longer match. In `WatchBridge.pushFace` (`app/.../transport/WatchConnection.kt`), add
   `catch (e: CancellationException) { throw e }` above the broad catch (M6), and replace the
   `runCatching`-wrapped `withTimeout` calls in `queryInstalledState`/`uninstallFace` with
   try/catch that rethrows `CancellationException`.
3. **H3** — surface uninstall failure: `MainViewModel.uninstallFace` exposes a transient
   `uninstallError: StateFlow<String?>`; Home/Detail show a snackbar ("Couldn't remove — keep your
   watch nearby") and clear on retry/refresh.
4. **H4 (wire + UX)** — `wear-common/WearConstants.kt`: add `RESPONSE_UNSUPPORTED: Byte = 2`
   (append-only). `wear/.../DialedListenerService.onRequest`: before `tryBegin()`, if
   `!repo.isSupported()` reply `RESPONSE_UNSUPPORTED` (also answer `PATH_QUERY_STATE` with an empty
   snapshot as it already does via the guarded repo). Phone `WatchBridge.pushFace`: map the byte to
   a new `PushStatus.Unsupported`; `PushToWatchSheet` gains the graceful unsupported state the spec
   asks for ("This watch runs an older Wear OS…"); `MainViewModel` maps it to
   `WatchConnection.UNSUPPORTED` so the pill state finally has a producer (fixes half of L1).
5. **M1** — `DialedApp.kt:93` → `firstOrNull { … } ?: return Home` guard.
6. **M3** — phone `AndroidManifest.xml`: `android:launchMode="singleTask"` on MainActivity.
7. **M4** — remove the dead "More" button; wire Settings "Version" to `BuildConfig.VERSION_NAME`;
   Privacy/Licenses rows: hide until Phase 4 provides real targets (leave a TODO referencing
   Phase 4) or wire "Privacy policy" to the URL once it exists; swap Home's settings affordance to
   a proper gear/`ic_more_vert` icon asset with a 48dp target.
8. **M5** — touch targets: `minimumInteractiveComponentSize()`/padding so settings icon, paywall
   close, detail icons, compact uninstall all hit ≥48dp; add `liveRegion = Polite` semantics to
   `WatchStatusPill`.
9. **M6** — delete dead `hasInstalledFace()` (`wear/.../WatchFacePushRepository.kt:113-117`);
   `FaceAssetProvider.stageApk` reuses one staging file name (`push_staged.apk`) or deletes after
   send in `pushFace`'s `finally`.
10. **L1 partial** — delete `WatchStatus.wearOsVersion`/`activeFaceName` and their Settings
    renderers (never populated); delete unused `InstallState.Installing/Error` branches **only if**
    Phase 2/3 won't use them (they will — leave with a comment) → just delete the WatchStatus dead
    fields.
11. **H6 (docs)** — rewrite `CLAUDE.md`'s status/deviation sections to v0.19.0 reality; append
    v0.11→v0.19 rows to `docs/IMPROVEMENTS-PLAN.md`'s Progress table (one line each, tags + dates);
    note in both that `docs/ASSESSMENT.md` + this plan are now the active roadmap.

**Acceptance / verification:**
- CI green; both APKs released with direct links.
- Adversarial review confirms: no new WFP call unbounded, no broad catch swallowing cancellation,
  wire enum append-only, all call-sites of changed signatures updated.
- On-device (owner): push A → dismiss mid-flight → push B immediately → B's sheet shows only B's
  outcome and a comprehensible busy/error message; uninstall with watch out of range → visible
  error; paywall in release-candidate build does NOT unlock (verify via a locally-validated release
  APK if convenient, else code-review-only with the debug APK).
- Zero-context check: Settings shows the real version.

**Risks:** the H4 wire change touches both sides — old-phone/new-watch mixes reply `2` to a phone
that only knows 0/1; the v0.18 phone treats non-`1` as busy ("try again") — acceptable degradation,
note it in the release notes; both APKs ship together anyway.

---

## Phase 2D detail — Collections Home + remote catalog + free faces

> This section is the original Phase-2 spec and remains the **contract for 2D's IA work**. Read it
> together with the 2D summary above (which adds coming-soon tiles + the rendered-art pipeline).
> Where it says "Phase 2", read "Phase 2D".

**Goal:** the owner's marketplace IA. Home = collection cards (no paywall). Collection screen =
that collection's faces; a configured subset is FREE and installable by anyone; the rest show a
lock that routes to the (still-stub) per-collection unlock CTA. All driven by a remote-editable
catalog config with a bundled fallback.

**Design gate:** the `.dc.html` spec predates collections. Before writing UI code, produce
`docs/DESIGN-ADDENDUM-COLLECTIONS.md`: Home collection-card mockup description (reuse the vitrine
card language from spec 1e: cover trio of FaceDials, collection name, face count, "N free" pill,
unlock state), Collection screen (reuse the existing showroom grid 1d verbatim, scoped to one
collection, plus a header), and the locked-face detail CTA (reuse InstallButton.Locked). Stay
inside the existing tokens (`Color.kt`/`Type.kt`/`Dimens.kt`/`Motion.kt`). Owner eyeballs the first
CI build — treat his screenshot feedback as the fidelity check.

**Scope / exact changes:**

1. **Config schema + file** — new `config/catalog.json` (bundled copy at
   `app/src/main/assets/catalog.json`, kept identical by a gen step or by hand):
   ```json
   {
     "schemaVersion": 1,
     "storeMessage": null,
     "collections": [
       {
         "id": "kinetik",
         "title": "Kinetik",
         "subtitle": "Mechanical",
         "productId": "unlock_kinetik",
         "faceOrder": ["kinetik_orrery", "kinetik_turbine", "…"],
         "freeFaces": ["kinetik_orrery"],
         "badge": null
       }
     ],
     "allAccessProductId": "unlock_all_faces"
   }
   ```
   Faces not listed in any collection fall back to their `Face.series` grouping (forward-compat
   for Phase 7). Unknown face ids in config are ignored with a log (config may reference faces the
   installed app doesn't bundle yet — old app + new config must never crash).
2. **`CatalogConfigRepository`** (new, `app/.../data/`): serves `StateFlow<CatalogConfig>` =
   bundled asset immediately → then a network refresh from
   `https://raw.githubusercontent.com/aucksy/dialed/main/config/catalog.json` (constant in one
   place, D3), cached to DataStore with fetch timestamp; refresh at most every 24 h + manual on
   Settings. Parse with `org.json` (no new serialization dep) + schemaVersion tolerance (unknown
   fields ignored; schemaVersion > known → keep last-good). Network errors → keep last-good/bundled.
3. **IA** — `Screen` gains `Collection(collectionId)` (`DialedApp.kt` Saver updated). Home
   (`HomeScreen.kt` rewrite): wordmark + status pill + vertical list of collection cards (cover
   trio, title, count, "N free" pill, OWNED badge when entitled). **No price, no paywall on Home.**
   New `CollectionScreen.kt`: header + the existing 2-col face grid scoped to the collection;
   free faces render unlocked; locked faces keep the lock badge. `FaceDetailScreen`: entitlement
   input becomes per-face (`face is free || collection owned || debug`), locked CTA reads "Unlock
   <Collection>" and routes to the paywall carrying the collectionId.
4. **Entitlement v2 (schema only, billing in Phase 3)** — `EntitlementStore` becomes a
   `Set<String>` of owned collection ids + an `all` marker (DataStore string-set). Migration: old
   `collection_unlocked=true` → `all`. `isFaceUnlocked(face, config, owned)` helper with unit-style
   truth table in a comment. Debug default: all owned.
5. **Copy pass** — expectation-setting copy for slot=1 and first-of-day (assessment §1): collection
   screen footer: "Your watch holds one Dialed face at a time — installing another replaces it."
6. **Admin story v1** — `config/README.md`: exactly how to flip a face free/paid (edit
   `freeFaces`, commit to main, apps pick it up within 24 h or on Settings refresh), how ordering
   and badges work, and the constraint that **prices are edited in Play Console** (D2). Stretch
   (only if the phase is light): `admin/admin.html` static editor using the GitHub contents API
   with a PAT pasted at use time; do not block the tag on it.

**Acceptance:**
- CI green, links posted. Home shows 5 collection cards (Arclight/Kinetik/Aether/Settype/Vespera);
  tapping opens the collection; configured free faces install end-to-end on the watch with **no
  unlock**; locked faces show the per-collection CTA (stub purchase in debug).
- Flip a face free→paid in `config/catalog.json` on main → app reflects it after refresh
  (owner-verifiable with the Settings manual refresh).
- Kill-network test: airplane-mode fresh install renders the bundled config (no blank Home).
- Adversarial review: config parse against malformed/missing/oversized JSON; face-id drift between
  config and catalog; Saver restore of `Screen.Collection` for a removed collection id (fallback
  Home — same class as M1).

**Risks:** IA churn — keep `FaceDial`, `InstallButton`, push sheet, detail untouched except
entitlement inputs, so the WFP path is provably untouched (diff should show zero `:wear` changes).
Free-faces choice: default one free face per collection (first in `faceOrder`) — owner adjusts in
config afterwards, which is the whole point.

---

## Phase 3 — *next free tag* · Play Billing (per-collection) — code-complete

**Goal:** real money. `BillingManager` on billing-ktx 9.1.0 (already a dep), per-collection INAPP
+ optional all-access, restore, acknowledge, price display from `ProductDetails`. Ships
code-complete behind the debug seam; **e2e purchase testing happens in Phase 4** once the app is on
a Play internal-testing track (Play Billing cannot complete purchases for a package Play has never
seen).

**Scope / exact changes:**

1. **`BillingManager`** (new, `app/.../billing/`): connect on first use with retry/backoff;
   `queryProductDetailsAsync` for all productIds from the catalog config (D1);
   `queryPurchasesAsync` on launch + on resume → reconcile entitlements (restore = this same query,
   wired to the existing Restore buttons); `launchBillingFlow(activity, productId)`;
   `PurchasesUpdatedListener` → on PURCHASED: **acknowledge** (mandatory ≤3 days, never consume) →
   write entitlement; handle PENDING (show "pending" state, do not entitle), USER_CANCELED
   (silent), ITEM_ALREADY_OWNED (re-query + entitle). Map productId → collection id via config;
   `unlock_all_faces` → `all`.
2. **EntitlementStore** stays the cache; Play is the source of truth (assessment C1 note): on every
   successful `queryPurchasesAsync`, overwrite the owned-set (union with debug-grants in debug).
3. **Paywall v2** — `PaywallScreen` becomes per-collection: cover trio from that collection, value
   bullets, **real localized price** from `ProductDetails` (placeholder "—" until loaded, never a
   hardcoded string), optional secondary "Get everything" row showing the all-access price.
   Settings "Restore purchase" → `BillingManager.restore()` with success/failure feedback.
4. **Release-build behavior:** with billing present, delete the C1 stub wiring; debug builds keep
   `debugToggleEntitlement` for offline UI testing (guarded since Phase 1).
   ⚠ **Carry-over from Phase 1's review (must not survive this phase):** the v0.19.0 debug gate makes
   the paywall CTA and "Restore" *inert no-ops in a release build* — the safe failure mode (no unlock
   is granted), and unreachable in practice because CI only ships debug builds and Phase 4 is the
   first release lane. But a shown control that does nothing is exactly the defect Phase 1 removed
   elsewhere, so **it is this phase's job to end it**: acceptance requires that in a release build
   every paywall control either performs a real billing action or is not shown at all. No release
   build may ship with the stub still wired (plan order protects this: 3 lands before 4).
5. Unit-testable pure logic (no test infra exists — keep logic in plain functions):
   purchase-state → entitlement-set reducer.

**Acceptance:**
- CI green; adversarial review focuses: acknowledge-or-refund-in-3-days path, PENDING not entitled,
  no entitlement write from an unacknowledged purchase, reconnect-on-SERVICE_DISCONNECTED, price
  never hardcoded, all flows main-safe.
- Debug APK: paywall renders per-collection with "—" prices (no Play on sideload), purchase button
  degrades gracefully (billing unavailable state), debug toggle still works.
- **Owner gates (do in Phase 4):** create the INAPP products in Play Console with ids from
  `config/catalog.json`, set prices, add license testers.

**Risks:** billing-ktx 9.x API shape — verify `queryProductDetailsAsync` signatures against the
official docs during the phase (pinned facts move); do not bump the dep version casually.

---

## Phase 4 — *next free tag* · Store readiness (and billing e2e)

**Goal:** a Play-uploadable, properly-signed release lane, and the owner-facing kit. This is where
Phase 3's billing gets its real end-to-end test.

**Scope:**

1. **Signing:** generate a real upload keystore → repo secrets (`DIALED_UPLOAD_KEYSTORE_B64`,
   `…_PASS`, `…_ALIAS`, `…_KEY_PASS`); `release` signingConfig reads env/secrets (pattern already
   used in ColorCloset/Spends CI). Debug lane and the committed debug keystore stay for the
   sideload releases.
2. **CI:** add a release job (same workflow): `:app:bundleRelease` (AAB) + `:wear:bundleRelease`,
   plus keep the debug APK assets for the owner's sideload loop. Release artifacts uploaded on tag.
3. **Release-build correctness:** verify R8 keeps billing/wearable classes (add keep rules only if
   CI-built release APK actually fails — validate locally with the WFP validator loop +
   `apkanalyzer` if needed); `EntitlementStore` release default = locked (already true).
4. **minSdk decision (H4 long-term):** raise `:wear` minSdk 33→36 for the Play AAB so unsupported
   watches never get the companion (keep the runtime gate as belt-and-braces). Sideload debug APK
   can stay 33 if the owner wants; simplest is to raise both.
5. **Privacy policy** — static page (aucksy.github.io pattern from NotDigest/Spends); truthful:
   no accounts, no analytics, Data Layer transfer phone↔watch only, Play billing. Wire the
   Settings "Privacy policy" row (un-hide from Phase 1) + "Open-source licenses" via
   `com.google.android.gms.oss-licenses` **or** a hand-rolled static licenses screen (prefer
   hand-rolled: zero new plugin surface).
6. **Listing kit** in `play/`: 512 icon, feature graphic, phone + watch screenshots checklist,
   short/long description, data-safety answers — follow the structure of Spends' `play/` kit.
7. **Owner-gated runbook** (`play/SUBMISSION.md`): verify `com.dialed.app` availability (H5 —
   if taken, the applicationId change ripples into token `--package_name`, wear applicationId, and
   ALL 18 face tokens: document the blast radius prominently), create app, upload AAB to internal
   testing, create the INAPP products (ids from config), add license testers, **then** e2e-test
   purchases on-device and confirm entitlements restore on reinstall.

**Acceptance:** CI green producing signed AABs + the usual debug APKs; owner completes the runbook;
a real purchase unlocks a collection; reinstall restores it; refund (test) removes it on next
launch reconcile.

**Risks:** applicationId collision (checked first, before any other step); Play review flags for
`WAKE_LOCK`/wearable perms are standard-approvable.

---

## Phase 5 — *next free tag* · Default watch face + gallery discoverability

**Goal:** execute the R4 recipe (`docs/research/R4-carousel-slot-model.md` — execution-ready):
a branded "Dialed" default face seeds the system watch-face gallery at install time, and
uninstall reverts to it instead of emptying the slot.

**Scope (follow R4's RECIPE section verbatim, summarized):**
1. Author a simple, instantly-recognizable **Dialed-branded WFF v2 face at 480×480** (dial mark +
   wordmark + time; run the wff-watchface-polish §7 pass) as `faces-default/` in THIS repo (not the
   submodule — it's Dialed-brand, not Fable), packaged like a facepack
   (`com.dialed.app.watchfacepush.dialed.default`), faces keystore.
2. CI: build it, validate with the same validator, and (new step) copy the APK to
   `wear/src/main/assets/default_watchface.apk` + emit the token into a generated res file wired as
   an extra res dir; un-comment the manifest meta-data
   (`wear/src/main/AndroidManifest.xml:29-32` placeholder already exists):
   `com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN`.
3. `WatchFacePushRepository`: add `revertToDefault()` (an `updateWatchFace` with the bundled
   default APK/token, timeout-bounded); `DialedListenerService` `PATH_UNINSTALL` handler calls it
   instead of `removeByPackage` (phone copy changes from "Remove from watch" to "Reset watch to the
   Dialed face" — keep an explicit true-remove in Settings if the owner wants one).
4. Honest UX copy from R4 §5-A on phone detail + wear Home.
5. On-device verification closes R4's two `[UNVERIFIED]`s: does the default consume the slot
   (`remainingSlotCount`), and which surface (favorites strip vs "+" gallery) shows the tile —
   record both answers in R4.

**Acceptance:** fresh wear install (clean) shows a Dialed tile in the system gallery with no push;
uninstall-from-phone leaves the default face installed; CI 19/19 faces validated.
**Risks:** the slot-consumption unknown — if the default occupies the slot, the "first push" flow
must take the update path (it already does: `installOrUpdate` checks `remainingSlotCount`).

---

## Phase 6 — *next free tag* · Living gallery (R7 phase 1: E-LITE + WebP delivery)

**Goal:** real, per-face motion in the phone app — no fake hands — per
`docs/research/R7-face-animation-in-app.md` Phase-1 scope.

**Scope (R7 §4 Phase 1, plus the delivery gates):**
1. Add Coil 3 (`coil-compose` + `coil-gif`, register `AnimatedImageDecoder`).
2. For the ~8 analog/sweep faces + Pulsar: bake hands-off base plates + real hand sprites
   (submodule `collection3-tools/gen/bake.mjs` `!LIVE_KINDS` path where available; else a one-off
   bake from the face's own layered sources); bundle as drawables; extend `FaceDial` to composite
   base + rotate the REAL hand sprite about its true pivot using the existing `rememberSecondsAngle`
   loop (assessment L2 hook) — detail hero + on-screen grid cells only, reduce-motion freezes.
3. Digital faces: live `TimeText`-style overlay only where cheap; minute-cadence faces stay on
   `preview.png` (R7's census: 9/18 have sub-minute motion — don't animate what doesn't move).
4. Metronome (sprite): defer the emulator-capture WebP to a 6b slice unless trivially cheap; the
   emulator CI job (R7 Approach D, API 35 wear image, scrcpy/screencap NOT screenrecord) is its own
   tag if pursued.
**Acceptance:** detail hero of Solstice/Turbine/etc. moves with the face's own hand art; grid stays
calm (visible-cells only); reduce-motion static; no per-frame recomposition (transform-only rule).
**Risks:** pivot fidelity per face — verify against the face XML pivot values; battery — gate to
STARTED lifecycle as `rememberSecondsAngle` already does.

---

## ~~Phase 7~~ → **ABSORBED INTO PHASE 2C** · Collection 3 scale-out (25 faces)

> **This is no longer a separate phase.** The 2026-07-16 census established that these 25 faces are
> already built and merely unbundled, which makes them the cheapest large win in the program — so the
> work moved forward into **Phase 2C** (`dialed-v0.21.0`). The detail below is unchanged and is still
> 2C's contract; read it there.

**Goal:** grow the catalog 18→43 with the premium Collection 3 (Vakt/Meridian/Terra/Halo/Aurum) —
the collections IA from Phase 2D exists precisely to absorb this.

**Scope:**
1. **Icon-label rollout to the 25 faces first** (owner standing rule: fix faces to current recipe
   before shipping them) — reuse the v0.12.0 scripted transform (`apply-icon-labels.mjs` pattern;
   scope inside `<Complication>` blocks only, the two label shapes, layout-aware STACKED/SIDE,
   value-box per-element match, tint rules), validate all 25 locally, ship in the **fablecollection**
   repo per its rules.
2. Dialed: extend `BUNDLED_FACES` (+ regenerate facepacks with the absolute-root invocation),
   extend `config/catalog.json` with the 5 new collections + free picks + product ids; Play Console
   products (owner). `SERIES_META` gains the 5 series (and per-face features come from the M2 fix).
3. **Watch the APK size:** 43 faces bundled — if the phone APK crosses ~150 MB the sideload story
   is fine but Play AAB may warrant Play Asset Delivery; measure first (CI logs the asset dir
   size), decide then (D6).
**Acceptance:** CI 43/43 validated + tokenized; new collections browsable/purchasable; existing
users see them after a config refresh (badge "NEW").
**Risks:** validator time ×43 in CI (acceptable, linear); memory of full-res previews (assessment
L3 — Coil from Phase 6 mitigates; make the grid use size-aware requests here at the latest).

---

## Phase 8 — *next free tag* · Wear polish batch

**Goal:** the remaining wear-side comfort items, sized by owner feedback.

**Scope:** Home face-size bump 73→~88dp with rebalanced fractions (deliberate spec deviation —
owner has asked twice; verify at 192dp AND 225dp, update the design addendum); W3 coaching loop
(animated 6 s loop ×3 then Replay, reduced-motion keeps the static cards); receive-flow thumbnail
(phone downsizes the preview to ≤64px PNG and appends it to the initiate payload — keep the message
comfortably under the Data-Layer message size limit; wear shows it materializing per W1); optional
DialedTile (spec 1p) if the owner wants a tile surface.
**Acceptance:** owner confirms the Home proportions on-wrist; receive shows the actual incoming
face; coaching plays.

---

## Progress table (keep live — update as each phase tags)

| Phase | Tag | State | Notes |
|---|---|---|---|
| 1 Hygiene | v0.19.0 | ✅ **SHIPPED** 2026-07-16 (CI run 29472817925 green, 18/18 faces validated; commit `0ed9dfe`) | C1 paywall debug-gate (+ restore only ever grants) · H2 push-token guard + `CancellationException` rethrow + `NonCancellable` cleanup · H3 uninstall-error snackbar · H4 `RESPONSE_UNSUPPORTED=2` + `!unsupported` query sentinel → honest sheet state + the UNSUPPORTED pill finally has a producer (`QueryStateResult.supported`) · M1 `firstOrNull` detail guard · M3 phone `singleTask` · M4 dead "More"/no-op rows removed + `BuildConfig.VERSION_NAME` + real gear icon · M5 48dp targets (incl. the theme selector) + pill live region · M6 dead `hasInstalledFace()` + per-transfer staged APK with sweep · L1 dead `WatchStatus` fields · H6 CLAUDE.md + this table refreshed. **7-lens adversarial review (43 agents, 3 refuters/finding) caught 2 real self-inflicted bugs pre-tag: (a) BLOCKER — the new `catch (CancellationException)` swallowed the setup RPC's `withTimeout` (`TimeoutCancellationException` IS a `CancellationException`) → the sheet hung on "Sending…" forever with no error and no Retry; fixed by `withTimeoutOrNull` + an explicit emit. (b) HIGH — cancelling the push job on dismiss abandoned a transfer the watch was still installing (the phone cannot stop the Data Layer anyway), so the face landed on the wrist while the phone still read "Install to watch"; fixed by making the token the whole guard, never cancelling, and recording watch FACTS (Done→refresh, Unsupported) above the token gate so only the sheet write is gated.** Also fixed off-review: the M3 default Snackbar was painting on unmapped `inverse*` roles (the one off-system surface) and the 48dp gear target visibly indented the glyph past the 24dp margin. |
| **2A Census & curation** | — (docs) | 🟡 **DOCS DELIVERED 2026-07-16 — awaiting owner sign-off** | `docs/CATALOG-AUDIT.md` + `config/catalog-inventory.json` (102-agent census + deterministic merge + judged curation panel + 3-refuter adversarial pass, every count re-verified from disk). **Findings that revise this plan's hypotheses:** 192 distinct designs total (43 built / 149 designed-unbuilt — plan guessed ~46 designed, off ~3×); ⭐**Collection 5 "Meridian" = 70 faces with 170 finished SVGs**, not 5 sketches (nearly half the backlog); Collection 4 = 32 not 6; the "Second Movement" proposals (25) are the owner-REJECTED direction — excluded; TwelveSixty already folded (16 of the 18 bundled ARE its designs) → archive, harvest only FIELD-Splitline. **Colour audit corrected:** not "16 at 3" but 16-at-3 + Pulsar-at-ZERO + **9 placed coming-soon designs also at 3** (FIELD×5, Aether ×3, Settype Spoken); the 25 unbundled already have 5 baked themes. **Theming decision: do NOT converge** — bring the 18 to 5 swatches (8 rich = cheap, 8 low-wiring = ½-day each, Pulsar = owner call), leave the 25 baked-theme faces alone. **Map: 7 collections all ≥10** (Vespera 10/10 built = pilot; Vakt 15; Atelier/Kinetik/Halo 5+5; Settype/Arclight 4+6), all renames display-only/token-safe. Open questions for owner in the audit §11. |
| **2C Bundle the 25** | v0.21.0 | ✅ **SHIPPED** 2026-07-16 (commit `140c263`) | Store **18 → 43**. Two of five scoped items dissolved on contact with the files (the icon-label rollout was a **NO-OP** — those faces have no hardcoded label at all; the two Terra renames were **SKIPPED** — the names are painted into the dial art, and the spec + on-watch `app_name` agree with them). Delivered: `BUNDLED_FACES` 18→43 + `SERIES_META` ×5 + a new `FACE_META` (without it the store read "Eclat"/"MeridianLine"/"PetiteSeconde"); **M2 fixed** — chips now derive from each face's real `<ComplicationSlot>`s, and the old series-level guesses were not vague but *false* (Aether advertised "Weather" with no weather provider). **Two unscoped defects found and fixed:** Home's filter row was a non-scrolling `Row` that would have made half the store unfilterable at 11 chips (now scrollable; **2D replaces the surface**), and CI could silently ship a stale generated catalog (now re-runs the generator, fails on drift, asserts 43/43 + one APK+token per facepack). Full detail: `CLAUDE.md` → "Phase 2C findings that contradict the plan". **Still open (owner):** Terra-Compass's leaked `COMPASS · ROSE IS STATIC` dial note (audit §11-q5). |
| **— VAKT complications** | v0.22.0 | ✅ **SHIPPED** 2026-07-17 (commit `a1d8dc1`) | **Out-of-band, not a planned phase — this is the ship that took 2D's reserved number** (see the renumbering note under the Phase map). Faces-only, submodule bump, **zero app code**. Assigning any provider to a VAKT register used to paint an opaque flat disc over the machined dial; a slot is now a **FRAME** (permanent dial art) + swappable **CONTENT**, with the empty-state scale/needle in a `<Complication type="EMPTY">` block the platform swaps out. All 8 types render. VAKT is the **first WFF v2** face family (GOAL_PROGRESS + WEIGHTED_ELEMENTS); CI reads the version per-face from the manifest so this needed no CI change. ⚠ The facepack template hardcodes `minSdk = 33` while a v2 face wants 34 — inert (WFP is Wear OS 6 only) but revisit if faces are ever sold standalone. Detail: `faces/collection3-tools/VAKT-COMPLICATIONS-PLAN.md` §7. |
| **— Terra-Compass leaked note** | v0.23.0 | ✅ **SHIPPED** 2026-07-17 | **Out-of-band, art-only** (audit §11-q5 closed). The dial silkscreen read `COMPASS · ROSE IS STATIC` on all five themes — the second half was a designer's note about the concept (WFF has no heading sensor, so the rose is honest decoration) that belongs in the spec's `concept` field, where it already sits verbatim. Now reads `COMPASS`, matching the sibling convention (`TERRA` + face name; Terra-MeridianLine uses identical geometry). **Swept every text literal in all 5 spec files — this was the only leaked note**; the other all-caps strings are real silkscreen (`AUTOMATIC · 41`, `TITANIUM`, `INSTRUMENT`) or units (`PWR`, `SEC`, `STEPS ×1000`). Scope: `spec/cat-c.js` 1 line + 8 re-baked PNGs; `watchface.xml`/`strings.xml`/`watch_face_info.xml` byte-identical after the re-bake ⇒ **no schema or validator surface**. Re-baked with `node gen/build-all.mjs WF-C3`. |
| **2D Collections IA** | *next free* | 🚧 **BLOCKED** on the 2A map | Home = collection cards, no paywall; free faces; `config/catalog.json`; coming-soon tiles + Chrome-rendered art. Keep the `:wear` diff at **zero**. ⚠ **Cannot start**: the shelves are the collection map, and the map's shape questions (audit §11 **q1** Vakt 15-vs-10, **q4** card shows total vs split) were parked by the owner 2026-07-17. Pricing is *not* a blocker (the paywall is a stub until Phase 3) — the **map** is. |
| 2B Colour parity → 5 | *next free* | ⬜ **← the live candidate** | 16 bundled faces sit at 3 ColorOptions; **Arclight-Pulsar has ZERO** (owner call, audit §11-q2, parked); Collection-3's 25 already have 5 baked themes (leave). Fits the owner's "perfect the faces first" steer; needs no parked decision except Pulsar's. |
| 2E Showcase & motion | *next free* | ⬜ | F2 shared-element expand + collection covers; `frontend-design`/`impeccable` skills. Depends on 2D. |
| 2F Gap builds | *rolling* | ⬜ | Each slice takes the next free tag. Start with "The Art of Motion" (15, best-prepared handoff). |
| 3 Billing | *next free* | ⬜ | Must end the release paywall no-op (see Phase 3 §4). Owner: pricing is "the last state". |
| 4 Store | *next free* | ⬜ | owner gates: Play account/products/upload; verify `com.dialed.app` is free FIRST |
| 5 Default face | *next free* | ⬜ | closes R4 UNVERIFIEDs on device |
| 6 Living gallery | *next free* | ⬜ | 6b = emulator WebP, optional |
| ~~7 Collection 3~~ | — | ➡️ **MOVED** | Absorbed into 2C (the faces are already built — it's the cheapest big win). |
| 8 Wear polish | *next free* | ⬜ | face-size = owner call |

---

## Kickoff prompt for the next session (Phase 2A) — paste verbatim

```
You're executing PHASE 2A of the Dialed implementation plan: the CATALOG CENSUS & CURATION PLAN.
This is a research + planning phase. Produce documents, not features. Do NOT write app code, do NOT
touch any watchface.xml, do NOT tag a release.

Repo: D:\Apps\WearOS Apps\WatchFaces\Dialed App (github.com/aucksy/dialed), at dialed-v0.19.0.
Work in Fable mode: evidence before opinion, adversarial self-check, calibrated reporting.
Ultracode is on — run the census as a Workflow (fan out; token cost is not a constraint).

READ FIRST (authoritative — do not assess from memory):
1. docs/IMPLEMENTATION-PLAN.md → "PHASE 2 — the catalog program", especially "Verified facts
   (2026-07-16)" and "Phase 2A". That section has starting coordinates for every source folder.
   TREAT ITS TABLES AS A HYPOTHESIS TO VERIFY, NOT AS TRUTH — re-count everything from the files.
2. CLAUDE.md (architecture, WFP gotchas, ship loop) + docs/ASSESSMENT.md (M2 = the features-chips
   defect you will fix in 2C; note what the catalog currently is).
3. Resources/wff-watchface.skill + Resources/wff-watchface-polish.skill + WFF-Design-Guidelines.md
   (standing rule: load these every faces session).

THE OWNER'S GOAL, in his words:
- The app must show ALL the watch faces we've built — including the ones already on GitHub but not
  in the app — not just the 18 bundled today.
- "Each collection inside the app should have at least 10 watch faces." Understand the whole
  catalog, RECATEGORIZE and RENAME as needed, and where a collection genuinely can't reach 10,
  FLAG IT so we can design more to complete it.
- Any design that isn't built yet: show its image in the app marked "coming soon" (so coming-soon
  faces count toward the 10).
- Every face should have at least 5 colour options — he noticed the built ones only have 3.

YOUR DELIVERABLES (commit to the Dialed repo; docs only):
1. docs/CATALOG-AUDIT.md — the human read.
2. config/catalog-inventory.json — one record per face, schema in the plan's Phase 2A.
3. A CURATION PROPOSAL inside the audit, for owner sign-off, covering: the collection map (each
   ≥10, built + coming-soon); every rename with old→new (⚠ renaming a BUILT face changes its
   package id → new WFP token → call that out); per-collection gap counts + which designed faces
   fill them; the THEMING DECISION (Fable series use <ColorConfiguration> swatches, Collection 3
   uses <ListConfiguration> 5 baked themes — do they converge? what does "5 colour options" mean
   for each?); the TwelveSixty scope call; and the Collection 5 / "1st Designs" census result.

METHOD (fan out, then verify):
- One agent per source: each of the 10 built series (read the real watchface.xml: WFF version,
  canvas, theme mechanism + count, complication slots, motion type, preview asset), and each design
  folder (Fable showcases, proposals, Collection 4 Atelier IV, Collection 5 Meridian, Collection 6
  "The Art of Motion" — that one has a machine-readable handoff/manifest.json with per-face
  palettes, start there — "1st Designs by Fable 5", TwelveSixty).
- Then merge/dedupe into one inventory. Then a curation agent proposes the collection map.
- Then an ADVERSARIAL pass that attacks the proposal: are these collections coherent to a buyer, or
  just bins? Does any rename break a token or a package id? Is any face double-counted or missing?
  Does every collection really reach 10? Are the collisions actually resolved?

KNOWN COLLISIONS you must resolve (verify, then fix in the proposal): Meridian is a built series AND
a face (Vakt-Meridian) AND a design folder; Aurum is a series AND a face (Vespera-Aurum); Ledger is
a designed series AND a face (Halo-Ledger); Odometer/Orrery exist as both Kinetik (built) and
Armature (designed); Solstice is both Arclight and Terra; Sector is both Meridian and Armature.

FACTS ALREADY ESTABLISHED (verify, don't re-derive): 43 built faces in faces/ (10 series); 18
bundled, 25 built-but-unbundled (Aurum/Halo/Meridian/Terra/Vakt); no series has 10; 16 of the 18
bundled have exactly 3 <ColorOption>s, Arclight-Solstice has 5, ARCLIGHT-PULSAR HAS NO USER CONFIG
AT ALL; the 25 unbundled use <ListConfiguration id="theme"> with t0..t4 = 5 themes each; unbuilt
designs exist ONLY as HTML (no PNG/SVG anywhere) so coming-soon art must be rendered with headless
INSTALLED Chrome (browser auto-download fails on this machine).

RULES: docs only this phase (no version bump, no tag, no code). Git author simpleapps108@gmail.com.
Don't invent new design languages — organise what exists and name the gaps. Report in PLAIN ENGLISH
to the owner (he is not a developer): lead with what he has, what's missing, and what you recommend;
put the detail in the docs, not the chat.

END BY: committing the docs, then presenting the curation proposal to the owner for sign-off — a
short, plain-English summary of the recommended collections, what each is missing, and the decisions
you need from him (theming convergence, TwelveSixty, any rename he may dislike). Then hand off the
kickoff prompt for whichever of 2B (colour parity) or 2C (bundle the 25) he wants first.
```
