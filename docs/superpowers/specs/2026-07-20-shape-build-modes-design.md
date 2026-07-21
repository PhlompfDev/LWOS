# Shape Build Modes — Design Spec (sub-project A of the Effortless-Building recreation)

Date: 2026-07-20. Status: approved (user delegated final approval 2026-07-20).

Recreates Effortless Building's click-driven shape building inside LWOS's plan
pipeline. Sub-project B (radial wheel redesign + Claude-Design icon set) and
sub-project C (UI tween layer) follow separately; this spec deliberately keeps
their surface area minimal (placeholder icons, one shared spring utility).

## 1. Goals

- Right-click point placement of six shapes — Line, Wall, Floor, Cube, Circle,
  Sphere — with live preview, exactly re-derived server-side (preview == apply).
- Left-click drives the identical shapes as **break mode** (red preview, plans air).
- Spring-animated preview presentation (bounce-in anchors, extent-chasing mesh).
- A `survivalMode` server config: item consumption, drops on break, hard cap.
- Existing invariants untouched: purity boundary, determinism, intent-shaped
  packets, server-authoritative rebuild, LwosAccess re-check, undo/redo.

Non-goals (deferred): diagonals/slope-floor/cylinder, EB modifiers
(mirror/array/radial), skeleton-cube / corner-circle fill variants, wheel visual
redesign, 2D UI tweens.

## 2. Interaction model

State machine (per shape gesture): `IDLE → ANCHORED [→ BASE_DONE (Cube only)] → commit → IDLE`.

- **Line (2 clicks)** — click 1 anchors a block; preview runs from the anchor
  toward the look target, snapped to the dominant axis (X/Y/Z). Click 2 commits.
- **Floor (2 clicks)** — click 1 anchors; the camera ray intersects the
  horizontal plane at anchor Y; hit corner defines an axis-aligned rectangle.
- **Wall (2 clicks)** — click 1 anchors; the vertical construction plane through
  the anchor (XY or ZY, whichever is more perpendicular to the initial look
  direction, fixed at anchor time) is ray-intersected; the hit defines length
  and height together.
- **Cube (3 clicks)** — clicks 1–2 define the base rectangle like Floor; the
  preview then extrudes vertically (ray vs. the vertical axis line through the
  second corner); click 3 commits.
- **Circle (2 clicks)** — click 1 is the center; the ray hit on the anchor's
  horizontal plane sets an integer radius; midpoint-circle rasterization.
- **Sphere (2 clicks)** — center + radius from the same plane hit; shell via
  integer distance band.

Rules:

- After the first anchor, targeting switches from terrain raycast to
  construction-plane intersection — shapes stretch into open air.
- First anchor comes from the normal block raycast (crosshair block, or the face-
  adjacent position for placement, EB-style). No mid-air first anchor in v1.
- Left-click anchors/commits the same state machine in **break mode**. A
  left-click during a place gesture cancels it (and vice versa) — one gesture,
  one intent. Esc (or tool switch / builder-mode toggle) cancels.
- `M` cycles the active shape's fill option `FILLED ↔ HOLLOW` (closed shapes
  only; Line ignores it). Hollow = outline/shell, filled = solid.
- Material = the block the player holds at first anchor (BlockStateRef of the
  BlockItem). Empty/non-block hand → HUD hint, gesture does not start.
- Per-axis extent clamp: 64 blocks, enforced client-side during stretch and
  re-checked server-side.

## 3. Preview motion (spring layer)

- New `client/anim/Spring` (scalar) + `SpringVec3`: critically-tunable damped
  spring, `x'' = ω²(target − x) − 2ζωx'`, **ζ = 0.8, f = 3 Hz** (ω = 6π),
  semi-implicit Euler integrated with real frame dt, clamped dt ≤ 50 ms.
- Anchor/hover outline **bounces in**: scale spring 0 → 1 (overshoot ≈ 1.05,
  settles < 400 ms).
- While stretching and on second-point click, rendered preview bounds are the
  spring state chasing the exact extents: the mesh is drawn under a matrix that
  linearly maps exact bounds → spring bounds (camera-relative, double math);
  the outline box draws at spring bounds.
- Springs are presentation-only: the `EditPlan`, plan cache, and packet always
  use exact geometry. Spring state lives in the renderer, resets on
  cancel/commit/tool-change, and never affects determinism.
- Sub-project C reuses `Spring` for 2D UI tweens. It lives under
  `com.lwos.client.anim` (client-owned; nothing pure needs it) but itself has
  no Minecraft imports, so it stays unit-testable.

## 4. Pure core: `com.lwos.shape`

Same purity rules as `plan`/`brush` (no `net.minecraft.*`, `com.mojang.*`,
`net.minecraftforge.*`).

- **`ShapeMode`** — `LINE, WALL, FLOOR, CUBE, CIRCLE, SPHERE`; knows
  `clickCount()` (2/2/2/3/2/2) and `supportsFill()` (all but LINE).
- **`ShapeOptions`** — immutable record `{ FillMode fill }` with lenient JSON
  round-trip (`toJson`/`fromJson`; unknown keys ignored, missing → FILLED).
- **`ShapeGeometry`** — static rasterizers returning deterministic sorted
  `List<GridPos>`:
  - `line(a, b)` — dominant-axis integer walk (no diagonal stepping in v1).
  - `rect(a, b, hollow)` — axis-aligned rectangle in the plane the two corners
    span (XZ for Floor, XY/ZY for Wall).
  - `box(a, b, hollow)` — solid box or 1-thick shell.
  - `circle(center, radius, hollow)` — midpoint circle / filled disc on XZ.
  - `sphere(center, radius, hollow)` — shell = positions where
    `round(dist) == radius`; filled = `dist ≤ radius + 0.5`.
  No RNG anywhere — shapes are deterministic by construction.
- **`ShapePlanBuilder.build(List<GridPos> anchors, ShapeMode, ShapeOptions,
  BlockStateRef material, boolean breakMode, WorldView) → EditPlan`**
  - place mode: plan `material` at each geometry position whose current block
    is air/replaceable (existing solid blocks are skipped, EB-style).
  - break mode: plan air at each geometry position that is currently solid.
  - Reuses `EditPlan`/`GridPos`/`PlannedChange` — preview cache, renderer,
    placement engine, and undo all work unchanged.
  - Throws on wrong anchor count; clamps per-axis extents at 64 defensively.

## 5. Client integration

- **`ShapeTool`** (`tool/`, pure like its siblings): anchors, mode (derived from
  the selected `ToolType`), options, breakMode, revision counter (preview cache
  key), plus the state machine of §2. One instance in `ToolManager`, shared by
  all six shape ToolTypes.
- **`ToolType`** becomes `PATH, TERRAIN, LINE, WALL, FLOOR, CUBE, CIRCLE,
  SPHERE` (FILL placeholder removed). Mapping `ToolType → ShapeMode` lives on
  the enum. `ToolManager.isShapeToolActive()` + `currentShape()` added;
  existing path/terrain routing untouched. The enum gains an explicit
  `iconIndex()` (path=0, line=1, circle=2, terrain=4; wall=5, floor=6, cube=7,
  sphere=8 — fill's old slot 3 is orphaned) so `ToolWheelOverlay` stops
  indexing the icon strip by `ordinal()` and existing strip art keeps its
  positions while new glyphs append.
- **`LwosInputHandler`**: when shape tool active — right/left click drive the
  gesture; `M` cycles fill; Esc cancels. Existing keys (Alt wheel, Ctrl panel,
  undo/redo) unchanged.
- **Plane targeting** in a small client helper (`client/ShapeAim`): camera ray ∩
  construction plane/axis in doubles; results clamped to extent cap.
- **Preview**: `ShapeTool` revision + anchors + aim target feed the existing
  debounced `PreviewPlanCache` pattern (a `ShapePreviewCache` sibling keyed on
  (revision, aim target, mode, options, material)); mesh rendered through the
  existing `PathRenderer` infrastructure (private Tesselator buffer at
  AFTER_PARTICLES, camera-relative doubles, +0.125Y / 1.01 scale lift). Break
  preview tints red (multiply vertex color; same mesh path).
- **HUD**: `ModeHudOverlay` plate shows active shape, fill mode, and commit
  results (placed N / insufficient items / capped).

## 6. Networking & server

- **`ShapeRequestPacket`** (`apply/net/`, after `BrushRequestPacket`):
  `{ List<GridPos> anchors, int modeOrdinal, String optionsJson,
  String materialJson, boolean breakMode }`. Intent only — never positions.
- Handler: re-check `LwosAccess` → validate (anchor count matches mode, world
  bounds, extent caps, anchor within 128 of player) → `ShapePlanBuilder.build`
  over `ServerWorldView` → survival enforcement (§7) → `PlacementEngine.apply`
  → `UndoHistory.record`. Lenient JSON fallback identical to `styleJson`.

## 7. Survival mode

New `LwosServerConfig` (ForgeConfigSpec, `lwos-server.toml`) — the mod's first
server config:

- `survivalMode` (bool, default `false`).
- `maxBlocksPerCommit` (int, default 32768) — enforced always (not just
  survival), rejecting oversized plans with a HUD message.

When `survivalMode` is true:

- **Place**: count planned blocks per material; consume matching items
  (`BlockItem` whose block matches the planned state's block) from the player's
  inventory before applying. Insufficient items → reject whole commit
  ("no half-shapes") with a HUD message naming the shortfall. Creative-gamemode
  players skip consumption.
- **Break**: broken blocks drop as items via standard loot tables (server-side
  `Block.dropResources`), respecting tool-independence (drop as if by hand is
  NOT used — drop unconditionally like EB's quick-break, keeps it simple).
- Undo of a survival place does NOT refund items in v1 (documented limitation;
  matches EB behavior closely enough and keeps UndoHistory unchanged).

## 8. Testing

- Pure JUnit: `ShapeGeometryTest` (each rasterizer: sizes, hollow vs filled,
  degenerate 1-block shapes, symmetry, extent clamp), `ShapePlanBuilderTest`
  (place skips solids, break plans air only on solids, anchor-count validation,
  byte-identical determinism over a fake WorldView), `ShapeOptionsJsonTest`
  (lenient round-trip), `ShapeToolTest` (state machine transitions, cancel
  rules, revision bumps), `SpringTest` (converges, overshoots once for ζ=0.8,
  dt-clamp stability).
- Packet round-trip test following the existing `BrushRequestPacket` pattern.
- Build gate: `./gradlew build` (JDK 17) green before push.
- Manual playtest checklist (in plan): every shape place+break at small/large
  sizes, mid-air stretch, hollow/filled via M, undo/redo across shape commits,
  survival toggle on dev server (consumption, rejection, drops, cap), spring
  feel (bounce, chase, settle), far-from-origin preview sanity, GUI scales 1–4
  HUD readability.

## 9. Asset note (texgen)

Four new 16×16 ink pictograms (wall, floor, cube, sphere) appended to
`tools/texgen/generate.py` **after all existing regions** (RNG append-only rule;
`--verify` must stay byte-identical for prior art). Icon strip indexing by
`ToolType.ordinal()` is re-pointed accordingly. These are placeholders until
sub-project B ships the full redesigned set.
