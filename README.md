# Dialed.

**Your wrist, dialed.** A boutique Watch Face Push marketplace for Wear OS 6 — the whole
fablecollection watch-face collection, unlocked with a single one-time purchase and pushed
straight to your Pixel Watch. Native Kotlin · Jetpack Compose · Material 3.

## What this is
- A **phone storefront** (`:app`) that browses the collection and pushes a chosen face to a
  paired Wear OS 6 watch via the [Watch Face Push](https://developer.android.com/training/wearables/watch-face-push) API.
- A **living hub**: every face in [aucksy/fablecollection](https://github.com/aucksy/fablecollection)
  — and every future one — is part of this app, via a git submodule.
- The developer's **laptop-free test harness** for new faces (dev/sideload mode, Phase 4A).

## Repo layout
```
app/                 phone storefront (Compose + billing + WFP transport)
faces/               git submodule → aucksy/fablecollection (the face library)
facepacks/<key>/     GENERATED per-face WFP packaging modules (tools/gen-facepacks.mjs)
tools/               gen-facepacks.mjs   ·  scripts/add_face.mjs
dialed-faces.keystore  shared throwaway faces signing key (≠ app key, per WFP)
```

## Build
Cloud-only, via GitHub Actions (`.github/workflows/build.yml`) — no local Android toolchain.
Push to `main` builds the storefront APK; tag `dialed-v*` cuts a signed release.

## Adding a face
```
node scripts/add_face.mjs      # sync submodule + regenerate facepacks/catalog/previews
```
then commit and tag `dialed-v*`. See `CLAUDE.md` for the full workflow and `FACE-INVENTORY.md`
for the current collection.

## Status
Phase 0 (face pipeline) + Phase 1 (storefront) built. Billing, the Wear bridge, and phone→watch
push land in Phases 2–4. See `CLAUDE.md`.
