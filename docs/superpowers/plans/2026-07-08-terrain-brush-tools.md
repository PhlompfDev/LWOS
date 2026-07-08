# Terrain Brush Tools (Smooth / Melt / Fill / Lift) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Spec:** `docs/superpowers/specs/2026-07-07-terrain-brush-tools-design.md` (approved 2026-07-07)

**Goal:** Four terrain-sculpting brush ops (Smooth, Melt, Fill, Lift) as one "Terrain" tool in the Alt wheel, with live hover preview, left-click commit, Ctrl+scroll radius, full undo, and preview==apply — implemented as "a brush is just another plan."

**Architecture:** A new pure-core package `com.lwos.brush` builds an `EditPlan` from a local ground-height grid (`HeightField` → `BrushOp` kernel → old/new height diff), exactly like the path pipeline does. The client previews the dab through the existing `PreviewRenderer` + debounced cache over `ForgeWorldView`; a new `BrushRequestPacket` carries only intent (op, center column, radius) and the server re-derives the plan over `ServerWorldView` and applies via `PlacementEngine`, so determinism, preview==apply, access gating, and Ctrl+Z undo all reuse existing machinery.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1 (47.4.10, official mappings), JUnit 5, Gradle (JDK 17 pin).

## Global Constraints

- **Purity boundary:** `com.lwos.brush` joins `plan`, `config`, `geometry`, `organic` as pure core — MUST NOT import `net.minecraft.*`, `com.mojang.*`, or `net.minecraftforge.*` (spec §2).
- **Determinism:** identical inputs + identical `WorldView` answers → byte-identical `EditPlan`. v1 kernels are single-pass and noise-free: no RNG, no seed, no wall-clock (spec §2).
- **preview == apply:** client preview and server apply call the *same* `BrushPlanBuilder.build(...)` over their own `WorldView`s (spec §5).
- **Server authority:** the `BrushRequestPacket` handler re-checks `LwosAccess.isAllowed` — the client-side gate is UX only (spec §5).
- **Radius:** integer, clamped **2..16**, default **6** — clamped on both the client tool state and the server handler (spec §1).
- **Tool rename:** `ToolType.TERRAIN_BLEND` → `TERRAIN`, display name `"Terrain"`; wheel position and icon index unchanged (ordinal 4, twin-hills icon in `widgets.png`). No other `ToolType` changes (spec §1).
- **The path tool and its flow are completely untouched** (spec §1). The existing `WorldView.surfaceHeight` behavior is untouched (spec §4).
- **Build/test:** Git Bash, `export JAVA_HOME="/c/Program Files/Java/jdk-17"` before every Gradle command (ForgeGradle rejects newer JDKs). Full suite: `./gradlew test`.
- **Commits:** conventional-commit subjects. Push to `main` when the final task's verification is green.

## File Structure

| File | Role |
|---|---|
| `src/main/java/com/lwos/geometry/WorldView.java` (modify) | + `groundHeight` (default = `surfaceHeight`), + `surfaceBlockId` (abstract) |
| `src/main/java/com/lwos/apply/SurfaceScan.java` (modify) | + shared MC-bound `groundHeight` / `surfaceBlockId` helpers (both views delegate here → identical answers) |
| `src/main/java/com/lwos/client/ForgeWorldView.java`, `src/main/java/com/lwos/apply/ServerWorldView.java` (modify) | override the two new methods via `SurfaceScan` |
| `src/main/java/com/lwos/brush/HeightField.java` (create) | immutable local ground-height grid, disc + 1-block ring |
| `src/main/java/com/lwos/brush/BrushOp.java` (create) | enum SMOOTH/MELT/FILL/LIFT; pure kernel `HeightField → HeightField` with smoothstep falloff |
| `src/main/java/com/lwos/brush/BrushPlanBuilder.java` (create) | `build(op, cx, cz, radius, view) → EditPlan` (height diff → PLACE/REMOVE) |
| `src/main/java/com/lwos/tool/TerrainBrushTool.java` (create) | pure client tool state: op, radius, revision counter |
| `src/main/java/com/lwos/tool/ToolType.java`, `ToolManager.java` (modify) | rename to TERRAIN; expose `currentBrush()` / `isTerrainToolActive()` |
| `src/main/java/com/lwos/apply/net/BrushRequestPacket.java` (create) + `src/main/java/com/lwos/LwosMod.java` (modify) | intent packet + channel registration (id 3) |
| `src/main/java/com/lwos/client/PreviewPlanCache.java` (modify) | genericize key from nested `Key` record to `Object` (equals-based) so the brush can reuse the debounce |
| `src/main/java/com/lwos/client/BrushRenderer.java` (create) | AFTER_PARTICLES hover preview + frame-published target column |
| `src/main/java/com/lwos/client/ModeHudOverlay.java` (modify) | `<Op> · r<radius>` plate while the Terrain tool is active |
| `src/main/java/com/lwos/client/LwosInputHandler.java` (modify) | M cycles op, Ctrl+scroll radius, left-click commit |
| `src/main/java/com/lwos/ui/PathStylePanel.java`, `PathStylePanelInput.java` (modify) | `cursorOverPanel()` helper + scroll precedence (panel wins over radius when cursor is over it) |

Tests: `src/test/java/com/lwos/brush/HeightFieldTest.java`, `BrushOpTest.java`, `BrushPlanBuilderTest.java`; `src/test/java/com/lwos/tool/TerrainBrushToolTest.java`; `src/test/java/com/lwos/apply/net/BrushRequestPacketTest.java`; modify `EditPlanBuilderTest.java`, `TerrainSamplerTest.java` (fakes gain `surfaceBlockId`), `ToolSessionTest.java` (rename).

---

### Task 1: WorldView ground mask + surface block id

The pure `WorldView` interface gains the two queries the brush engine needs (spec §3, §4). `groundHeight` gets a **default** implementation (`= surfaceHeight`) — semantically sound (no flora ⇒ ground == surface) and it keeps existing test fakes compiling. `surfaceBlockId` is **abstract** (there is no honest pure default for "what block is here"), so the compiler forces every implementation — including the two production views — to answer it. Both MC-bound implementations delegate to `SurfaceScan` so client preview and server apply answer identically (preview==apply). The sibling spec `2026-07-07-path-refinements-design.md` §1 consumes the same `surfaceBlockId` — do not rename it.

Note on TDD: this task is an interface extension plus MC-bound plumbing that headless tests can't execute; its *behavior* is pinned by the pure-core tests in Tasks 2–4 (which drive fakes through these exact signatures) and by the manual playtest in Task 9. The verification here is compilation plus the existing suite staying green.

**Files:**
- Modify: `src/main/java/com/lwos/geometry/WorldView.java`
- Modify: `src/main/java/com/lwos/apply/SurfaceScan.java`
- Modify: `src/main/java/com/lwos/client/ForgeWorldView.java`
- Modify: `src/main/java/com/lwos/apply/ServerWorldView.java`
- Modify: `src/test/java/com/lwos/plan/EditPlanBuilderTest.java:17-32` (three fakes)
- Modify: `src/test/java/com/lwos/geometry/TerrainSamplerTest.java:11` (one fake)

**Interfaces:**
- Consumes: existing `SurfaceScan.solidSurfaceHeight(LevelReader, int, int)`.
- Produces: `WorldView.groundHeight(int x, int z) → int` (default: `surfaceHeight(x, z)`), `WorldView.surfaceBlockId(int x, int z) → String` (abstract), `SurfaceScan.groundHeight(LevelReader, int, int) → int`, `SurfaceScan.surfaceBlockId(LevelReader, int, int) → String`. Tasks 2 and 4 call the `WorldView` methods.

- [ ] **Step 1: Extend the WorldView interface**

Replace the body of `src/main/java/com/lwos/geometry/WorldView.java` with:

```java
package com.lwos.geometry;

/**
 * Read-only world query surface for pure geometry code (spec §4.1, §4.3 boundary rule).
 * MUST NOT be implemented by anything importing net.minecraft.* in this package —
 * the Forge-backed implementation lives in com.lwos.client.
 */
public interface WorldView {
    /** Y coordinate of the topmost solid surface block at the given column. */
    int surfaceHeight(int x, int z);

    /**
     * Y of the topmost block that counts as *ground* at the column — skips logs, leaves,
     * saplings, flowers, grass/ferns, mushrooms, vines, and snow layers (the FAWE ground-mask
     * lesson: naive surface detection smears trees into terrain). Defaults to
     * {@link #surfaceHeight}: with no flora present, ground and surface coincide.
     */
    default int groundHeight(int x, int z) {
        return surfaceHeight(x, z);
    }

    /** Block id (e.g. "minecraft:stone") of the topmost solid surface block at the column. */
    String surfaceBlockId(int x, int z);
}
```

- [ ] **Step 2: Add the shared MC-bound helpers to SurfaceScan**

In `src/main/java/com/lwos/apply/SurfaceScan.java`, add two imports and two methods (keep `solidSurfaceHeight` byte-identical — the path pipeline's behavior must not change):

```java
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
```

```java
    /**
     * Y of the topmost block that counts as ground (spec §4): scans down from MOTION_BLOCKING,
     * skipping leaves, logs, snow layers, and anything that doesn't block motion (saplings,
     * flowers, grass/ferns, small mushrooms, vines are all non-motion-blocking). Shared by both
     * world views so client preview and server apply answer identically (preview==apply).
     */
    public static int groundHeight(LevelReader level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (y > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                    && !state.is(Blocks.SNOW) && state.blocksMotion()) break;
            y--;
            pos.setY(y);
        }
        return y;
    }

    /** Registry id string of the topmost solid surface block at the column (spec §3). */
    public static String surfaceBlockId(LevelReader level, int x, int z) {
        int y = solidSurfaceHeight(level, x, z);
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id == null ? "minecraft:air" : id.toString();
    }
```

- [ ] **Step 3: Override in both production views**

In `src/main/java/com/lwos/client/ForgeWorldView.java`, add below `surfaceHeight`:

```java
    @Override
    public int groundHeight(int x, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return 64;
        return SurfaceScan.groundHeight(level, x, z);
    }

    @Override
    public String surfaceBlockId(int x, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return "minecraft:air";
        return SurfaceScan.surfaceBlockId(level, x, z);
    }
```

In `src/main/java/com/lwos/apply/ServerWorldView.java`, add below `surfaceHeight`:

```java
    @Override
    public int groundHeight(int x, int z) {
        return SurfaceScan.groundHeight(level, x, z);
    }

    @Override
    public String surfaceBlockId(int x, int z) {
        return SurfaceScan.surfaceBlockId(level, x, z);
    }
```

- [ ] **Step 4: Give the four existing test fakes the new abstract method**

In `src/test/java/com/lwos/plan/EditPlanBuilderTest.java`, add to each of `FlatWorldView`, `SlopedWorldView`, and `LevelWorldView`:

```java
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
```

In `src/test/java/com/lwos/geometry/TerrainSamplerTest.java`, add the same override to its `SlopedWorldView`.

- [ ] **Step 5: Run the full suite (compile + regression check)**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test`
Expected: BUILD SUCCESSFUL, all existing tests pass (the path pipeline is untouched).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/lwos/geometry/WorldView.java src/main/java/com/lwos/apply/SurfaceScan.java src/main/java/com/lwos/client/ForgeWorldView.java src/main/java/com/lwos/apply/ServerWorldView.java src/test/java/com/lwos/plan/EditPlanBuilderTest.java src/test/java/com/lwos/geometry/TerrainSamplerTest.java
git commit -m "feat(geometry,apply): WorldView ground mask + surface block id for terrain brushes"
```

---

### Task 2: HeightField (pure core)

Immutable local ground-height grid over the brush disc plus a 1-block ring (kernels read 3×3 / 4-neighborhoods, so every column within the radius needs sampled neighbors). Sampled once from `WorldView.groundHeight` (never `surfaceHeight` — the ground mask is the whole point, spec §4); ops produce a modified copy.

**Files:**
- Create: `src/main/java/com/lwos/brush/HeightField.java`
- Test: `src/test/java/com/lwos/brush/HeightFieldTest.java`

**Interfaces:**
- Consumes: `WorldView.groundHeight(int, int)` from Task 1.
- Produces: `HeightField.sample(WorldView view, int cx, int cz, int radius) → HeightField`, `height(int x, int z) → int` (world coords), `copyHeights() → int[]`, `set(int[] target, int x, int z, int height)`, `with(int[] newHeights) → HeightField`. Tasks 3–4 use all of these.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/brush/HeightFieldTest.java`:

```java
package com.lwos.brush;

import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeightFieldTest {

    /** Ground height = x + 100*z so every column is distinguishable; surfaceHeight is a trap. */
    private static final class CoordWorldView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 999; } // must never be consulted
        @Override
        public int groundHeight(int x, int z) { return x + 100 * z; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    @Test
    void samplesDiscPlusOneBlockRing() {
        HeightField f = HeightField.sample(new CoordWorldView(), 10, -5, 3);
        // The sampled square spans [c-radius-1, c+radius+1] on both axes.
        assertEquals((10 - 4) + 100 * (-5 - 4), f.height(10 - 4, -5 - 4));
        assertEquals((10 + 4) + 100 * (-5 + 4), f.height(10 + 4, -5 + 4));
        assertEquals(10 + 100 * -5, f.height(10, -5));
    }

    @Test
    void samplesTheGroundMaskNotTheRawSurface() {
        HeightField f = HeightField.sample(new CoordWorldView(), 0, 0, 2);
        assertEquals(0, f.height(0, 0)); // groundHeight, not the 999 surfaceHeight
    }

    @Test
    void withProducesAnIndependentCopy() {
        HeightField f = HeightField.sample(new CoordWorldView(), 0, 0, 2);
        int[] work = f.copyHeights();
        f.set(work, 1, 1, 42);
        HeightField g = f.with(work);
        assertEquals(42, g.height(1, 1));
        assertEquals(1 + 100, f.height(1, 1)); // original untouched: fields are immutable snapshots
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.brush.HeightFieldTest"`
Expected: compilation FAILS — `HeightField` does not exist.

- [ ] **Step 3: Implement HeightField**

Create `src/main/java/com/lwos/brush/HeightField.java`:

```java
package com.lwos.brush;

import com.lwos.geometry.WorldView;

/**
 * Immutable local ground-height grid over the brush disc plus a 1-block ring (spec §2 —
 * kernels read 3x3 / 4-neighborhoods, so every column within the radius needs sampled
 * neighbors). Heights come from {@link WorldView#groundHeight}, the flora-skipping ground
 * mask (spec §4), never the raw surface. Ops never mutate a field; they take a working copy
 * via {@link #copyHeights()} and wrap it into a new field via {@link #with(int[])}.
 */
public final class HeightField {
    private final int minX;
    private final int minZ;
    private final int size;      // grid is size x size, row-major: index = (z - minZ) * size + (x - minX)
    private final int[] heights;

    private HeightField(int minX, int minZ, int size, int[] heights) {
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
        this.heights = heights;
    }

    /** Samples the square [c-radius-1, c+radius+1] on both axes from the view's ground mask. */
    public static HeightField sample(WorldView view, int cx, int cz, int radius) {
        int size = 2 * (radius + 1) + 1;
        int minX = cx - radius - 1;
        int minZ = cz - radius - 1;
        int[] h = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                h[z * size + x] = view.groundHeight(minX + x, minZ + z);
            }
        }
        return new HeightField(minX, minZ, size, h);
    }

    /** Ground height at world column (x, z). Must be within the sampled square. */
    public int height(int x, int z) {
        return heights[(z - minZ) * size + (x - minX)];
    }

    /** Working copy of the raw height array, for a kernel to mutate then wrap via {@link #with}. */
    public int[] copyHeights() {
        return heights.clone();
    }

    /** Writes a height into a working array obtained from {@link #copyHeights()} (world coords). */
    public void set(int[] target, int x, int z, int height) {
        target[(z - minZ) * size + (x - minX)] = height;
    }

    /** New field with the same bounds and the given heights (takes ownership of the array). */
    public HeightField with(int[] newHeights) {
        return new HeightField(minX, minZ, size, newHeights);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.brush.HeightFieldTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/brush/HeightField.java src/test/java/com/lwos/brush/HeightFieldTest.java
git commit -m "feat(brush): HeightField ground-height grid for brush kernels"
```

---

### Task 3: BrushOp kernels (pure core)

The four kernels, each a pure function `HeightField → HeightField` over columns within `radius` of center, faded by `smoothstep(1 - dist/radius)` so no op cuts a cylinder (spec §2). All neighbor reads come from the **original** snapshot (single-pass); writes go to a working copy.

**Files:**
- Create: `src/main/java/com/lwos/brush/BrushOp.java`
- Test: `src/test/java/com/lwos/brush/BrushOpTest.java`

**Interfaces:**
- Consumes: `HeightField` from Task 2.
- Produces: `enum BrushOp { SMOOTH, MELT, FILL, LIFT }` with `displayName() → String` ("Smooth"/"Melt"/"Fill"/"Lift"), `next() → BrushOp` (cycle order SMOOTH→MELT→FILL→LIFT→SMOOTH), `apply(HeightField field, int cx, int cz, int radius) → HeightField`. Task 4 calls `apply`; Task 5 uses `next()`; Task 7 uses `displayName()`; Tasks 6–8 use `ordinal()`/`values()`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/brush/BrushOpTest.java`:

```java
package com.lwos.brush;

import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrushOpTest {

    /** Flat ground at 70 everywhere. */
    private static final class FlatView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    /** Flat at 70 with a one-column spike (75) at the origin. */
    private static final class SpikeView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 0 && z == 0 ? 75 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    /** Flat at 70 with a one-column pit (65) at the origin. */
    private static final class PitView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 0 && z == 0 ? 65 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    /** Checkerboard 0/8 — every column has the identical 3x3 neighborhood pattern. */
    private static final class CheckerView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return ((x + z) & 1) == 0 ? 0 : 8; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:stone"; }
    }

    @Test
    void cycleOrderIsSmoothMeltFillLift() {
        assertEquals(BrushOp.MELT, BrushOp.SMOOTH.next());
        assertEquals(BrushOp.FILL, BrushOp.MELT.next());
        assertEquals(BrushOp.LIFT, BrushOp.FILL.next());
        assertEquals(BrushOp.SMOOTH, BrushOp.LIFT.next());
    }

    @Test
    void flatGroundIsANoOpForSmoothMeltFill() {
        HeightField before = HeightField.sample(new FlatView(), 0, 0, 6);
        for (BrushOp op : new BrushOp[] { BrushOp.SMOOTH, BrushOp.MELT, BrushOp.FILL }) {
            HeightField after = op.apply(before, 0, 0, 6);
            for (int z = -7; z <= 7; z++) {
                for (int x = -7; x <= 7; x++) {
                    assertEquals(70, after.height(x, z), op + " must not disturb flat ground");
                }
            }
        }
    }

    @Test
    void liftRaisesThePlateauByExactlyOne() {
        HeightField before = HeightField.sample(new FlatView(), 0, 0, 6);
        HeightField after = BrushOp.LIFT.apply(before, 0, 0, 6);
        assertEquals(71, after.height(0, 0));  // center: falloff 1 -> +1
        assertEquals(71, after.height(3, 0));  // dist 3, t=0.5, falloff 0.5 -> rounds to +1
        assertEquals(70, after.height(4, 0));  // dist 4, t=1/3, falloff ~0.26 -> rounds to 0
        assertEquals(70, after.height(6, 0));  // rim: falloff 0
    }

    @Test
    void meltFlattensASpike() {
        HeightField before = HeightField.sample(new SpikeView(), 0, 0, 4);
        HeightField after = BrushOp.MELT.apply(before, 0, 0, 4);
        assertEquals(70, after.height(0, 0)); // pulled down to the max of its 4-neighbors
        assertEquals(70, after.height(1, 0)); // neighbors untouched
    }

    @Test
    void smoothLowersASpikePartially() {
        HeightField before = HeightField.sample(new SpikeView(), 0, 0, 4);
        HeightField after = BrushOp.SMOOTH.apply(before, 0, 0, 4);
        // 3x3 weighted mean at the spike: (4*75 + 2*(4*70) + 4*70)/16 = 71.25 -> 71.
        assertEquals(71, after.height(0, 0));
    }

    @Test
    void fillLeavesASpikeAlone() {
        HeightField before = HeightField.sample(new SpikeView(), 0, 0, 4);
        HeightField after = BrushOp.FILL.apply(before, 0, 0, 4);
        for (int z = -5; z <= 5; z++) {
            for (int x = -5; x <= 5; x++) {
                assertEquals(before.height(x, z), after.height(x, z));
            }
        }
    }

    @Test
    void fillRaisesAPit() {
        HeightField before = HeightField.sample(new PitView(), 0, 0, 4);
        HeightField after = BrushOp.FILL.apply(before, 0, 0, 4);
        assertEquals(70, after.height(0, 0)); // raised to the min of its 4-neighbors
    }

    @Test
    void smoothRaisesAPitPartially() {
        HeightField before = HeightField.sample(new PitView(), 0, 0, 4);
        HeightField after = BrushOp.SMOOTH.apply(before, 0, 0, 4);
        // (4*65 + 2*(4*70) + 4*70)/16 = 68.75 -> 69.
        assertEquals(69, after.height(0, 0));
    }

    @Test
    void meltLeavesAPitAlone() {
        HeightField before = HeightField.sample(new PitView(), 0, 0, 4);
        HeightField after = BrushOp.MELT.apply(before, 0, 0, 4);
        for (int z = -5; z <= 5; z++) {
            for (int x = -5; x <= 5; x++) {
                assertEquals(before.height(x, z), after.height(x, z));
            }
        }
    }

    @Test
    void smoothEditsAreWeakerAtTheRimThanAtTheCenter() {
        HeightField before = HeightField.sample(new CheckerView(), 0, 0, 6);
        HeightField after = BrushOp.SMOOTH.apply(before, 0, 0, 6);
        int centerDelta = Math.abs(after.height(0, 0) - before.height(0, 0));
        int rimDelta = Math.abs(after.height(5, 0) - before.height(5, 0));
        assertTrue(centerDelta > rimDelta,
                "center delta " + centerDelta + " must exceed near-rim delta " + rimDelta);
        assertEquals(4, centerDelta); // full-strength smooth pulls a checker column to the mean
    }

    @Test
    void columnsBeyondTheRadiusAreNeverTouched() {
        for (BrushOp op : BrushOp.values()) {
            HeightField before = HeightField.sample(new CheckerView(), 0, 0, 3);
            HeightField after = op.apply(before, 0, 0, 3);
            // dist > radius within the square, and the +1 sampling ring.
            assertEquals(before.height(3, 3), after.height(3, 3), op + " touched dist>radius"); // dist ~4.24
            assertEquals(before.height(4, 0), after.height(4, 0), op + " touched the ring");
            assertEquals(before.height(-4, -4), after.height(-4, -4), op + " touched the ring corner");
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.brush.BrushOpTest"`
Expected: compilation FAILS — `BrushOp` does not exist.

- [ ] **Step 3: Implement BrushOp**

Create `src/main/java/com/lwos/brush/BrushOp.java`:

```java
package com.lwos.brush;

/**
 * The four terrain-brush kernels (spec §2). Each is a pure function HeightField -> HeightField
 * over the columns within {@code radius} of the brush center, faded by
 * {@code smoothstep(1 - dist/radius)} so every op softens at the rim instead of cutting a
 * cylinder; {@code dist > radius} is never touched. Neighbor reads always come from the
 * original snapshot (single-pass). v1 kernels are noise-free: no RNG, no seed — determinism
 * is structural (same WorldView answers -> byte-identical plan).
 */
public enum BrushOp {
    SMOOTH("Smooth"),
    MELT("Melt"),
    FILL("Fill"),
    LIFT("Lift");

    private final String displayName;

    BrushOp(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    /** Cycle order for the M key: Smooth -> Melt -> Fill -> Lift -> Smooth (spec §1). */
    public BrushOp next() {
        BrushOp[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Applies this kernel around (cx, cz), reading the original field and returning a modified copy. */
    public HeightField apply(HeightField field, int cx, int cz, int radius) {
        int[] out = field.copyHeights();
        for (int z = cz - radius; z <= cz + radius; z++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                long dx = x - cx;
                long dz = z - cz;
                double dist = Math.sqrt((double) (dx * dx + dz * dz));
                if (dist > radius) continue;
                double t = 1.0 - dist / radius;
                double falloff = t * t * (3.0 - 2.0 * t); // smoothstep
                field.set(out, x, z, kernel(field, x, z, falloff));
            }
        }
        return field.with(out);
    }

    private int kernel(HeightField field, int x, int z, double falloff) {
        int old = field.height(x, z);
        switch (this) {
            case SMOOTH -> {
                // 3x3 weighted mean: center 4, edges 2, corners 1, /16; rounded, then lerped
                // from the original height toward the mean by the radial falloff.
                double mean = (4.0 * old
                        + 2.0 * (field.height(x + 1, z) + field.height(x - 1, z)
                               + field.height(x, z + 1) + field.height(x, z - 1))
                        + field.height(x + 1, z + 1) + field.height(x - 1, z + 1)
                        + field.height(x + 1, z - 1) + field.height(x - 1, z - 1)) / 16.0;
                int smoothed = (int) Math.round(mean);
                return old + (int) Math.round((smoothed - old) * falloff);
            }
            case MELT -> {
                // Morphological erosion: a column strictly higher than the max of its
                // 4-neighbors is pulled down toward that max, scaled by falloff.
                int max4 = Math.max(Math.max(field.height(x + 1, z), field.height(x - 1, z)),
                                    Math.max(field.height(x, z + 1), field.height(x, z - 1)));
                return old > max4 ? old - (int) Math.round((old - max4) * falloff) : old;
            }
            case FILL -> {
                // Morphological dilation: a column strictly lower than the min of its
                // 4-neighbors is raised toward that min, scaled by falloff.
                int min4 = Math.min(Math.min(field.height(x + 1, z), field.height(x - 1, z)),
                                    Math.min(field.height(x, z + 1), field.height(x, z - 1)));
                return old < min4 ? old + (int) Math.round((min4 - old) * falloff) : old;
            }
            case LIFT -> {
                // Plateau-ish raise with soft edges: +1 wherever falloff rounds to 1.
                return old + (int) Math.round(falloff);
            }
        }
        throw new AssertionError("unreachable");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.brush.BrushOpTest"`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/brush/BrushOp.java src/test/java/com/lwos/brush/BrushOpTest.java
git commit -m "feat(brush): smooth/melt/fill/lift kernels with smoothstep falloff"
```

---

### Task 4: BrushPlanBuilder (pure core)

Diffs old→new heights per column into an `EditPlan`: raises PLACE copies of the column's own current surface block, lowers REMOVE to air (`ChangeKind.PLACE`/`REMOVE`, exactly as CUT_AND_FILL uses them). Fixed iteration order (z ascending, then x ascending) into a `LinkedHashMap` gives byte-identical plans for identical inputs — the same determinism contract as `EditPlanBuilder`.

**Files:**
- Create: `src/main/java/com/lwos/brush/BrushPlanBuilder.java`
- Test: `src/test/java/com/lwos/brush/BrushPlanBuilderTest.java`

**Interfaces:**
- Consumes: `HeightField` (Task 2), `BrushOp.apply` (Task 3), `WorldView.surfaceBlockId` (Task 1); existing `EditPlan`, `PlannedChange`, `ChangeKind`, `GridPos`, `BlockStateRef`.
- Produces: `BrushPlanBuilder.build(BrushOp op, int cx, int cz, int radius, WorldView view) → EditPlan`. Tasks 6 and 7 call it — the *same* method from both sides is what makes preview==apply hold.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/brush/BrushPlanBuilderTest.java`:

```java
package com.lwos.brush;

import com.lwos.geometry.WorldView;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrushPlanBuilderTest {

    /** Flat grass ground at 70. */
    private static final class FlatView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    /** Flat at 70 with a one-column stone spike (75) at the origin. */
    private static final class SpikeView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 0 && z == 0 ? 75 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) {
            return x == 0 && z == 0 ? "minecraft:stone" : "minecraft:grass_block";
        }
    }

    /** Flat at 70 with a one-column sand pit (65) at the origin. */
    private static final class PitView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 0 && z == 0 ? 65 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) {
            return x == 0 && z == 0 ? "minecraft:sand" : "minecraft:grass_block";
        }
    }

    /**
     * A "tree": the raw surface at (2,0) is a 90-high canopy, but the ground mask says 70.
     * The pit at the origin is the thing being filled; the tree column must never be edited
     * relative to its canopy height (spec §7 case 6 — the FAWE lesson).
     */
    private static final class TreeView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 2 && z == 0 ? 90 : 70; }
        @Override
        public int groundHeight(int x, int z) { return x == 0 && z == 0 ? 65 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    @Test
    void flatGroundYieldsAnEmptyPlanForSmoothMeltFill() {
        for (BrushOp op : new BrushOp[] { BrushOp.SMOOTH, BrushOp.MELT, BrushOp.FILL }) {
            assertTrue(BrushPlanBuilder.build(op, 0, 0, 6, new FlatView()).isEmpty(),
                    op + " on flat ground must be a no-op");
        }
    }

    @Test
    void liftPlacesAOneBlockPlateauOfTheSurfaceBlock() {
        EditPlan plan = BrushPlanBuilder.build(BrushOp.LIFT, 0, 0, 6, new FlatView());
        assertFalse(plan.isEmpty());
        for (PlannedChange change : plan.changes().values()) {
            assertEquals(ChangeKind.PLACE, change.kind());
            assertEquals(71, change.pos().y());
            assertEquals("minecraft:grass_block", change.state().id());
        }
        assertTrue(plan.changes().containsKey(new GridPos(0, 71, 0)));  // center raised
        assertTrue(plan.changes().containsKey(new GridPos(3, 71, 0)));  // plateau edge (falloff 0.5)
        assertFalse(plan.changes().containsKey(new GridPos(4, 71, 0))); // soft edge: no raise
    }

    @Test
    void meltingASpikeEmitsRemoveToAir() {
        EditPlan plan = BrushPlanBuilder.build(BrushOp.MELT, 0, 0, 4, new SpikeView());
        assertEquals(5, plan.size()); // 71..75 carved
        for (int y = 71; y <= 75; y++) {
            PlannedChange change = plan.changes().get(new GridPos(0, y, 0));
            assertNotNull(change, "carve expected at y=" + y);
            assertEquals(ChangeKind.REMOVE, change.kind());
            assertEquals("minecraft:air", change.state().id());
        }
    }

    @Test
    void fillingAPitStacksTheColumnsOwnSurfaceBlock() {
        EditPlan plan = BrushPlanBuilder.build(BrushOp.FILL, 0, 0, 4, new PitView());
        assertEquals(5, plan.size()); // 66..70 filled
        for (int y = 66; y <= 70; y++) {
            PlannedChange change = plan.changes().get(new GridPos(0, y, 0));
            assertNotNull(change, "fill expected at y=" + y);
            assertEquals(ChangeKind.PLACE, change.kind());
            assertEquals("minecraft:sand", change.state().id(), "raise must copy the column's own block");
        }
    }

    @Test
    void groundMaskKeepsTheBrushOffTreeCanopies() {
        EditPlan plan = BrushPlanBuilder.build(BrushOp.FILL, 0, 0, 4, new TreeView());
        assertFalse(plan.isEmpty());
        for (PlannedChange change : plan.changes().values()) {
            assertEquals(0, change.pos().x());
            assertEquals(0, change.pos().z());
            assertTrue(change.pos().y() <= 70, "edits must be relative to ground, not the 90-high canopy");
        }
    }

    @Test
    void identicalInputsProduceByteIdenticalPlans() {
        EditPlan a = BrushPlanBuilder.build(BrushOp.MELT, 0, 0, 4, new SpikeView());
        EditPlan b = BrushPlanBuilder.build(BrushOp.MELT, 0, 0, 4, new SpikeView());
        assertEquals(a.changes(), b.changes());
        // Map iteration order included (spec §7 case 7): LinkedHashMap key order must match.
        assertEquals(List.copyOf(a.changes().keySet()), List.copyOf(b.changes().keySet()));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.brush.BrushPlanBuilderTest"`
Expected: compilation FAILS — `BrushPlanBuilder` does not exist.

- [ ] **Step 3: Implement BrushPlanBuilder**

Create `src/main/java/com/lwos/brush/BrushPlanBuilder.java`:

```java
package com.lwos.brush;

import com.lwos.geometry.WorldView;
import com.lwos.plan.BlockStateRef;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@link EditPlan} for one brush dab (spec §2): sample the local ground
 * {@link HeightField}, run the {@link BrushOp} kernel, and diff old->new heights per column.
 * Raises stack copies of the column's own current surface block (spec §3); lowers carve to air.
 * Iteration order is fixed (z ascending, then x ascending) into a LinkedHashMap, so identical
 * inputs + identical WorldView answers produce a byte-identical plan — the same determinism
 * contract as EditPlanBuilder, which is what lets preview and apply share this one method.
 */
public final class BrushPlanBuilder {
    private static final BlockStateRef AIR = new BlockStateRef("minecraft:air");

    private BrushPlanBuilder() { }

    public static EditPlan build(BrushOp op, int cx, int cz, int radius, WorldView view) {
        HeightField before = HeightField.sample(view, cx, cz, radius);
        HeightField after = op.apply(before, cx, cz, radius);
        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (int z = cz - radius; z <= cz + radius; z++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                int oldH = before.height(x, z);
                int newH = after.height(x, z);
                if (newH > oldH) {
                    // Raise: grass-topped columns grow grass-topped (spec §3 — v1 has no
                    // stratigraphy; material fidelity belongs to a later masks/painting phase).
                    BlockStateRef surface = new BlockStateRef(view.surfaceBlockId(x, z));
                    for (int y = oldH + 1; y <= newH; y++) {
                        GridPos pos = new GridPos(x, y, z);
                        changes.put(pos, new PlannedChange(pos, ChangeKind.PLACE, surface));
                    }
                } else if (newH < oldH) {
                    for (int y = newH + 1; y <= oldH; y++) {
                        GridPos pos = new GridPos(x, y, z);
                        changes.put(pos, new PlannedChange(pos, ChangeKind.REMOVE, AIR));
                    }
                }
            }
        }
        return new EditPlan(changes);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.brush.BrushPlanBuilderTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/brush/BrushPlanBuilder.java src/test/java/com/lwos/brush/BrushPlanBuilderTest.java
git commit -m "feat(brush): BrushPlanBuilder — dab height diff to deterministic EditPlan"
```

---

### Task 5: TerrainBrushTool state + TERRAIN rename + ToolManager wiring

Client-side tool state (active op, radius) lives alongside `PathTool` in `tool/` — pure, not part of any style, never serialized (spec §1). `ToolType.TERRAIN_BLEND` becomes `TERRAIN` ("Terrain"); ordinal stays 4 so the wheel position and `widgets.png` icon index are untouched.

**Files:**
- Create: `src/main/java/com/lwos/tool/TerrainBrushTool.java`
- Modify: `src/main/java/com/lwos/tool/ToolType.java:9`
- Modify: `src/main/java/com/lwos/tool/ToolManager.java`
- Modify: `src/test/java/com/lwos/tool/ToolSessionTest.java:78`
- Test: `src/test/java/com/lwos/tool/TerrainBrushToolTest.java`

**Interfaces:**
- Consumes: `BrushOp` from Task 3.
- Produces: `TerrainBrushTool` with `op() → BrushOp`, `cycleOp()`, `radius() → int`, `adjustRadius(int delta)` (clamped 2..16), `revision() → long`, `bumpRevision()`, constants `MIN_RADIUS = 2`, `MAX_RADIUS = 16`, `DEFAULT_RADIUS = 6`; `ToolManager.currentBrush() → TerrainBrushTool`, `ToolManager.isTerrainToolActive() → boolean`, `ToolType.TERRAIN`. Tasks 6–8 use all of these.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/tool/TerrainBrushToolTest.java`:

```java
package com.lwos.tool;

import com.lwos.brush.BrushOp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainBrushToolTest {

    @Test
    void defaultsAreSmoothAtRadiusSix() {
        TerrainBrushTool tool = new TerrainBrushTool();
        assertEquals(BrushOp.SMOOTH, tool.op());
        assertEquals(6, tool.radius());
    }

    @Test
    void mCycleWrapsThroughAllFourOps() {
        TerrainBrushTool tool = new TerrainBrushTool();
        tool.cycleOp();
        assertEquals(BrushOp.MELT, tool.op());
        tool.cycleOp();
        assertEquals(BrushOp.FILL, tool.op());
        tool.cycleOp();
        assertEquals(BrushOp.LIFT, tool.op());
        tool.cycleOp();
        assertEquals(BrushOp.SMOOTH, tool.op());
    }

    @Test
    void radiusClampsToTwoThroughSixteen() {
        TerrainBrushTool tool = new TerrainBrushTool();
        for (int i = 0; i < 30; i++) tool.adjustRadius(-1);
        assertEquals(2, tool.radius());
        for (int i = 0; i < 30; i++) tool.adjustRadius(1);
        assertEquals(16, tool.radius());
    }

    @Test
    void everyMutationBumpsTheRevision() {
        TerrainBrushTool tool = new TerrainBrushTool();
        long r0 = tool.revision();
        tool.cycleOp();
        long r1 = tool.revision();
        assertTrue(r1 > r0);
        tool.adjustRadius(1);
        long r2 = tool.revision();
        assertTrue(r2 > r1);
        tool.bumpRevision();
        assertTrue(tool.revision() > r2);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.tool.TerrainBrushToolTest"`
Expected: compilation FAILS — `TerrainBrushTool` does not exist.

- [ ] **Step 3: Implement the tool state and wiring**

Create `src/main/java/com/lwos/tool/TerrainBrushTool.java`:

```java
package com.lwos.tool;

import com.lwos.brush.BrushOp;

/**
 * Terrain-brush session state (spec §1): the active op and brush radius. Client-side tool
 * state alongside PathTool — not part of any style, never serialized, never on the wire
 * beyond the per-dab request. Pure — no Minecraft imports.
 */
public class TerrainBrushTool {
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 16;
    public static final int DEFAULT_RADIUS = 6;

    private BrushOp op = BrushOp.SMOOTH;
    private int radius = DEFAULT_RADIUS;
    private long revision = 0;

    public BrushOp op() { return op; }

    /** M key: Smooth -> Melt -> Fill -> Lift -> Smooth (spec §1). */
    public void cycleOp() {
        op = op.next();
        revision++;
    }

    public int radius() { return radius; }

    /** Ctrl+scroll: steps the radius by delta, integer clamp 2..16 (spec §1). */
    public void adjustRadius(int delta) {
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius + delta));
        revision++;
    }

    /** Monotonic counter bumped on every mutation; the brush preview cache keys on it. */
    public long revision() { return revision; }

    /** Forces the next preview rebuild (e.g. after a committed dab changed the ground under it). */
    public void bumpRevision() { revision++; }
}
```

In `src/main/java/com/lwos/tool/ToolType.java`, change line 9:

```java
    TERRAIN("Terrain", 0x795548);
```

In `src/main/java/com/lwos/tool/ToolManager.java`, add a field next to `pathTool` and two methods next to `isPathToolActive()`/`currentPath()`:

```java
    private final TerrainBrushTool brushTool = new TerrainBrushTool();
```

```java
    public boolean isTerrainToolActive() { return enabled && selected == ToolType.TERRAIN; }

    public TerrainBrushTool currentBrush() { return brushTool; }
```

In `src/test/java/com/lwos/tool/ToolSessionTest.java:78`, update the rename:

```java
        assertEquals(ToolType.TERRAIN, tm.selected()); // wrap backwards
```

- [ ] **Step 4: Run the tool tests to verify they pass**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.tool.TerrainBrushToolTest" --tests "com.lwos.tool.ToolSessionTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/tool/TerrainBrushTool.java src/main/java/com/lwos/tool/ToolType.java src/main/java/com/lwos/tool/ToolManager.java src/test/java/com/lwos/tool/TerrainBrushToolTest.java src/test/java/com/lwos/tool/ToolSessionTest.java
git commit -m "feat(tool): terrain brush session state; rename TERRAIN_BLEND to TERRAIN"
```

---

### Task 6: BrushRequestPacket + channel registration

Intent-shaped C2S packet mirroring `EditRequestPacket`'s flow (spec §5): primitive fields only (there is no styleJson analog — brushes have no style), no seed on the wire (there is no seed). The handler re-checks `LwosAccess`, clamps the radius server-side, re-derives the plan via `BrushPlanBuilder` over `ServerWorldView`, applies via `PlacementEngine` (bedrock protection included for free), and pushes priors onto `UndoHistory` — Ctrl+Z/Ctrl+Y just work.

**Files:**
- Create: `src/main/java/com/lwos/apply/net/BrushRequestPacket.java`
- Modify: `src/main/java/com/lwos/LwosMod.java:36-47` (`registerPackets`)
- Test: `src/test/java/com/lwos/apply/net/BrushRequestPacketTest.java`

**Interfaces:**
- Consumes: `BrushOp` (Task 3), `BrushPlanBuilder.build(op, cx, cz, radius, view)` (Task 4), `TerrainBrushTool.MIN_RADIUS`/`MAX_RADIUS` semantics (Task 5 — values duplicated here as the server-side clamp, like `EditRequestPacket` duplicates the width clamp); existing `PlacementEngine.apply`, `LwosServerState.UNDO`, `LwosAccess.isAllowed`.
- Produces: `record BrushRequestPacket(int opOrdinal, int cx, int cz, int radius)` with static `encode`, `decode`, `handle` — Task 8's click-commit constructs and sends it via `LwosMod.CHANNEL`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/apply/net/BrushRequestPacketTest.java`:

```java
package com.lwos.apply.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrushRequestPacketTest {

    @Test
    void roundTripPreservesAllFields() {
        BrushRequestPacket before = new BrushRequestPacket(2, -1234, 5678, 9);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        BrushRequestPacket.encode(before, buf);
        BrushRequestPacket after = BrushRequestPacket.decode(buf);
        assertEquals(before, after);
    }

    @Test
    void decodeRejectsAnOutOfRangeOpOrdinal() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        BrushRequestPacket.encode(new BrushRequestPacket(99, 0, 0, 6), buf);
        assertThrows(IllegalArgumentException.class, () -> BrushRequestPacket.decode(buf));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.apply.net.BrushRequestPacketTest"`
Expected: compilation FAILS — `BrushRequestPacket` does not exist.

- [ ] **Step 3: Implement the packet and register it**

Create `src/main/java/com/lwos/apply/net/BrushRequestPacket.java`:

```java
package com.lwos.apply.net;

import com.lwos.apply.PlacementEngine;
import com.lwos.apply.ServerWorldView;
import com.lwos.brush.BrushOp;
import com.lwos.brush.BrushPlanBuilder;
import com.lwos.plan.EditPlan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server "commit this brush dab" request (spec §5). Carries only the user's intent —
 * op, center column, radius — never blocks, never a style (brushes have none), never a seed
 * (v1 kernels are noise-free). The server re-derives the plan over its own
 * {@link ServerWorldView} via the same {@link BrushPlanBuilder#build} the preview used, so
 * preview==apply holds by construction.
 */
public record BrushRequestPacket(int opOrdinal, int cx, int cz, int radius) {
    /** Server-side radius clamp (mirrors TerrainBrushTool's client clamp) so a crafted packet can't over-edit. */
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 16;

    public static void encode(BrushRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.opOrdinal());
        buf.writeInt(msg.cx());
        buf.writeInt(msg.cz());
        buf.writeVarInt(msg.radius());
    }

    public static BrushRequestPacket decode(FriendlyByteBuf buf) {
        int op = buf.readVarInt();
        if (op < 0 || op >= BrushOp.values().length) {
            throw new IllegalArgumentException("BrushRequestPacket op ordinal out of range: " + op);
        }
        return new BrushRequestPacket(op, buf.readInt(), buf.readInt(), buf.readVarInt());
    }

    public static void handle(BrushRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            // Authoritative access gate: the client-side check is UX only (spec §5).
            if (!com.lwos.LwosAccess.isAllowed(sender.getGameProfile().getName())) return;
            ServerLevel level = sender.serverLevel();
            int radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, msg.radius()));
            BrushOp op = BrushOp.values()[msg.opOrdinal()];

            // Deterministic rebuild from the server's own world view — never trust client blocks.
            EditPlan plan = BrushPlanBuilder.build(op, msg.cx(), msg.cz(), radius, new ServerWorldView(level));
            // Bedrock is only replaceable in creative — survival dabs leave the world floor intact.
            java.util.List<com.lwos.apply.UndoHistory.BlockSnapshot> priors =
                    PlacementEngine.apply(level, plan, sender.isCreative());
            if (!priors.isEmpty()) com.lwos.apply.LwosServerState.UNDO.push(sender.getUUID(), priors);
        });
        context.setPacketHandled(true);
    }
}
```

In `src/main/java/com/lwos/LwosMod.java`, add the import and register the packet at the end of `registerPackets()` (after `RedoRequestPacket`, so existing ids 0–2 are unchanged):

```java
import com.lwos.apply.net.BrushRequestPacket;
```

```java
        CHANNEL.registerMessage(id++, BrushRequestPacket.class,
                BrushRequestPacket::encode, BrushRequestPacket::decode, BrushRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.apply.net.BrushRequestPacketTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/apply/net/BrushRequestPacket.java src/main/java/com/lwos/LwosMod.java src/test/java/com/lwos/apply/net/BrushRequestPacketTest.java
git commit -m "feat(net): BrushRequestPacket — server-authoritative brush dab commit"
```

---

### Task 7: Hover preview + HUD plate

The client continuously builds the dab plan for the targeted ground column over `ForgeWorldView` and renders it through the existing `PreviewRenderer` (same ghost visuals as the path preview), re-previewing on crosshair move, radius change, or op change (spec §5). Reuses the debounced `PreviewPlanCache` — its key just needs to be generic (the brush key has different fields than the path key). The renderer publishes the target column via volatile statics, the established `PathRenderer` width-handle pattern, so Task 8's click-commit acts on exactly what the ghost shows. The HUD plate reads `<Op> · r<radius>`.

Two hard-won rendering lessons apply verbatim (see `lwos-known-problems`): never rebuild the plan per frame (hence the cache), and `PreviewRenderer.render` already handles the private-Tesselator-buffer and camera-relative-double-precision traps — pass it the **raw** camera-space pose stack (do NOT pre-translate by `-cam`) and flush the shared `lines()` batch afterwards for the red REMOVE outlines.

**Files:**
- Modify: `src/main/java/com/lwos/client/PreviewPlanCache.java` (genericize key)
- Create: `src/main/java/com/lwos/client/BrushRenderer.java`
- Modify: `src/main/java/com/lwos/client/ModeHudOverlay.java`

**Interfaces:**
- Consumes: `BrushPlanBuilder.build` (Task 4), `ToolManager.isTerrainToolActive()`/`currentBrush()` (Task 5), `BrushOp.displayName()` (Task 3); existing `PreviewRenderer.render(EditPlan, PoseStack, Vec3, MultiBufferSource)`, `ForgeWorldView.INSTANCE`, `JournalTheme` HUD plate drawing.
- Produces: `BrushRenderer.hasTarget` (volatile boolean), `BrushRenderer.targetX` / `targetZ` (volatile int) — Task 8's click-commit reads these. `PreviewPlanCache.needsRebuild(Object key, long nowMillis)` / `accept(Object key, EditPlan plan, long nowMillis)` (widened from the nested `Key` record; the record stays for `PathRenderer`).

- [ ] **Step 1: Genericize the preview cache key**

In `src/main/java/com/lwos/client/PreviewPlanCache.java`, widen the key parameter from `Key` to `Object` (equals-based, so any record works as a key; the nested `Key` record stays for `PathRenderer`):

```java
    /** Path-preview key (PathRenderer). Other tools may key with any equals-comparable record. */
    public record Key(long styleVersion, long pathRevision, double width, int modeOrdinal) { }

    private Object acceptedKey = null;
    private EditPlan last = null;
    private long lastRebuildMillis = Long.MIN_VALUE;

    public boolean needsRebuild(Object key, long nowMillis) {
        if (last == null || acceptedKey == null) return true;
        if (key.equals(acceptedKey)) return false;
        return nowMillis - lastRebuildMillis >= MIN_REBUILD_INTERVAL_MS;
    }

    public void accept(Object key, EditPlan plan, long nowMillis) {
        this.acceptedKey = key;
        this.last = plan;
        this.lastRebuildMillis = nowMillis;
    }
```

- [ ] **Step 2: Run the existing cache tests to confirm no regression**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.client.PreviewPlanCacheTest"`
Expected: PASS unchanged (the `Key` record still satisfies `Object`).

- [ ] **Step 3: Implement BrushRenderer**

Create `src/main/java/com/lwos/client/BrushRenderer.java`:

```java
package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.brush.BrushPlanBuilder;
import com.lwos.plan.EditPlan;
import com.lwos.tool.TerrainBrushTool;
import com.lwos.tool.ToolManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Live hover preview for the Terrain brush (spec §5): raycasts the targeted ground column each
 * frame, builds the dab plan over {@link ForgeWorldView} through the debounced cache, and
 * renders it with the same ghost visuals as the path preview. Preview==apply because this and
 * the server handler call the same {@code BrushPlanBuilder.build(...)}.
 *
 * <p>The target column is published via volatile statics each frame (the {@link PathRenderer}
 * width-handle pattern) so {@link LwosInputHandler}'s click-commit acts on exactly the column
 * the ghost is showing. No target (looking at sky) = no preview, and the click does nothing.
 */
@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BrushRenderer {
    /** Max look-ray distance (blocks), matching the path tool's extended placement reach. */
    private static final double MAX_TARGET_DISTANCE = 96.0;

    // Target column, published each frame for LwosInputHandler to commit against.
    public static volatile boolean hasTarget = false;
    public static volatile int targetX;
    public static volatile int targetZ;

    /** Debounced dab-plan cache — same policy as the path preview: no per-frame rebuilds. */
    private static final PreviewPlanCache CACHE = new PreviewPlanCache();

    /** Rebuild key: crosshair column, op, radius, and the tool revision (bumped on commit/undo). */
    private record BrushKey(int opOrdinal, int radius, int cx, int cz, long revision) { }

    private BrushRenderer() { }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        hasTarget = false; // cleared here; re-published below only when a ground column is targeted

        ToolManager tm = ToolManager.get();
        if (!tm.isTerrainToolActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        // Extended-reach ground pick (same clip as path point placement). Sky = no preview.
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 end = eye.add(look.x * MAX_TARGET_DISTANCE, look.y * MAX_TARGET_DISTANCE, look.z * MAX_TARGET_DISTANCE);
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        targetX = pos.getX();
        targetZ = pos.getZ();
        hasTarget = true;

        TerrainBrushTool brush = tm.currentBrush();
        BrushKey key = new BrushKey(brush.op().ordinal(), brush.radius(), targetX, targetZ, brush.revision());
        long now = System.currentTimeMillis();
        if (CACHE.needsRebuild(key, now)) {
            CACHE.accept(key, BrushPlanBuilder.build(
                    brush.op(), targetX, targetZ, brush.radius(), ForgeWorldView.INSTANCE), now);
        }
        EditPlan plan = CACHE.last();
        if (plan == null || plan.isEmpty()) return;

        // Same ghost visuals as the path preview. The pose stack must stay the RAW camera-space
        // stack (PreviewRenderer subtracts the camera in double — the far-from-origin lesson);
        // the REMOVE carve outlines ride the shared lines batch, flushed here because
        // PathRenderer skips entirely while a non-path tool is selected.
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PreviewRenderer.render(plan, ps, cam, buffers);
        buffers.endBatch(RenderType.lines());
    }
}
```

- [ ] **Step 4: Extend the HUD plate**

In `src/main/java/com/lwos/client/ModeHudOverlay.java`, replace the `render` method body's early-return and text selection (keep the plate drawing identical):

```java
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        ToolManager tm = ToolManager.get();
        if (mc.screen != null) return;

        String text;
        if (tm.isTerrainToolActive()) {
            // Brush readout (spec §1): e.g. "Smooth · r6".
            com.lwos.tool.TerrainBrushTool brush = tm.currentBrush();
            text = brush.op().displayName() + " · r" + brush.radius();
        } else if (tm.isPathToolActive()) {
            text = "Mode: " + tm.currentPath().terrainMode().displayName();
        } else {
            return;
        }

        Font font = mc.font;
        int x = MARGIN;
        int y = MARGIN;
        int w = font.width(text);
        JournalTheme.blitNineSlice(g, JournalTheme.HUD_PLATE, JournalTheme.HUD_TEX_W, JournalTheme.HUD_TEX_H,
                0, 0, JournalTheme.HUD_TEX_W, JournalTheme.HUD_TEX_H, JournalTheme.HUD_INSET,
                x - PAD, y - PAD, w + 2 * PAD, font.lineHeight + 2 * PAD);
        g.drawString(font, text, x, y, JournalTheme.INK, false);
    }
```

(The unused `TerrainMode` import can stay — `terrainMode()` is still referenced. Remove the import only if the compiler flags it.)

- [ ] **Step 5: Run the full suite (client classes are compile-verified only)**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass. (`BrushRenderer`/HUD behavior is exercised by the Task 9 playtest checklist.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/lwos/client/PreviewPlanCache.java src/main/java/com/lwos/client/BrushRenderer.java src/main/java/com/lwos/client/ModeHudOverlay.java
git commit -m "feat(client): terrain brush hover preview and HUD plate"
```

---

### Task 8: Input wiring — M cycles op, Ctrl+scroll radius, left-click commit

While the Terrain tool is active (builder mode, no screen): **M** cycles the op (the path tool's existing M binding, branched by active tool — no new keybinding, no lang change), **Ctrl+scroll** steps the radius, **left-click** (attack) commits the previewed dab. Precedence (spec §1): if the style panel is open and the cursor is over the panel while Ctrl is held, the panel's Ctrl-edit interaction wins and the radius does not change. That needs a cursor-over-panel test the panel doesn't currently expose, so `PathStylePanel` gains a `cursorOverPanel()` helper, and `PathStylePanelInput.onMouseScroll` gets the mirror-image guard **only when the Terrain tool is active** (path-tool panel scrolling from anywhere is unchanged) — this makes the two scroll consumers mutually exclusive by region instead of racing on event-bus order.

**Files:**
- Modify: `src/main/java/com/lwos/client/LwosInputHandler.java`
- Modify: `src/main/java/com/lwos/ui/PathStylePanel.java` (add helper)
- Modify: `src/main/java/com/lwos/ui/PathStylePanelInput.java:130-138` (`onMouseScroll`)

**Interfaces:**
- Consumes: `ToolManager.isTerrainToolActive()`/`currentBrush()` (Task 5), `TerrainBrushTool.cycleOp()`/`adjustRadius(int)`/`bumpRevision()`/`op()`/`radius()` (Task 5), `BrushRequestPacket` (Task 6), `BrushRenderer.hasTarget`/`targetX`/`targetZ` (Task 7), existing `PathStylePanelState.isEditing()`.
- Produces: `PathStylePanel.cursorOverPanel() → boolean` (gui-scaled cursor within the docked panel strip).

- [ ] **Step 1: Add the cursor-over-panel helper**

In `src/main/java/com/lwos/ui/PathStylePanel.java`, add (uses the same `x = screenWidth - PANEL_W - 8` origin as the panel's own layout; add `import net.minecraft.client.Minecraft;` if not already present):

```java
    /** True when the gui-scaled cursor is horizontally within the docked panel strip (right edge). */
    public static boolean cursorOverPanel() {
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        return mx >= mc.getWindow().getGuiScaledWidth() - PANEL_W - 8;
    }
```

- [ ] **Step 2: Wire the input handler**

In `src/main/java/com/lwos/client/LwosInputHandler.java`:

**(a)** Add a `ctrlHeld` helper next to `altHeld()`:

```java
    private static boolean ctrlHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
```

**(b)** In `onKey`, branch the M key by active tool (replace the existing `TOGGLE_TERRAIN_MODE` loop) and bump the brush revision after undo/redo sends (the ground under the hover preview may have changed):

```java
        while (LwosKeyMappings.UNDO.consumeClick()) {
            LwosMod.CHANNEL.sendToServer(new com.lwos.apply.net.UndoRequestPacket());
            tm.currentBrush().bumpRevision(); // ground may change under the brush preview
        }
        while (LwosKeyMappings.REDO.consumeClick()) {
            LwosMod.CHANNEL.sendToServer(new com.lwos.apply.net.RedoRequestPacket());
            tm.currentBrush().bumpRevision();
        }
```

```java
        while (LwosKeyMappings.TOGGLE_TERRAIN_MODE.consumeClick()) {
            // Same key, per-tool meaning (spec §1): brush op cycle vs the path terrain-mode cycle.
            if (tm.isTerrainToolActive()) tm.currentBrush().cycleOp();
            else tm.currentPath().toggleTerrainMode();
        }
```

**(c)** Replace `onScroll` with the Alt (tool wheel) + Ctrl (brush radius) version:

```java
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!inWorld() || !isModUser()) return;
        ToolManager tm = ToolManager.get();
        if (!tm.isEnabled()) return;
        double delta = event.getScrollDelta();
        if (delta == 0) return;
        if (altHeld()) {
            tm.cycle(delta > 0 ? 1 : -1);
            event.setCanceled(true); // don't move the hotbar selection
            return;
        }
        // Ctrl+scroll: brush radius (spec §1). Precedence: with the panel open and the cursor
        // over it while Ctrl is held, the panel's Ctrl-edit interaction wins.
        if (ctrlHeld() && tm.isTerrainToolActive()) {
            if (com.lwos.ui.PathStylePanelState.isEditing() && com.lwos.ui.PathStylePanel.cursorOverPanel()) return;
            tm.currentBrush().adjustRadius(delta > 0 ? 1 : -1);
            event.setCanceled(true);
        }
    }
```

**(d)** Add the left-click commit as a new subscriber (the existing `onUse` handles `isUseItem()` for the path tool and is untouched):

```java
    /** Left-click with the Terrain tool: commit the previewed dab at the targeted ground column (spec §1). */
    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || !inWorld() || !isModUser()) return;
        ToolManager tm = ToolManager.get();
        if (!tm.isTerrainToolActive()) return;
        event.setSwingHand(false);
        event.setCanceled(true); // the brush owns left-click while active — never break blocks
        if (!BrushRenderer.hasTarget) return; // looking at sky: no preview, click does nothing
        com.lwos.tool.TerrainBrushTool brush = tm.currentBrush();
        LwosMod.CHANNEL.sendToServer(new com.lwos.apply.net.BrushRequestPacket(
                brush.op().ordinal(), BrushRenderer.targetX, BrushRenderer.targetZ, brush.radius()));
        brush.bumpRevision(); // the ground under the preview just changed; force a rebuild
    }
```

- [ ] **Step 3: Guard the panel's scroll consumer (mutual exclusion by region)**

In `src/main/java/com/lwos/ui/PathStylePanelInput.java`, at the top of `onMouseScroll` (after the `isEditing` check), add:

```java
        // The terrain brush's Ctrl+scroll owns the wheel while the cursor is off-panel (spec §1
        // precedence rule); path-tool behavior (panel scrolls from anywhere while editing) is kept.
        if (ToolManager.get().isTerrainToolActive() && !PathStylePanel.cursorOverPanel()) return;
```

- [ ] **Step 4: Full build**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass. (Input wiring is MC-bound; behavior is verified by the Task 9 playtest checklist.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/client/LwosInputHandler.java src/main/java/com/lwos/ui/PathStylePanel.java src/main/java/com/lwos/ui/PathStylePanelInput.java
git commit -m "feat(client): terrain brush input — M cycles op, Ctrl+scroll radius, click commit"
```

---

### Task 9: Final verification, playtest, push

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Full clean verification**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew build`
Expected: BUILD SUCCESSFUL, zero test failures.

- [ ] **Step 2: Purity spot-check on the new package**

Run: `grep -rn "net.minecraft\|com.mojang\|net.minecraftforge" src/main/java/com/lwos/brush/ || echo "PURE"`
Expected: `PURE` (no forbidden imports in `com.lwos.brush`; note `geometry/Vec3d.java`'s javadoc false-positive does not apply here — if a match appears, it is a real violation, fix it).

- [ ] **Step 3: Manual playtest (spec §8) via `./gradlew runClient`**

Launch: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew runClient` — then walk the spec's checklist:

1. Alt wheel shows "Terrain" (twin-hills icon, same wheel position); M cycles Smooth → Melt → Fill → Lift; HUD reads `<Op> · r<n>`.
2. Ctrl+scroll resizes radius 2..16; with the style panel open and the cursor over it, Ctrl interaction edits the panel, not the radius.
3. Hover a hill: preview ghost appears; click: the world matches the ghost exactly (preview==apply).
4. Smooth a bumpy field flat-ish; melt a spike; fill a trench; lift a platform.
5. Brush near a tree: the tree is not smeared into terrain.
6. Ctrl+Z undoes a dab; Ctrl+Y redoes it.
7. Non-allowlisted player: brush packets are rejected server-side (verify via `runServer` + a second account, or by temporarily removing the dev name from `LwosAccess` and confirming clicks do nothing server-side; restore afterwards).

Record any failures as bugs and fix before pushing; do not push with a failing checklist item that indicates broken behavior (cosmetic nits may ship with a follow-up note).

- [ ] **Step 4: Push**

```bash
git push origin main
```

---

## Self-Review (performed while writing)

- **Spec coverage:** §1 tool/input → Tasks 5, 7 (HUD), 8; §2 engine → Tasks 2–4; §3 materials → Tasks 1, 4; §4 ground mask → Tasks 1, 2 (sampling), 4 (test); §5 preview/wire/apply/undo → Tasks 6–8 (size budget: one-burst apply is the default `PlacementEngine.apply`, nothing to build; per-tick batching explicitly deferred); §6 out-of-scope items appear nowhere; §7 test cases 1–7 → BrushOpTest (1–4), BrushPlanBuilderTest (1, 5, 6, 7); §8 checklist → Task 9.
- **Type consistency:** `BrushPlanBuilder.build(BrushOp, int, int, int, WorldView)` is identical at its Task 4 definition and Task 6/7 call sites; `TerrainBrushTool` method names match across Tasks 5, 7, 8; `PreviewPlanCache` widened signatures match the Task 7 renderer usage; `BrushRenderer.hasTarget/targetX/targetZ` match Task 8's reads.
- **Known deviation, intentional:** spec §1 says the panel wins when "the cursor is over the panel"; the existing `PathStylePanelInput` consumes scrolls from *anywhere* while Ctrl-editing. Task 8 resolves this by region (cursor over panel → panel; off panel → radius), guarding the panel side only when the Terrain tool is active so path-tool behavior is untouched.
