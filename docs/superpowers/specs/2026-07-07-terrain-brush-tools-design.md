# Terrain Brush Tools (Smooth / Melt / Fill / Lift) — Design Spec

**Date:** 2026-07-07 · **Status:** approved · **Research:** vault synthesis `lwos-terrain-tools-research` (brush-as-plan thesis, VoxelSniper erosion trio, FAWE ground-mask lesson)

## Goal

Four terrain-sculpting brush operations — **Smooth, Melt, Fill, Lift** — as one "Terrain" tool in the Alt wheel, with live hover preview, click-to-commit, Ctrl+scroll radius, full undo, and preview==apply. The thesis: **a brush is just another plan** — the ops build an `EditPlan` in the pure core exactly like the path pipeline, so determinism, preview, undo, and server authority all come free.

Rejected alternatives: true-3D voxel-ball ops (handles overhangs; far more complex, doesn't reuse column thinking — YAGNI for surface terraforming); ad-hoc server-side ops without plans (breaks preview==apply and undo — rejected outright).

## §1 Tool & input

- `ToolType.TERRAIN_BLEND` is **renamed to `TERRAIN`** (display name "Terrain"). Wheel position and icon index unchanged (ordinal 4 = the twin-hills pictogram already in `widgets.png`). No other `ToolType` changes.
- While the Terrain tool is active (builder mode, no screen open):
  - **M** cycles the op: Smooth → Melt → Fill → Lift → Smooth (same pattern/keybinding style as the path tool's terrain-mode cycle).
  - **Ctrl+scroll** adjusts brush radius, integer clamp **2..16**, default 6. Precedence: if the style panel is open and the cursor is over the panel while Ctrl is held, the panel's Ctrl-edit interaction wins and the brush radius does not change.
  - **Left-click** commits the currently previewed dab at the targeted ground column. No target (looking at sky) = no preview, click does nothing.
  - **HUD plate** (existing `ModeHudOverlay` pattern) reads `<Op> · r<radius>`, e.g. `Smooth · r6`.
- Brush state (active op, radius) is client-side tool state (alongside the path tool's state in `tool/`), not part of any style.
- The path tool and its flow are completely untouched.

## §2 Engine (pure core)

New package **`com.lwos.brush`** — subject to the purity boundary (no `net.minecraft.*` / `com.mojang.*` / `net.minecraftforge.*` imports):

- **`HeightField`** — local ground-height grid over the brush disc plus a 1-block ring (kernels need neighbors): `(x, z) → int height`, sampled once from `WorldView.groundHeight` (§4). Immutable snapshot; ops produce a modified copy.
- **`BrushOp`** — enum `SMOOTH, MELT, FILL, LIFT`, each a pure kernel `HeightField → HeightField` over columns within `radius` of center:
  - **SMOOTH**: 3×3 weighted mean of neighbor heights (center weight 4, edge 2, corner 1, /16), rounded; result lerped toward the original height by the radial falloff.
  - **MELT**: morphological erosion — a column strictly higher than the max of its 4-neighbors is pulled down to that max; falloff-scaled.
  - **FILL**: morphological dilation — a column strictly lower than the min of its 4-neighbors is raised to that min; falloff-scaled.
  - **LIFT**: +1 to every column where `falloff ≥ 0.5` (a plateau-ish raise with soft edges), i.e. `round(falloff)` added.
  - **Falloff**: `smoothstep(1 - dist/radius)` from brush center — every op fades at the rim instead of cutting a cylinder. `dist > radius` → untouched.
  - v1 kernels are single-pass and noise-free: no RNG, no seed. Determinism is trivial (same `WorldView` answers → byte-identical plan).
- **`BrushPlanBuilder.build(BrushOp op, int cx, int cz, int radius, WorldView view) → EditPlan`** — samples the `HeightField`, applies the kernel, diffs old→new heights per column:
  - **Raise** (`new > old`): PLACE copies of the column's current surface block (`WorldView.surfaceBlockId`) at `old+1 .. new`.
  - **Lower** (`new < old`): REMOVE (→ air) at `new+1 .. old`.
  - Unchanged columns emit nothing. Change kinds reuse `ChangeKind.PLACE` / `ChangeKind.REMOVE` exactly as CUT_AND_FILL does.

## §3 Materials

Raised columns stack the column's **own current surface block** (grass-topped column grows grass-topped). Simple by design for v1: no dirt-under-grass stratigraphy, no palettes — material fidelity belongs to a later masks/painting phase (research build-order steps 3–4). This spec **introduces** the required `WorldView` extension (pure interface, `com.lwos.geometry`):

```java
/** Block id (e.g. "minecraft:stone") of the topmost solid surface block at the column. */
String surfaceBlockId(int x, int z);
```

Plain `String` (not `BlockStateRef`) so `geometry` takes no dependency on `plan`. Both implementations answer by reading the block at `(x, surfaceHeight(x, z), z)` — identical answers on the preview and server views, so preview==apply holds. The path-refinements spec (`2026-07-07-path-refinements-design.md` §1) consumes the same method.

## §4 Ground mask (the FAWE lesson)

Naive surface detection counts trees and flora as terrain — smoothing near a tree smears it into a mound. `WorldView` gains **`int groundHeight(int x, int z)`**: the topmost block that counts as *ground*, skipping logs, leaves, saplings, flowers, grass/ferns, mushrooms, vines, snow layers. Implemented **once** in a shared MC-bound helper used by both `ForgeWorldView` (client preview) and `ServerWorldView` (server apply) so both sides answer identically — preview==apply holds. The existing `surfaceHeight` is untouched (the path pipeline keeps its current behavior).

## §5 Preview, wire, apply, undo

- **Preview**: while the Terrain tool is active, the client continuously builds the dab plan for the targeted column via `BrushPlanBuilder` over `ForgeWorldView` and renders it through the existing preview renderer (same ghost visuals as path preview). Re-previews on crosshair move, radius change, or op change. Preview==apply because both sides call the same `build(...)`.
- **Wire**: new **`BrushRequestPacket`** `(int opOrdinal, int cx, int cz, int radius)` mirroring `EditRequestPacket`'s flow: registered on the same channel, server handler **re-checks `LwosAccess`** (client check is UX only; authority is server-side), re-derives the plan via `BrushPlanBuilder` over `ServerWorldView`, applies via `PlacementEngine`. No seed on the wire (there is no seed). Params stay primitive fields — there is no styleJson analog because brushes have no style.
- **Undo**: applied brush plans are recorded in `UndoHistory` exactly like path commits — Ctrl+Z / Ctrl+Y just work.
- **Size budget**: a dab at r16 touches ≤ ~800 columns — well under path-commit sizes. v1 applies in one burst like paths. Per-tick chunk-grouped batching (the FAWE perf lesson) is **deferred** until a real size problem shows.

## §6 Out of scope (deferred, per research build order)

Drag-stroke brushes (spline-driven painting), masks / affect-only-these-blocks, roughen/distort noise brushes, slope-aware material painting, brush UI panel, scriptable brushes, per-tick batched apply.

## §7 Testing

Pure-core unit tests (no Minecraft), on synthetic `WorldView` fakes:

1. Flat ground: every op is a no-op (empty plan) except LIFT, which raises the falloff plateau by exactly 1.
2. A 1-column spike: MELT flattens it; SMOOTH lowers it partially; FILL leaves it.
3. A 1-column pit: FILL raises it; SMOOTH raises it partially; MELT leaves it.
4. Falloff: edits at the rim are weaker than at center (SMOOTH), and columns beyond `radius` are never touched (all ops).
5. Raise materials: raised blocks equal the column's `surfaceBlockId`; lowers emit REMOVE→air.
6. Ground mask: a fake view where `groundHeight < surfaceHeight` (a "tree") — the plan edits relative to ground, never touching the tree columns' canopy heights.
7. Determinism: same inputs → byte-identical `EditPlan` (map iteration order included).

## §8 Manual playtest checklist

1. Alt wheel shows "Terrain" (twin-hills icon); M cycles the four ops; HUD reads `<Op> · r<n>`.
2. Ctrl+scroll resizes radius 2..16; with the style panel open and cursor over it, Ctrl interaction edits the panel, not the radius.
3. Hover a hill: preview ghost appears; click: world matches the ghost exactly (preview==apply).
4. Smooth a bumpy field flat-ish; melt a spike; fill a trench; lift a platform.
5. Brush near a tree: the tree is not smeared into terrain.
6. Ctrl+Z undoes a dab; Ctrl+Y redoes it.
7. Non-allowlisted player: brush packets are rejected server-side.
