# R5 evidence — the #5 "distorted install" screenshot (received 2026-07-12)

Owner-supplied on-device photo (Pixel Watch 4 42mm) during a watch-face push/activation.

## What the photo shows
- System `12:04` TimeText (top) + a **teal dot + "Connected"** — Dialed's `ConnectionStatus(link = CONNECTED)`.
- A face **preview rendered as a horizontal STADIUM/OVAL** (~1.3–1.4:1, wider than tall), analog hands + a small
  complication (top-right) + tiny readout (lower-left), all **stretched horizontally**.
- The oval is **small** — roughly 45×34 dp equivalent, i.e. SMALLER than any Dialed `FaceDial` (which range 58–97 dp).

## Proof it is NOT a Dialed Compose bug (geometric + git)
1. **Code unchanged since the tested build.** `HEAD` differs from tag `dialed-v0.2.1` by exactly ONE commit
   (`c490c70`, docs only). The wear FaceDial/HomeScreen/ConciergeScreen code the owner tested IS today's HEAD.
   → "already fixed at HEAD" is ruled out.
2. **Every wear preview goes through `FaceDial`, which cannot render an oval.** `FaceDial`
   (`wear/.../ui/components/FaceDial.kt`) is a rigid `Box(Modifier.size(dp)).clip(CircleShape).border(CircleShape)`
   with `Image(contentScale = ContentScale.Crop, Modifier.fillMaxSize().clip(CircleShape))`. A fixed **square** box +
   Crop always yields a **circle**, even from a non-square bitmap (Crop preserves aspect, center-crops, clips). All
   call sites are rigid squares: HomeScreen `:68`/`:51` (73/66 dp), ConciergeScreen `:68`/`:85` (97/80 dp),
   ReceiveScreen (58–69 dp, `preview=null` anyway).
3. **`homePreview` is a cached 450×450 square PNG** (`WearViewModel.loadHome()` → `FacePreviewExtractor.loadCached()`;
   extractor pulls the face's `preview` drawable, all 18 are 450×450). Square source → square box → circle.

→ There is no oval-producing composable anywhere in the wear module. The stretched oval is drawn by **something other
   than Dialed** on that frame.

## Conclusion (matches the owner's own suspicion)
The oval is the **Wear OS platform's own "applying watch face" preview thumbnail**, shown by the system during
`setWatchFaceAsActive()` (a rounded-rectangle card that fits the face's preview/WFF render into a non-1:1 area).
It appears composited on/around the transition while Dialed's HomeScreen "Connected" chrome is still on screen. This
is a **platform surface Dialed cannot restyle**, not the `FaceDial`/extractor path.

Confidence: **HIGH on "not a Dialed FaceDial bug" (geometric proof).** MODERATE that the exact surface is the system
apply-preview — a 10-second **screen recording** or **`adb logcat`** at the set-active moment would make it 100%.

## Definitive next check (cheap, owner-gated)
- Record the screen (or logcat) during a push→set-active and note whether the oval belongs to a system dialog.
- A/B a **450-canvas face (Arclight)** vs a **300-canvas face** through activation: if only the 300-canvas faces look
  stretched in the system preview, the WFF authoring canvas is implicated (ties to R1); if both stretch equally, it's
  purely the platform thumbnail aspect.

## Implication for the phased plan (supersedes the earlier "FaceDial ContentScale fix" read)
- **Phase 2 does NOT need a `FaceDial` distortion fix** — that path is already correct (square + Crop + circle).
  Optional hardening only: make `FacePreviewExtractor.extract()` return a **guaranteed-square** bitmap
  (`toBitmap(w,h)` / center-crop-to-square) as belt-and-suspenders, and mirror the square+Crop+clip on the phone
  `FaceDial(face=...)` path — but neither causes this photo.
- **The real "#5 distortion" fix is Phase 5**: replace/cover the raw system apply moment with Dialed's OWN circular
  Concierge/Celebration wherever the activation strategy allows (`NO_ACTION_NEEDED` / `CALL_SET_ACTIVE_NO_USER_ACTION`
  already show a circular `FaceDial`; the distorted oval only appears on the paths that hand off to the system apply
  UI — `FOLLOW_PROMPT_ON_WATCH` / gesture). Where the system UI is unavoidable, set expectation with copy.
- Verify the WFF `preview`/`PREVIEW_TIME` renders correctly (square) in the system preview as part of R1/R2 work.

_Fold this verdict into `R5-installed-state-and-preview.md` Part B when finalizing. The running R5 agent was told the
screenshot was owed and to keep Part B candidate-based; this note is the definitive resolution._
