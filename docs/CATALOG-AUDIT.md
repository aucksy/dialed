# Dialed — Catalog Audit & Curation Proposal (Phase 2A)

**Date:** 2026-07-16 · **Status:** DOCS ONLY — no code, no version bump, no tag. **Awaiting owner sign-off.**
**Companion file:** `config/catalog-inventory.json` (one record per face, the machine-readable version of everything below).
**Method:** a 102-agent census (one reader per built series + one per design folder), a deterministic merge, a 3-way curation panel judged and synthesised, then an adversarial review that attacked the result with 3 independent fact-checkers per finding. Every number here was re-counted from the actual files on disk, not taken from the plan or from memory.

---

## 1. The short version (read this first)

**What you have is far bigger than the app shows — or than we thought.**

- The app today shows **18** watch faces.
- We have actually **built 43** faces. So **25 finished faces are sitting on GitHub, not in the app.** (Phase 2C puts them in.)
- On top of that, we have **149 more faces that are designed but never built** — real, detailed designs, just not turned into working faces yet.
- **Grand total: 192 distinct watch-face designs** across the whole workshop.

**The biggest surprise:** one folder we had written down as "5 sketches, look into it later" (Collection 5, "Meridian") turned out to be **70 finished face designs with ready-made pictures for every one** — nearly half of the entire unbuilt backlog, and the single most valuable asset in the estate. More on that in §9.

**Your three requests, answered:**

1. **"Show everything we've built."** Yes — the plan already does this in Phase 2C (the 25 unbundled faces). This audit confirms the exact 25 and how to group them.
2. **"Every collection should have at least 10 faces."** Achievable **today** with a 7-collection map (below). Every collection reaches 10+ using faces we've built plus "coming soon" tiles for designs we haven't. **But be warned:** only **one** of the seven (Vespera) is 10-out-of-10 *built* right now. The others launch part-built (as low as 4 of 10) and fill up as we build. That is not a filing problem, it's a real decision about what "10 faces" means on a price tag — see §11, question 3.
3. **"Every face should have at least 5 colours — the built ones only have 3."** You were right, and it's a bit more nuanced than "3":
   - The **25 unbundled faces already have 5 colours each** (they were built to a newer standard). No work needed.
   - Of the **18 in the app now:** 1 already has 5, **16 have 3**, and **1 (Arclight-Pulsar) has no colour choice at all.** That's the Phase 2B job.
   - ⚠️ **The honest catch:** on several of those 16, the "3 colours" barely change anything — the colour only repaints one small element. Giving them a real 5-colour choice means a bit of design work per face, not just typing two more hex codes. Detail in §4.

**What I recommend you decide now (the sign-off):** approve the 7-collection map in §5, the renames in §6, and the theming plan in §4; then answer the handful of genuine judgement calls in §11 (mainly: how to price/present the collections that launch part-built). Once you sign off, Phase 2B (colours) and 2C (show the 25) can both start.

---

## 2. The numbers, verified

| | Count | Notes |
|---|---:|---|
| **Faces we've BUILT** | **43** | Real working faces (`watchface.xml` on disk), 10 series |
| — of those, in the app today | 18 | The bundled set |
| — of those, built but NOT in the app | **25** | All of "Collection 3": Vakt, Meridian, Terra, Halo, Aurum — 5 each |
| **Faces DESIGNED but not built** | **149** | Detailed designs (HTML/JSON/SVG), no working face yet |
| **TOTAL distinct designs** | **192** | Every face-shaped thing in the whole workshop |

**Where the old plan's guesses were off:** the built side (43 / 18 / 25) was exactly right. The designed side was under-counted roughly three-fold — the plan guessed ~46 designs, the real number is 149. The reason is simple: the plan counted *files*, and almost every "file" is a folder of 5 faces. The two big unknowns resolved as **Collection 5 = 70 faces** (not 5) and **1st Designs = 25 faces, 16 already built, 9 still to build**.

---

## 3. What each built series is (so the grouping makes sense)

The 43 built faces come in two families that were made at different times to different standards:

**Family A — the 18 in the app now** ("Fable" faces: Aether, Arclight, Kinetik, Settype, Vespera). Authored at the larger 480-pixel canvas, WFF v2 for two of them. Colour = a **swatch picker** (pick an accent colour).

**Family B — the 25 built-but-hidden** ("Collection 3": Vakt, Meridian, Terra, Halo, Aurum). Authored at 450 pixels, WFF v1. Colour = **5 fully-baked themes** (each theme is a completely different-looking watch, not just a tint). These are the newer, richer faces — and they already meet your 5-colour rule.

The one-line "what unifies each series" (used to build the collection map):

| Series | Built | The idea |
|---|---:|---|
| Aether | 2 | Atmospheric light — glows, horizons, sky washes |
| Arclight | 2 | A radiant solar dial with a luminous sweep |
| Kinetik | 5 | Visible machinery — escapements, orreries, turbines that really move |
| Settype | 4 | Type *is* the watch — editorial letterforms |
| Vespera | 5 | Evening dress dials, gold and shadow |
| Vakt | 5 | Machined tool-watch instruments (registers, lume, skeleton) |
| Meridian | 5 | Bauhaus dress restraint — make the smartwatch disappear |
| Terra | 5 | Field/expedition instruments — compass, altimeter, 24-hour |
| Halo | 5 | Data-first — goal rings, gauges, glanceable |
| Aurum | 5 | Openwork haute-horlogerie — guilloché, skeleton, jewellery |

---

## 4. The colour story, and the theming decision ⭐

**This is the decision Phase 2B depends on, so here it is in full.**

**The catch you spotted is real, and it splits three ways:**

- **The 25 built-but-hidden faces already have 5 colours each.** They use baked-in themes. **Leave them exactly as they are** — and treat 5 as the *ceiling*, not a floor: a 6th theme means drawing a whole new set of dial art for 25 faces. Don't.
- **The 18 in the app** use a colour swatch. Adding colours costs nothing in artwork — but a swatch only recolours the parts of the face that are *wired* to it. When I counted the wiring, the 16 three-colour faces split into two groups:
  - **8 faces where two more colours will genuinely read as a new look** (their colour is wired into many elements). ~15 min each: add two swatches, rebuild.
  - **8 faces where the colour barely touches anything** (e.g. Kinetik-Orrery's colour repaints *one* element — the sun; Aether-Horizon's "barely does anything" by its own note). For these, giving a buyer a real 5-colour choice means wiring the colour into the dial/hands first — about half a day each, and each goes back through the design review.
- **Arclight-Pulsar has no colour choice at all** — no customisation of any kind. It needs a colour system built from scratch (about a day), *or* we ship it honestly as a one-look face, *or* we pull it. (Owner call — §11, question 2.)

**Do the two halves "converge" onto one system?** **No — and they shouldn't.** They're two legitimate mechanisms for the same promise ("5 colours"). Forcing them together would mean either re-drawing art for 18 faces or re-authoring 25 — huge cost, no buyer benefit. The buyer sees "5 colours" either way. **Recommendation: keep both, bring the 18 up to 5, leave the 25 alone.**

**One more honest catch the review caught:** **9 of the "coming soon" designs we're about to place are specced at only 3 colours too** (the 5 FIELD designs + Aether Tidal/Nimbus/Aurora + Settype Spoken). So when we *build* those, they each need 2 more colourways drawn first — that colour work lands in the Arclight and Settype and Halo collections, not just in the existing 16. Flagging it now so it isn't a surprise later.

---

## 5. The recommended collection map ⭐ (needs your sign-off)

Seven collections, every one at 10+, all 43 built faces shown, no leftover bin. "Built" = works today; "Coming soon" = a designed face shown with its picture, marked coming-soon, not installable.

| Collection | Built | Coming soon | Total | The pitch (what a buyer sees) |
|---|---:|---:|---:|---|
| **Vespera** — *Dress for the evening* | **10** | 0 | **10** | Ten finished dress dials. **The only 100%-ready collection** — your paid-unlock pilot. |
| **Vakt** — *Tools, not toys* | **10** | 5 | **15** | Machined instruments + field dials + a dive set. Biggest collection. |
| **Atelier** — *The movement is the dial* | 5 | 5 | 10 | Openwork / skeleton haute-horlogerie. |
| **Kinetik** — *Mechanisms you can watch work* | 5 | 5 | 10 | Time as visible machinery. |
| **Halo** — *Your day, at a glance* | 5 | 5 | 10 | Data-first: rings, gauges, race clocks. |
| **Settype** — *Time, set in type* | 4 | 6 | 10 | The typographer's shelf. |
| **Arclight** — *Sky, light and water* | 4 | 6 | 10 | Weather and sky as a dial. |
| **TOTAL** | **43** | **32** | **75** | All 43 built faces placed; 32 coming-soon tiles fill the gaps. |

**How each one reaches 10** (the merges and fills that do the work):

- **Vespera (10, all built):** the built Vespera 5 + the built **Meridian 5** merged in. Both are dress dials — coherent, and it retires the confusing "Meridian" series name for free.
- **Vakt (15 built+cs):** built Vakt 5 + built **Terra 5** (same "instrument" buyer) = 10 built, + 5 designed **Trench** dive faces coming soon.
- **Atelier (10):** built **Aurum 5** (renamed shelf) + 5 designed **Calibre** faces coming soon.
- **Kinetik (10):** built Kinetik 5 + 5 designed **Counterpoise** faces coming soon.
- **Halo (10):** built Halo 5 + the 5 designed **FIELD** faces coming soon — the highest-value unbuilt work in the estate (Splitline is Play screenshot #1 and the source of the app icon's lime; it's ~90% built already in an old repo — see §8).
- **Settype (10):** built Settype 4 + the one missing Settype face (**Spoken**) + 5 designed **Ledger** typography faces.
- **Arclight (10):** built Arclight 2 + built **Aether 2** (merged — both are "sky/light") + their own 6 unbuilt siblings (Arclight Eclipse/Transit/Tideline, Aether Tidal/Nimbus/Aurora).

The full face-by-face membership is in `config/catalog-inventory.json` (`proposedCollection` on every record).

---

## 6. Renames — and which ones cost anything ⭐

**The key fact for you:** a face's storefront **display name** is separate from its hidden package id. **Renaming what a face is *called* in the app is free and safe** — it never re-installs anything on anyone's watch. (Verified in the code: the package id comes from the folder name, and the storefront reads a separate `displayName`.) The only expensive kind of rename is one that changes a *folder name*, and **this proposal has none of those.**

| What changes | From → To | Kind | Costs a re-install? |
|---|---|---|---|
| A face's shelf label | *Aurum* series → **Atelier** | Display | **No** |
| Retire a confusing series name | *Meridian* series label → gone (faces show under Vespera) | Display | **No** |
| Retire a confusing series name | *Aether* → gone (shows under Arclight) | Display | **No** |
| Retire a confusing series name | *Ledger* → gone (shows under Settype) | Display | **No** |
| Retire a confusing series name | *Terra* → gone (shows under Vakt) | Display | **No** |
| Two Terra faces, only to avoid clashes | Terra *Solstice* → **Daylight**; Terra *Meridian Line* → **Longitude** | Display | **No** — ⚠️ *one check first, see below* |
| A designed (unbuilt) face | Arclight *Meridian* → **Transit** | Rename before build | No (not built yet) |

**⚠️ The two Terra renames — checked, and the answer was no.** *(Updated 2026-07-17; this section originally posed it as an open check.)* The worry was that the 25 built faces have their name *painted into the dial artwork*. **Phase 2C did the check and the renames FAILED it:** `TERRA SOLSTICE` and `TERRA · MERIDIAN LINE` really are painted into the dial art of every theme, and three independent sources agree the current names are right — the art, the handoff spec (`faces/collection3-tools/spec/cat-c.js`), and each face's own on-watch label (`app_name`). **Renaming them in the storefront would contradict the watch on the wrist, so both renames were skipped.**

The face that prompted the worry — Terra-Compass, whose art read `COMPASS · ROSE IS STATIC` — **was fixed in `dialed-v0.23.0`** (§11-q5). ⭐ **That fix reopens the renames as a cheap option:** it proved a re-bake is a one-line spec edit plus one command (`node gen/build-all.mjs WF-C3`), so "the name is painted on" is no longer a *cost* argument against renaming — it's a one-face re-bake each. The renames stay skipped on **taste**, not effort: the names are good and the shelf reads fine. Revisit only if the owner wants them.

**Every rename here is display-only. Nothing in this proposal re-mints a WFP token or forces anyone to re-install a face.**

---

## 7. Name clashes — found and resolved

The census found **19 name clashes** across the 192 designs (same word used by two different things). The important ones and how the map resolves them:

| Clashing name | Used by | Resolved by |
|---|---|---|
| **Meridian** | a built series + Vakt-Meridian (face) + a whole design folder | retire the series label (faces go under Vespera); the design folder is a *collection* name, not a face |
| **Aurum** | a built series + Vespera-Aurum (face) | rename the series shelf to "Atelier"; the face keeps its name in another collection |
| **Ledger** | a designed series + Halo-Ledger (built face) | fold the designed Ledger faces into Settype; the built face keeps its name |
| **Solstice** | Arclight-Solstice + Terra-Solstice | Terra's becomes "Daylight" (Arclight's is the shipped flagship) |
| **Odometer / Orrery** | built Kinetik faces + designed Armature faces | Armature is held in reserve (not placed), so no clash surfaces to a buyer |
| **Sector** | Meridian-Sector + two designed Sectors | the designed ones are in reserve |

**Convention that keeps this clean going forward:** show each face as **"Collection · Face"** in the app (e.g. "Vespera · Classic"). This makes most remaining clashes invisible without any more renaming. (Owner ratify — §11, question 8.)

---

## 8. "Coming soon" pictures — can we actually show them?

Your plan is that coming-soon faces appear *with their picture*. Good news: **every one of the 32 coming-soon tiles in the launch map can be pictured today.** They fall into buckets by how cheap:

- **Free (already drawn):** Collection 5's faces ship with finished SVG pictures — no rendering needed. (Not in the launch map, but relevant for future collections.)
- **Easy (one screenshot each):** FIELD, Aether Tidal/Nimbus/Aurora, Settype Spoken — all come from one design file we can screenshot with the installed Chrome.
- **Easy (inline art):** Trench, Calibre, Counterpoise — screenshot from their Atelier IV pages.
- **A bit more work:** Ledger and Arclight's three — need a specific view opened before the screenshot.

None of these needs the browser auto-download that fails on this machine — they all use the Chrome already installed. So no coming-soon tile is a blank box.

---

## 9. Collection 5 ("Meridian") and 1st Designs — the two unknowns, resolved

**Collection 5 = 70 finished face designs, the richest asset we own.** Not 5 sketches. It's a complete production handoff: 70 per-face spec files, 170 ready-made pictures (a normal *and* an always-on view for every face), a build guide, a 125-palette colour system, and even working code that draws each face. It is grouped into "Meridian I–V" (Atelier, Terra, Folio, Pulse, Still, Volume, Apex, Regal, Kinetic, Species, Maison). **It is a completely separate design line from the built Collection 3** — no shared faces — so none of it is double-counted. It is **not in the launch map on purpose**: dropping 70 faces in would swamp the 43 real ones. But it's the obvious source for future collections, and its ready-made pictures make it the cheapest coming-soon art we have. (One caveat: 20 of the 70 are single-colour and 10 are "impossible motion" designs that WFF can't actually do — so the practical, sellable slice is ~40, mostly Meridian I and II.) **Decision logged for you: §11, question 9.**

**1st Designs = the birthplace of the app's own faces.** This folder is where Aether, Kinetik, Settype and Vespera came from — 25 designed, 16 built. The **9 that were never built** are the most valuable gap fills because they're the original design language of collections you already ship: an entire **FIELD** collection (5 faces, never built — and FIELD is what the marketing was built around), 3 more Aether faces, and 1 more Settype (Spoken). The map uses all 9.

---

## 10. TwelveSixty — the scope call

**Recommendation: archive it. There is no fold-in decision to make, because it already happened.** TwelveSixty is a separate repo, but 16 of its designs were built *into Dialed's own catalog long ago* — 16 of the 18 faces in the app right now ARE TwelveSixty designs. The repo itself is dormant (last touched 2026-07-10), only ever got one face built, and was built on a packaging approach we since abandoned, so there's nothing to salvage wholesale.

**The one thing worth taking (about an hour's work):** the repo has a ~90%-finished **FIELD Splitline** face — the single most valuable unbuilt face in the estate (Play screenshot #1, the app-icon colour). Harvest that one folder, re-author it at the 480 canvas, and it seeds the FIELD set in Halo. Everything else in TwelveSixty is already counted or superseded.

---

## 11. The decisions I need from you ⭐ (sign-off gate)

These are genuine judgement calls the proposal deliberately did **not** make for you:

1. **Vakt at 15 vs 10.** Vakt reaches 10 built (Vakt + Terra) and then adds 5 dive faces (Trench), making 15. Same price as a 10-face collection, a premium price, or split the dive set into its own future collection? (Dive is called out as the best-selling watch genre.)
2. **Arclight-Pulsar's missing colours.** Build it a colour system (~1 day), ship it honestly as a one-look face, or pull it from the collection? It's 1 of only 4 built faces in Arclight, so pulling it hurts.
3. **⭐ The big one — how to sell part-built collections.** Only Vespera is 10-of-10 built. Settype and Arclight launch at 4-built-of-10. Options: (a) full price, coming-soon tiles count as promised value; (b) launch those two cheaper and raise the price as faces land; (c) hold them unlisted until they're 7+ built, and launch Vespera and Vakt first. **This is the biggest revenue decision in the map.**
4. **What the store card advertises** — the total ("10 faces") or the split ("5 available · 5 coming")? Recommendation: show the split on the card, the total in the header. A buyer who pays for 10 and finds 4 will say so in a review.
5. ~~**Terra-Compass's leaked art**~~ — ✅ **ANSWERED 2026-07-17: fix it. Done, shipped in `dialed-v0.23.0`.** The dial now reads `COMPASS`. A sweep of all five spec files confirmed it was the only leaked note.
6. **The Terra rename silkscreen check** (§6) — confirm "SOLSTICE"/"MERIDIAN LINE" aren't painted into the art before those two renames.
7. **The 9 coming-soon faces at 3 colours** (§4) — accept a bit of extra colour work in Arclight/Settype/Halo when we build them, or label those specific tiles "3 colours"?
8. **Ratify "Collection · Face" labels** (§7) — adopt it and most clashes vanish for free.
9. **⭐ Collection 5 (70 faces) — inventory or separate product?** Is it a future wing of Dialed (Meridian I+II ≈ 40 sellable faces, cheapest art in the estate), or a separate line? Right now it's parked in reserve.

---

## 12. What the adversarial review caught (honesty log)

The proposal was attacked by 6 independent "lenses" (arithmetic, package-ids, buyer coherence, collisions, theming, omissions), and every finding was then fact-checked by 3 more agents. **23 objections were raised; 6 survived.** The survivors were all corrected in the final proposal:

- **BLOCKER (corrected):** an earlier draft claimed we needed to "fix 25 misspelled package ids in a closing window." Fact-check proved this is a **no-op** — Dialed regenerates every id correctly from the folder name on every build, so the faces' internal names never matter. Deleted.
- **HIGH (corrected):** the colour-gap count was understated as "5 faces" — it's really **9** (the 5 FIELD + Aether ×3 + Settype Spoken). Reflected in §4.
- **MEDIUM/LOW (corrected):** a coming-soon tile count (30 vs 32), one face missing from the render list, and an off-by-one in the rename tally. All fixed.

The objections that did **not** survive were mostly matters of taste dressed up as facts (e.g. "the Kinetik theme is stretched by including a camera aperture") — the fact-checkers found the built faces already establish those boundaries. Where a taste objection had a real kernel (e.g. "Vakt bundles three genres"), it's surfaced as an open question above rather than silently decided.

---

## 13. The reserve pool (for Phase 2F and future collections)

**117 designed faces are not in the launch map — held in reserve, not lost.** This is the backlog for growing collections to 15 and for new collections later:

| Source | Faces in reserve | Notable |
|---|---:|---|
| Collection 5 "Meridian" | 70 | The 70-face handoff with finished art (see §9); ~40 sellable |
| Collection 4 "Atelier IV" | 17 | A whole **Podium** racing set (5), **Airframe** aviation (5, the Trench fallback), **Strata** material-time (5), + 2 benched |
| Fable showcases | 15 | **Armature** (mechanical), **Wilder** (nature), **Afterglow** (retro-digital) — 5 each |
| Collection 6 "Art of Motion" | 15 | The best-prepared *new* set (machine-readable, per-face palettes) but highest feasibility risk — every face's identity is an animation not yet proven in WFF |

The plan's Phase 2F ("gap builds") draws from here. My suggestion for the first new collection after launch is **Maison** (Collection 5's jewellery room, 10 faces) — it's the only ornamental/non-masculine design language in the whole estate and has the cheapest art (finished SVGs) — but that's a §11-style call for later.

---

*Full per-face data — every face's real WFF version, canvas, colour mechanism, complication slots, motion, preview path, design source, and proposed collection — is in `config/catalog-inventory.json`.*
