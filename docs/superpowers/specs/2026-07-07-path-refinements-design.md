# Path Refinements: Branch-Friendly Compositing + Core Blend — Design Spec

**Date:** 2026-07-07 · **Status:** approved · **Executes after:** `2026-07-07-terrain-brush-tools-design.md`, which introduces the `WorldView.surfaceBlockId` extension this spec consumes (its §3 carries the authoritative definition; repeated in §1 below for self-containedness).

## Problems (from building sessions)

1. **Branching eats the parent path.** Branching a new path off an existing one (e.g. a stone path with moss/coarse-dirt edges), the branch's edge scatter fires on every column within `edgeReach` — including columns where the parent's stone core sits — and `EditPlanBuilder` never checks what block is already there. The parent path gets pocked with edge blocks and needs manual repair.
2. **Core materials come out in big chunks.** `GradientEngine` picks per-column material by argmax of `weight × (Worley cellValue + 0.5 × Perlin jitter)`. Worley cells are constant over `clusterSize` blocks and a higher weight wins large *connected* regions, so palettes read as chunky same-block patches with hard boundaries — the builder wants a dithered blend.

## §1 Branch-friendly compositing

**Rule** (in `EditPlanBuilder.build`, FOLLOW_SURFACE only): before emitting a **scatter** change at a column, read the column's existing surface block; if its id is a member of the active style's **core palette or edge palette**, skip the column entirely (emit nothing — the existing block stays).

- The branch's **core** columns (both protected-core and feather-kept inside columns) **always place**, unchanged — the junction spine stays continuous (stone flows into stone).
- Feather-dropped inside columns already emit nothing — unchanged.
- `CUT_AND_FILL` is untouched: carving through an existing path is structural and intentional.

**`WorldView` extension** (introduced by the terrain-brush spec §3; pure interface, `com.lwos.geometry`):

```java
/** Block id (e.g. "minecraft:stone") of the topmost solid surface block at the column. */
String surfaceBlockId(int x, int z);
```

Returns a plain `String` (not `BlockStateRef`) so `geometry` takes no dependency on `plan`. Both implementations answer by reading the block at `(x, surfaceHeight(x, z), z)`: `ForgeWorldView` (client preview) and `ServerWorldView` (server apply) — identical answers, so preview==apply holds. Palette membership is tested against the style entries' `id()` strings (exact match).

**Known limitation (documented, accepted):** recognition uses the *active* style's palettes. Branching off a path built from a disjoint palette (no shared blocks with the current style) is not protected. The daily workflow — branching with the same style — is fully covered. Test-fake `WorldView`s in the existing suite gain the new method (returning a constant terrain id unless the test says otherwise).

## §2 Core blend control

**New `PathStyle` scalar: `coreBlend`**, clamped to `[0, 1]`, **default 0.3**.

- `GradientEngine` scoring becomes: per entry, `base = lerp(cellValue, white(x, z), coreBlend)`; score stays `weight × (base + 0.5 × perlinJitter)`, argmax unchanged.
  - `white(x, z)` is a **per-entry, per-block seeded hash noise** in `[0, 1)` (splitmix-style finalizer of `(subSeed, x, z)` — a new tiny pure helper in `com.lwos.organic.noise`, no `java.util.Random`). Independent per entry, like the existing fields.
  - `coreBlend = 0`: bitwise-identical to today's assignments (regression-tested).
  - `coreBlend = 1`: the decision varies block-by-block — a fully dithered mix.
  - Between: patches with increasingly ragged, intermixed boundaries.
- **Weight semantics unchanged**: weights remain an area *bias* (exactly as today), not exact proportional shares.
- `GradientEngine` gains the blend parameter via its constructor; **both** call sites pass it: the core gradient (`GRAD_SALT` stage) and the edge-scatter material chooser inside `EdgeScatterEngine` (edge scatter inherits the same blend — one knob, consistent look).
- **Serialization:** `coreBlend` is a `styleJson` field like every other knob (never a packet field). `fromJson` stays lenient: missing → default **0.3**, so legacy saved styles load with a mild blend rather than the chunky look. Out-of-range values clamp.

## §3 UI

One new slider in the style panel's **Core Materials** section: **"Core blend"**, range 0..1, wired through `PathStylePanel` (a `labeledSlider` row + published `SliderRect`) and `PathStyleEdits` exactly like the existing knobs. Panel height/scroll adapt as they do for existing rows; no other geometry changes. Journal skin conventions apply (ink label, wax value).

## §4 Testing

Pure-core unit tests:

1. **Compositing:** fake `WorldView` reporting a core-palette block id at known columns inside the scatter band → plan emits no change there; the same columns as terrain id → scatter may place. Core columns place even where the fake reports core-palette ids.
2. **Blend regression:** `coreBlend = 0` reproduces the current engine's assignments byte-for-byte on a fixed seed/palette grid.
3. **Blend effect:** `coreBlend = 1` produces at least N distinct-material adjacencies on a grid where `coreBlend = 0` produces contiguous patches (dithering actually happens).
4. **Determinism:** same seed/style/coords → identical assignments at every tested blend value.
5. **Serialization:** `toJson`/`fromJson` round-trips `coreBlend`; JSON missing the field loads as 0.3; out-of-range clamps.

## §5 Manual playtest checklist

1. Build a stone path with moss/coarse-dirt edges; branch off its middle: the parent's stone core keeps every block; the branch junction's spine is continuous.
2. Core blend slider at 0 → chunky patches (old look); at 1 → per-block dithered mix; live preview updates while dragging (Ctrl-edit).
3. Save a preset, reload, and confirm the blend value persists; load a pre-existing preset and confirm it gets 0.3.
4. Commit + Ctrl+Z still behave identically.
