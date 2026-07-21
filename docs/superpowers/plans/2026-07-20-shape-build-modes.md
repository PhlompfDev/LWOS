# Shape Build Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** EB-style click-driven shape building (Line/Wall/Floor/Cube/Circle/Sphere) with left-click break, spring-animated previews, and a survival-mode server config — inside LWOS's plan pipeline.

**Architecture:** New pure `com.lwos.shape` package rasterizes shapes into `EditPlan`s (`ShapePlanBuilder`); the client `ShapeTool` + `ShapeRenderer` preview through the debounced-cache/`PreviewRenderer` machinery with a new spring-driven presentation transform; commit sends intent-only `ShapeRequestPacket`; the server re-derives, enforces `LwosServerConfig` survival rules, applies via `PlacementEngine`, and records undo.

**Tech Stack:** Forge 1.20.1 (47.4.10), JDK 17, Gson (pure core JSON), JUnit, Pillow texgen.

Spec: `docs/superpowers/specs/2026-07-20-shape-build-modes-design.md`.

## Global Constraints

- Build/test only on JDK 17 from Git Bash: `export JAVA_HOME="/c/Program Files/Java/jdk-17"` before every `./gradlew`.
- Purity boundary: `com.lwos.shape` (and `plan`, `config`, `geometry`, `organic`, `brush`, `tool`) MUST NOT import `net.minecraft.*`, `com.mojang.*`, `net.minecraftforge.*`.
- Determinism: no wall-clock, no unseeded Random anywhere in shape geometry/plan code (shapes use no RNG at all).
- preview == apply: client preview and server handler call the same `ShapePlanBuilder.build(...)`.
- Packets carry intent only (anchors/mode/options/material), never block lists.
- Conventional-commit subjects. Push to `main` only when build+tests are green.
- texgen: new drawing code is APPENDED after all existing regions in `tools/texgen/generate.py`; `--verify` must still pass for existing sheets before regeneration.

---

### Task 1: Per-block queries on WorldView

**Files:**
- Modify: `src/main/java/com/lwos/geometry/WorldView.java`
- Modify: `src/main/java/com/lwos/apply/SurfaceScan.java`
- Modify: `src/main/java/com/lwos/client/ForgeWorldView.java`
- Modify: `src/main/java/com/lwos/apply/ServerWorldView.java`
- Test: `src/test/java/com/lwos/geometry/WorldViewBlockIdTest.java`

**Interfaces:**
- Produces: `WorldView.blockIdAt(int x, int y, int z) -> String` (default derives from `surfaceHeight`/`surfaceBlockId`: above surface → `"minecraft:air"`, else the column's surface id — good enough for fake test views; Forge/Server views override with real lookups).
- Produces: `SurfaceScan.blockId(Level level, int x, int y, int z) -> String` (registry id of the block at the position; `"minecraft:air"` when out of bounds).

- [ ] **Step 1: Write the failing test**

```java
package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldViewBlockIdTest {
    /** Flat fake world: solid "minecraft:stone" at y <= 10, air above. */
    private static final WorldView FLAT = new WorldView() {
        @Override public int surfaceHeight(int x, int z) { return 10; }
        @Override public String surfaceBlockId(int x, int z) { return "minecraft:stone"; }
    };

    @Test
    void defaultBlockIdAt_airAboveSurface() {
        assertEquals("minecraft:air", FLAT.blockIdAt(0, 11, 0));
        assertEquals("minecraft:air", FLAT.blockIdAt(5, 200, -3));
    }

    @Test
    void defaultBlockIdAt_surfaceIdAtAndBelowSurface() {
        assertEquals("minecraft:stone", FLAT.blockIdAt(0, 10, 0));
        assertEquals("minecraft:stone", FLAT.blockIdAt(0, -20, 0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew test --tests "com.lwos.geometry.WorldViewBlockIdTest"`
Expected: compile FAILURE — `blockIdAt` not defined.

- [ ] **Step 3: Implement**

Append to `WorldView` (after `surfaceBlockId`):

```java
    /**
     * Registry id of the block at an exact position. Default derives a column-world answer
     * from the surface queries (above surface = air, else the surface block id) so simple
     * fake views keep working; the Forge-backed views override with real lookups.
     */
    default String blockIdAt(int x, int y, int z) {
        return y > surfaceHeight(x, z) ? "minecraft:air" : surfaceBlockId(x, z);
    }
```

Add to `SurfaceScan` (following its existing style; it already imports `Level`, `BlockPos`, `ForgeRegistries` — add any missing):

```java
    /** Registry id of the block at the exact position ("minecraft:air" outside the build height). */
    public static String blockId(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.isOutsideBuildHeight(pos)) return "minecraft:air";
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        return id == null ? "minecraft:air" : id.toString();
    }
```

Override in `ForgeWorldView`:

```java
    @Override
    public String blockIdAt(int x, int y, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return "minecraft:air";
        return SurfaceScan.blockId(level, x, y, z);
    }
```

Override in `ServerWorldView`:

```java
    @Override
    public String blockIdAt(int x, int y, int z) {
        return SurfaceScan.blockId(level, x, y, z);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.geometry.WorldViewBlockIdTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(geometry): WorldView.blockIdAt per-block query with column-derived default"
```

---

### Task 2: ShapeMode + ShapeOptions (pure)

**Files:**
- Create: `src/main/java/com/lwos/shape/ShapeMode.java`
- Create: `src/main/java/com/lwos/shape/ShapeOptions.java`
- Test: `src/test/java/com/lwos/shape/ShapeOptionsTest.java`

**Interfaces:**
- Produces: `enum ShapeMode { LINE, WALL, FLOOR, CUBE, CIRCLE, SPHERE }` with `int clickCount()` (CUBE=3, others 2), `boolean supportsFill()` (false for LINE), `String displayName()`.
- Produces: `ShapeOptions(ShapeOptions.Fill fill)` record, `enum Fill { FILLED, HOLLOW }`, `ShapeOptions cycleFill()`, `String toJson()`, `static ShapeOptions fromJson(String)` (lenient: null/garbage/missing → `FILLED`), `static ShapeOptions DEFAULT`.

- [ ] **Step 1: Write the failing test**

```java
package com.lwos.shape;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShapeOptionsTest {
    @Test
    void clickCounts() {
        assertEquals(3, ShapeMode.CUBE.clickCount());
        for (ShapeMode m : ShapeMode.values()) {
            if (m != ShapeMode.CUBE) assertEquals(2, m.clickCount());
        }
    }

    @Test
    void lineHasNoFill() {
        assertFalse(ShapeMode.LINE.supportsFill());
        assertTrue(ShapeMode.SPHERE.supportsFill());
    }

    @Test
    void jsonRoundTrip() {
        ShapeOptions hollow = new ShapeOptions(ShapeOptions.Fill.HOLLOW);
        assertEquals(hollow, ShapeOptions.fromJson(hollow.toJson()));
    }

    @Test
    void fromJsonLenient() {
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson(null));
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson(""));
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson("not json"));
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson("{}"));
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson("{\"unknown\":1}"));
    }

    @Test
    void cycleFillTogglesBothWays() {
        assertEquals(ShapeOptions.Fill.HOLLOW, ShapeOptions.DEFAULT.cycleFill().fill());
        assertEquals(ShapeOptions.Fill.FILLED, ShapeOptions.DEFAULT.cycleFill().cycleFill().fill());
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (compile failure — classes missing)

- [ ] **Step 3: Implement**

`ShapeMode.java`:

```java
package com.lwos.shape;

/**
 * The six EB-style click-driven shapes (spec §2). Pure — no Minecraft imports.
 * clickCount is the number of anchor clicks that define the shape (the final
 * click both defines the last anchor and commits).
 */
public enum ShapeMode {
    LINE("Line", 2, false),
    WALL("Wall", 2, true),
    FLOOR("Floor", 2, true),
    CUBE("Cube", 3, true),
    CIRCLE("Circle", 2, true),
    SPHERE("Sphere", 2, true);

    private final String displayName;
    private final int clickCount;
    private final boolean supportsFill;

    ShapeMode(String displayName, int clickCount, boolean supportsFill) {
        this.displayName = displayName;
        this.clickCount = clickCount;
        this.supportsFill = supportsFill;
    }

    public String displayName() { return displayName; }
    public int clickCount() { return clickCount; }
    public boolean supportsFill() { return supportsFill; }
}
```

`ShapeOptions.java`:

```java
package com.lwos.shape;

import com.google.gson.Gson;

/**
 * Per-gesture shape options (spec §4). Immutable; lenient JSON round-trip on the
 * PathStyle contract: unknown keys ignored, missing/malformed input falls back to
 * defaults so old clients and crafted packets degrade safely.
 */
public record ShapeOptions(Fill fill) {
    public enum Fill { FILLED, HOLLOW }

    public static final ShapeOptions DEFAULT = new ShapeOptions(Fill.FILLED);

    private static final Gson GSON = new Gson();

    public ShapeOptions {
        if (fill == null) fill = Fill.FILLED;
    }

    public ShapeOptions cycleFill() {
        return new ShapeOptions(fill == Fill.FILLED ? Fill.HOLLOW : Fill.FILLED);
    }

    public boolean hollow() { return fill == Fill.HOLLOW; }

    /** Serialized shape carried in ShapeRequestPacket's optionsJson. */
    private record Dto(String fill) { }

    public String toJson() {
        return GSON.toJson(new Dto(fill.name()));
    }

    public static ShapeOptions fromJson(String json) {
        if (json == null || json.isBlank()) return DEFAULT;
        try {
            Dto dto = GSON.fromJson(json, Dto.class);
            if (dto == null || dto.fill() == null) return DEFAULT;
            return new ShapeOptions(Fill.valueOf(dto.fill()));
        } catch (RuntimeException e) {
            return DEFAULT; // malformed json or unknown enum value — lenient by contract
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.shape.ShapeOptionsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(shape): ShapeMode enum and lenient ShapeOptions (pure core)"
```

---

### Task 3: ShapeGeometry rasterizers

**Files:**
- Create: `src/main/java/com/lwos/shape/ShapeGeometry.java`
- Test: `src/test/java/com/lwos/shape/ShapeGeometryTest.java`

**Interfaces:**
- Produces (all static, all return deterministic insertion-ordered `List<GridPos>` with no duplicates):
  - `line(GridPos a, GridPos b)` — dominant-axis integer walk from a to b.
  - `rect(GridPos a, GridPos b, boolean hollow)` — axis-aligned rectangle in the plane the corners span (the axis where a and b agree is the fixed axis; ties resolved Y-fixed → floor).
  - `box(GridPos a, GridPos b, boolean hollow)` — solid box or 1-thick shell.
  - `circle(GridPos center, int radius, boolean hollow)` — XZ-plane midpoint circle / filled disc at center.y.
  - `sphere(GridPos center, int radius, boolean hollow)` — shell (`round(dist)==radius`) or ball (`dist<=radius+0.5`).
  - `int MAX_EXTENT = 64` — every method clamps b (or radius) so no axis spans more than 64 blocks from a/center.

- [ ] **Step 1: Write the failing test**

```java
package com.lwos.shape;

import com.lwos.plan.GridPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShapeGeometryTest {
    @Test
    void lineWalksDominantAxis() {
        List<GridPos> l = ShapeGeometry.line(new GridPos(0, 5, 0), new GridPos(4, 5, 1));
        assertEquals(5, l.size()); // dominant axis X: 0..4
        assertEquals(new GridPos(0, 5, 0), l.get(0));
        assertEquals(new GridPos(4, 5, 0), l.get(l.size() - 1)); // z stays at the anchor's z
    }

    @Test
    void lineSingleBlock() {
        assertEquals(List.of(new GridPos(2, 2, 2)),
                ShapeGeometry.line(new GridPos(2, 2, 2), new GridPos(2, 2, 2)));
    }

    @Test
    void rectFloorFilledAndHollow() {
        // corners agree on Y -> XZ floor rectangle
        GridPos a = new GridPos(0, 4, 0), b = new GridPos(3, 4, 2);
        assertEquals(4 * 3, ShapeGeometry.rect(a, b, false).size());
        assertEquals(2 * (4 + 3) - 4, ShapeGeometry.rect(a, b, true).size()); // perimeter
    }

    @Test
    void rectWallSpansHeight() {
        // corners agree on X -> ZY wall
        GridPos a = new GridPos(1, 0, 0), b = new GridPos(1, 4, 3);
        assertEquals(5 * 4, ShapeGeometry.rect(a, b, false).size());
    }

    @Test
    void boxShellVsSolid() {
        GridPos a = new GridPos(0, 0, 0), b = new GridPos(3, 3, 3);
        assertEquals(64, ShapeGeometry.box(a, b, false).size());
        assertEquals(64 - 8, ShapeGeometry.box(a, b, true).size()); // 4^3 minus 2^3 interior
    }

    @Test
    void circleIsSymmetricAndCentered() {
        List<GridPos> c = ShapeGeometry.circle(new GridPos(0, 7, 0), 5, true);
        HashSet<GridPos> set = new HashSet<>(c);
        assertEquals(set.size(), c.size()); // no duplicates
        for (GridPos p : c) {
            assertEquals(7, p.y());
            assertTrue(set.contains(new GridPos(-p.x(), 7, p.z()))); // x-mirror symmetry
            assertTrue(set.contains(new GridPos(p.x(), 7, -p.z()))); // z-mirror symmetry
        }
    }

    @Test
    void filledCircleContainsOutline() {
        HashSet<GridPos> filled = new HashSet<>(ShapeGeometry.circle(new GridPos(0, 0, 0), 4, false));
        assertTrue(filled.containsAll(ShapeGeometry.circle(new GridPos(0, 0, 0), 4, true)));
        assertTrue(filled.contains(new GridPos(0, 0, 0)));
    }

    @Test
    void sphereShellHasNoInterior() {
        HashSet<GridPos> shell = new HashSet<>(ShapeGeometry.sphere(new GridPos(0, 0, 0), 4, true));
        assertFalse(shell.contains(new GridPos(0, 0, 0)));
        assertTrue(shell.contains(new GridPos(4, 0, 0)));
        assertTrue(shell.contains(new GridPos(0, -4, 0)));
    }

    @Test
    void extentClampCapsRunawayShapes() {
        List<GridPos> l = ShapeGeometry.line(new GridPos(0, 0, 0), new GridPos(500, 0, 0));
        assertEquals(ShapeGeometry.MAX_EXTENT + 1, l.size()); // anchor + 64
        List<GridPos> s = ShapeGeometry.sphere(new GridPos(0, 0, 0), 500, true);
        for (GridPos p : s) assertTrue(Math.abs(p.x()) <= ShapeGeometry.MAX_EXTENT);
    }

    @Test
    void deterministicOrder() {
        assertEquals(ShapeGeometry.sphere(new GridPos(3, 9, -2), 6, false),
                     ShapeGeometry.sphere(new GridPos(3, 9, -2), 6, false));
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (compile failure)

- [ ] **Step 3: Implement**

```java
package com.lwos.shape;

import com.lwos.plan.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure deterministic shape rasterizers (spec §4). No RNG anywhere — identical inputs
 * always produce identical, insertion-ordered position lists (iteration order is part
 * of the determinism contract because EditPlan preserves insertion order).
 */
public final class ShapeGeometry {
    /** Max blocks any axis may span from the anchor/center (spec §2 cap). */
    public static final int MAX_EXTENT = 64;

    private ShapeGeometry() { }

    /** Straight axis-aligned run from a toward b along the dominant delta axis. */
    public static List<GridPos> line(GridPos a, GridPos b) {
        b = clampToExtent(a, b);
        int dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        List<GridPos> out = new ArrayList<>();
        if (ax >= ay && ax >= az) {
            int s = Integer.signum(dx) == 0 ? 1 : Integer.signum(dx);
            for (int x = a.x(); x != b.x() + s; x += s) { out.add(new GridPos(x, a.y(), a.z())); if (dx == 0) break; }
        } else if (ay >= az) {
            int s = Integer.signum(dy);
            for (int y = a.y(); y != b.y() + s; y += s) out.add(new GridPos(a.x(), y, a.z()));
        } else {
            int s = Integer.signum(dz);
            for (int z = a.z(); z != b.z() + s; z += s) out.add(new GridPos(a.x(), a.y(), z));
        }
        return out;
    }

    /**
     * Axis-aligned rectangle between two corners. The fixed axis is the one where the
     * corners agree (Y fixed = floor, X or Z fixed = wall); if several agree, the first
     * of Y, X, Z wins (degenerate rectangles collapse to lines/points).
     */
    public static List<GridPos> rect(GridPos a, GridPos b, boolean hollow) {
        b = clampToExtent(a, b);
        List<GridPos> out = new ArrayList<>();
        if (a.y() == b.y()) { // floor: spans X and Z
            int y = a.y();
            int x0 = Math.min(a.x(), b.x()), x1 = Math.max(a.x(), b.x());
            int z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++)
                    if (!hollow || x == x0 || x == x1 || z == z0 || z == z1)
                        out.add(new GridPos(x, y, z));
        } else if (a.x() == b.x()) { // wall in the ZY plane
            int x = a.x();
            int z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
            int y0 = Math.min(a.y(), b.y()), y1 = Math.max(a.y(), b.y());
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++)
                    if (!hollow || z == z0 || z == z1 || y == y0 || y == y1)
                        out.add(new GridPos(x, y, z));
        } else { // wall in the XY plane (z fixed to the anchor's z)
            int z = a.z();
            int x0 = Math.min(a.x(), b.x()), x1 = Math.max(a.x(), b.x());
            int y0 = Math.min(a.y(), b.y()), y1 = Math.max(a.y(), b.y());
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    if (!hollow || x == x0 || x == x1 || y == y0 || y == y1)
                        out.add(new GridPos(x, y, z));
        }
        return out;
    }

    /** Solid box or 1-thick shell between opposite corners. */
    public static List<GridPos> box(GridPos a, GridPos b, boolean hollow) {
        b = clampToExtent(a, b);
        int x0 = Math.min(a.x(), b.x()), x1 = Math.max(a.x(), b.x());
        int y0 = Math.min(a.y(), b.y()), y1 = Math.max(a.y(), b.y());
        int z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
        List<GridPos> out = new ArrayList<>();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    if (!hollow || x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1)
                        out.add(new GridPos(x, y, z));
        return out;
    }

    /** XZ-plane circle at center.y: ring (hollow) or disc (filled). */
    public static List<GridPos> circle(GridPos center, int radius, boolean hollow) {
        radius = Math.min(Math.abs(radius), MAX_EXTENT);
        List<GridPos> out = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt((double) x * x + (double) z * z);
                boolean inside = dist <= radius + 0.5;
                boolean onRing = inside && dist >= radius - 0.5;
                if (hollow ? onRing : inside)
                    out.add(new GridPos(center.x() + x, center.y(), center.z() + z));
            }
        }
        return out;
    }

    /** Sphere shell (hollow) or ball (filled) around center. */
    public static List<GridPos> sphere(GridPos center, int radius, boolean hollow) {
        radius = Math.min(Math.abs(radius), MAX_EXTENT);
        List<GridPos> out = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
                    boolean inside = dist <= radius + 0.5;
                    boolean onShell = inside && (long) Math.round(dist) == radius;
                    if (hollow ? onShell : inside)
                        out.add(new GridPos(center.x() + x, center.y() + y, center.z() + z));
                }
            }
        }
        return out;
    }

    /** Clamps b so no axis is farther than MAX_EXTENT from a. */
    private static GridPos clampToExtent(GridPos a, GridPos b) {
        return new GridPos(
                a.x() + clamp(b.x() - a.x()),
                a.y() + clamp(b.y() - a.y()),
                a.z() + clamp(b.z() - a.z()));
    }

    private static int clamp(int d) {
        return Math.max(-MAX_EXTENT, Math.min(MAX_EXTENT, d));
    }
}
```

Note on `line`: the `dx == 0` break guard covers the single-block case where the signum is 0 and the loop must emit exactly the anchor.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.shape.ShapeGeometryTest"`
Expected: PASS (fix the sphere-shell ring test expectation only if the rounding contract genuinely differs — the shell must contain the axis poles).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(shape): deterministic shape rasterizers (line/rect/box/circle/sphere)"
```

---

### Task 4: ShapePlanBuilder

**Files:**
- Create: `src/main/java/com/lwos/shape/ShapePlanBuilder.java`
- Test: `src/test/java/com/lwos/shape/ShapePlanBuilderTest.java`

**Interfaces:**
- Consumes: `ShapeGeometry` rasterizers, `WorldView.blockIdAt`, `ShapeMode.clickCount()`, `ShapeOptions.hollow()`.
- Produces: `static EditPlan build(List<GridPos> anchors, ShapeMode mode, ShapeOptions options, BlockStateRef material, boolean breakMode, WorldView world)`.
  - Anchor semantics: LINE/WALL/FLOOR = [a, b]; CUBE = [a, b, c] (c.y is the extrusion height, c.x/z ignored); CIRCLE/SPHERE = [center, radiusPoint] (radius = rounded euclidean distance, CIRCLE horizontal-only).
  - Place mode: `ChangeKind.PLACE` of `material` at positions whose current block is air-like (`minecraft:air`, `minecraft:cave_air`, `minecraft:void_air`); existing solids skipped.
  - Break mode: `ChangeKind.REMOVE` with `BlockStateRef("minecraft:air")` at positions that are currently non-air-like.
  - `IllegalArgumentException` on wrong anchor count.

- [ ] **Step 1: Write the failing test**

```java
package com.lwos.shape;

import com.lwos.geometry.WorldView;
import com.lwos.plan.BlockStateRef;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShapePlanBuilderTest {
    private static final BlockStateRef OAK = new BlockStateRef("minecraft:oak_planks");
    /** Ground at y<=5 is stone, everything above is air. */
    private static final WorldView FLAT = new WorldView() {
        @Override public int surfaceHeight(int x, int z) { return 5; }
        @Override public String surfaceBlockId(int x, int z) { return "minecraft:stone"; }
    };

    @Test
    void placeFillsAirOnly() {
        // vertical wall from y=4 to y=8: y<=5 is stone (skipped), y>5 planned
        EditPlan plan = ShapePlanBuilder.build(
                List.of(new GridPos(0, 4, 0), new GridPos(0, 8, 3)),
                ShapeMode.WALL, ShapeOptions.DEFAULT, OAK, false, FLAT);
        assertFalse(plan.changes().containsKey(new GridPos(0, 4, 0))); // stone, skipped
        assertFalse(plan.changes().containsKey(new GridPos(0, 5, 2)));
        PlannedChange top = plan.changes().get(new GridPos(0, 8, 1));
        assertEquals(ChangeKind.PLACE, top.kind());
        assertEquals(OAK, top.state());
        assertEquals(4 * 3, plan.size()); // y 6,7,8 x z 0..3
    }

    @Test
    void breakPlansAirOnSolidsOnly() {
        EditPlan plan = ShapePlanBuilder.build(
                List.of(new GridPos(0, 4, 0), new GridPos(0, 8, 3)),
                ShapeMode.WALL, ShapeOptions.DEFAULT, OAK, true, FLAT);
        PlannedChange ground = plan.changes().get(new GridPos(0, 4, 0));
        assertEquals(ChangeKind.REMOVE, ground.kind());
        assertEquals("minecraft:air", ground.state().id());
        assertFalse(plan.changes().containsKey(new GridPos(0, 8, 0))); // already air
        assertEquals(2 * 4, plan.size()); // y 4,5 x z 0..3
    }

    @Test
    void cubeUsesThirdAnchorHeight() {
        EditPlan plan = ShapePlanBuilder.build(
                List.of(new GridPos(0, 6, 0), new GridPos(2, 6, 2), new GridPos(2, 9, 2)),
                ShapeMode.CUBE, ShapeOptions.DEFAULT, OAK, false, FLAT);
        assertEquals(3 * 3 * 4, plan.size()); // 3x3 base, y 6..9, all air
    }

    @Test
    void circleRadiusFromDistance() {
        EditPlan plan = ShapePlanBuilder.build(
                List.of(new GridPos(0, 10, 0), new GridPos(3, 10, 4)), // dist 5
                ShapeMode.CIRCLE, new ShapeOptions(ShapeOptions.Fill.HOLLOW), OAK, false, FLAT);
        assertTrue(plan.changes().containsKey(new GridPos(5, 10, 0)));
        assertTrue(plan.changes().containsKey(new GridPos(0, 10, 5)));
    }

    @Test
    void wrongAnchorCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> ShapePlanBuilder.build(
                List.of(new GridPos(0, 0, 0)), ShapeMode.LINE, ShapeOptions.DEFAULT, OAK, false, FLAT));
    }

    @Test
    void deterministicPlans() {
        List<GridPos> anchors = List.of(new GridPos(-4, 7, 9), new GridPos(1, 7, 12));
        EditPlan p1 = ShapePlanBuilder.build(anchors, ShapeMode.SPHERE, ShapeOptions.DEFAULT, OAK, false, FLAT);
        EditPlan p2 = ShapePlanBuilder.build(anchors, ShapeMode.SPHERE, ShapeOptions.DEFAULT, OAK, false, FLAT);
        assertEquals(p1.changes(), p2.changes());
        assertEquals(List.copyOf(p1.changes().keySet()), List.copyOf(p2.changes().keySet())); // same order
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (compile failure)

- [ ] **Step 3: Implement**

```java
package com.lwos.shape;

import com.lwos.geometry.WorldView;
import com.lwos.plan.BlockStateRef;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a shape gesture (anchors + mode + options) to an {@link EditPlan} (spec §4).
 * The client preview and the server's ShapeRequestPacket handler both call this over
 * their own {@link WorldView}, so preview == apply holds by construction. Deterministic:
 * no RNG, no clocks — identical anchors + identical world answers = identical plan.
 */
public final class ShapePlanBuilder {
    private static final BlockStateRef AIR = new BlockStateRef("minecraft:air");

    private ShapePlanBuilder() { }

    public static EditPlan build(List<GridPos> anchors, ShapeMode mode, ShapeOptions options,
                                 BlockStateRef material, boolean breakMode, WorldView world) {
        if (anchors.size() != mode.clickCount()) {
            throw new IllegalArgumentException(mode + " needs " + mode.clickCount()
                    + " anchors, got " + anchors.size());
        }
        boolean hollow = mode.supportsFill() && options.hollow();
        List<GridPos> cells = rasterize(anchors, mode, hollow);

        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (GridPos p : cells) {
            boolean airHere = isAirLike(world.blockIdAt(p.x(), p.y(), p.z()));
            if (breakMode) {
                if (!airHere) changes.put(p, new PlannedChange(p, ChangeKind.REMOVE, AIR));
            } else {
                if (airHere) changes.put(p, new PlannedChange(p, ChangeKind.PLACE, material));
            }
        }
        return new EditPlan(changes);
    }

    private static List<GridPos> rasterize(List<GridPos> anchors, ShapeMode mode, boolean hollow) {
        GridPos a = anchors.get(0);
        GridPos b = anchors.get(1);
        return switch (mode) {
            case LINE -> ShapeGeometry.line(a, b);
            case WALL, FLOOR -> ShapeGeometry.rect(a, b, hollow);
            case CUBE -> ShapeGeometry.box(a, new GridPos(b.x(), anchors.get(2).y(), b.z()), hollow);
            case CIRCLE -> ShapeGeometry.circle(a, horizontalRadius(a, b), hollow);
            case SPHERE -> ShapeGeometry.sphere(a, horizontalRadius(a, b), hollow);
        };
    }

    /** Radius = rounded horizontal distance center → radius point (both aim on the center's Y plane). */
    private static int horizontalRadius(GridPos center, GridPos edge) {
        double dx = edge.x() - center.x(), dz = edge.z() - center.z();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    private static boolean isAirLike(String id) {
        return "minecraft:air".equals(id) || "minecraft:cave_air".equals(id) || "minecraft:void_air".equals(id);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.shape.ShapePlanBuilderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(shape): ShapePlanBuilder — anchors to EditPlan, place/break modes"
```

---

### Task 5: ShapeTool + ToolType expansion + ToolManager routing

**Files:**
- Create: `src/main/java/com/lwos/tool/ShapeTool.java`
- Modify: `src/main/java/com/lwos/tool/ToolType.java` (full rewrite below)
- Modify: `src/main/java/com/lwos/tool/ToolManager.java`
- Test: `src/test/java/com/lwos/tool/ShapeToolTest.java`

**Interfaces:**
- Produces `ToolType`: `PATH, TERRAIN, LINE, WALL, FLOOR, CUBE, CIRCLE, SPHERE`, each with `displayName()`, `iconIndex()` (path=0, terrain=4, line=1, circle=2; wall=5, floor=6, cube=7, sphere=8), `ShapeMode shapeMode()` (null for PATH/TERRAIN). `color()` is deleted (nothing uses it after the wheel stopped in the icons follow-up).
- Produces `ShapeTool`: `enum State { IDLE, ANCHORED, BASE_DONE }`; `state()`, `anchors()` (unmodifiable `List<GridPos>`), `breakMode()`, `options()`, `material()`, `revision()`;
  `boolean addAnchor(GridPos p, boolean asBreak)` (returns false if a mixed-intent click — which cancels instead), `boolean isComplete(ShapeMode mode)` (anchors == clickCount-1, i.e. next click commits), `cycleFill()`, `setMaterial(BlockStateRef)`, `clear()`, `bumpRevision()`.
- Produces `ToolManager`: `isShapeToolActive()` (enabled && selected.shapeMode() != null), `currentShape()`, `activeShapeMode()`; `cycle(...)` and `toggleEnabled()` clear the shape tool.

- [ ] **Step 1: Write the failing test**

```java
package com.lwos.tool;

import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeMode;
import com.lwos.shape.ShapeOptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShapeToolTest {
    @Test
    void anchorAdvancesState() {
        ShapeTool t = new ShapeTool();
        assertEquals(ShapeTool.State.IDLE, t.state());
        assertTrue(t.addAnchor(new GridPos(0, 0, 0), false));
        assertEquals(ShapeTool.State.ANCHORED, t.state());
        assertFalse(t.breakMode());
    }

    @Test
    void mixedIntentClickCancels() {
        ShapeTool t = new ShapeTool();
        t.addAnchor(new GridPos(0, 0, 0), false);
        assertFalse(t.addAnchor(new GridPos(1, 0, 0), true)); // left-click mid place-gesture
        assertEquals(ShapeTool.State.IDLE, t.state());
        assertTrue(t.anchors().isEmpty());
    }

    @Test
    void twoClickShapeCompletesAfterOneAnchor() {
        ShapeTool t = new ShapeTool();
        t.addAnchor(new GridPos(0, 0, 0), false);
        assertTrue(t.isComplete(ShapeMode.WALL));   // next click commits
        assertFalse(t.isComplete(ShapeMode.CUBE));  // cube needs a second anchor first
        t.addAnchor(new GridPos(2, 0, 2), false);
        assertEquals(ShapeTool.State.BASE_DONE, t.state());
        assertTrue(t.isComplete(ShapeMode.CUBE));
    }

    @Test
    void fillCycleBumpsRevision() {
        ShapeTool t = new ShapeTool();
        long r = t.revision();
        t.cycleFill();
        assertEquals(ShapeOptions.Fill.HOLLOW, t.options().fill());
        assertTrue(t.revision() > r);
    }

    @Test
    void toolTypeMapping() {
        assertNull(ToolType.PATH.shapeMode());
        assertNull(ToolType.TERRAIN.shapeMode());
        assertEquals(ShapeMode.WALL, ToolType.WALL.shapeMode());
        assertEquals(0, ToolType.PATH.iconIndex());
        assertEquals(4, ToolType.TERRAIN.iconIndex());
        assertEquals(8, ToolType.SPHERE.iconIndex());
    }

    @Test
    void managerRoutesShapes() {
        ToolManager tm = ToolManager.get();
        if (!tm.isEnabled()) tm.toggleEnabled();
        while (tm.selected() != ToolType.WALL) tm.cycle(1);
        assertTrue(tm.isShapeToolActive());
        assertEquals(ShapeMode.WALL, tm.activeShapeMode());
        assertFalse(tm.isPathToolActive());
        tm.currentShape().addAnchor(new GridPos(0, 0, 0), false);
        tm.cycle(1); // switching tools abandons the gesture
        assertEquals(ShapeTool.State.IDLE, tm.currentShape().state());
        while (tm.selected() != ToolType.PATH) tm.cycle(1);
        if (tm.isEnabled()) tm.toggleEnabled(); // restore singleton state for other tests
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (compile failure)

- [ ] **Step 3: Implement**

`ToolType.java` (full replacement):

```java
package com.lwos.tool;

import com.lwos.shape.ShapeMode;

/**
 * The tools shown in the Alt+Scroll wheel. PATH and TERRAIN have bespoke tools; the six
 * shape entries all route to the shared {@link ShapeTool}, parameterized by shapeMode.
 * iconIndex is the widgets.png strip slot (16x16 glyphs; indices 0..4 at v=64, 5..8 at
 * v=80) — decoupled from ordinal() so existing sheet art never moves (FILL's old slot 3
 * is orphaned, its bucket art stays in the sheet unused).
 */
public enum ToolType {
    PATH("Path", 0, null),
    TERRAIN("Terrain", 4, null),
    LINE("Line", 1, ShapeMode.LINE),
    WALL("Wall", 5, ShapeMode.WALL),
    FLOOR("Floor", 6, ShapeMode.FLOOR),
    CUBE("Cube", 7, ShapeMode.CUBE),
    CIRCLE("Circle", 2, ShapeMode.CIRCLE),
    SPHERE("Sphere", 8, ShapeMode.SPHERE);

    private final String displayName;
    private final int iconIndex;
    private final ShapeMode shapeMode;

    ToolType(String displayName, int iconIndex, ShapeMode shapeMode) {
        this.displayName = displayName;
        this.iconIndex = iconIndex;
        this.shapeMode = shapeMode;
    }

    public String displayName() { return displayName; }
    public int iconIndex() { return iconIndex; }
    /** The shape this tool drives, or null for the bespoke path/terrain tools. */
    public ShapeMode shapeMode() { return shapeMode; }
}
```

`ShapeTool.java`:

```java
package com.lwos.tool;

import com.lwos.plan.BlockStateRef;
import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeMode;
import com.lwos.shape.ShapeOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shape gesture state machine (spec §5): anchors accumulate until the mode's clickCount
 * is reached (the final click is supplied at commit time from the live aim target).
 * One instance serves all six shape ToolTypes. Pure — no Minecraft imports.
 */
public class ShapeTool {
    public enum State { IDLE, ANCHORED, BASE_DONE }

    private final List<GridPos> anchors = new ArrayList<>();
    private State state = State.IDLE;
    private boolean breakMode = false;
    private ShapeOptions options = ShapeOptions.DEFAULT;
    private BlockStateRef material = new BlockStateRef("minecraft:stone");
    private long revision = 0;

    public State state() { return state; }
    public List<GridPos> anchors() { return Collections.unmodifiableList(anchors); }
    public boolean breakMode() { return breakMode; }
    public ShapeOptions options() { return options; }
    public BlockStateRef material() { return material; }

    /** Monotonic counter bumped on every mutation; the shape preview cache keys on it. */
    public long revision() { return revision; }

    /**
     * Adds an anchor. A click of the opposite intent mid-gesture (asBreak != breakMode)
     * cancels instead — one gesture, one intent (spec §2). Returns whether the anchor
     * was accepted.
     */
    public boolean addAnchor(GridPos p, boolean asBreak) {
        if (state != State.IDLE && asBreak != breakMode) {
            clear();
            return false;
        }
        if (state == State.IDLE) breakMode = asBreak;
        anchors.add(p);
        state = anchors.size() == 1 ? State.ANCHORED : State.BASE_DONE;
        revision++;
        return true;
    }

    /** True when the NEXT click supplies the final anchor and commits. */
    public boolean isComplete(ShapeMode mode) {
        return state != State.IDLE && anchors.size() == mode.clickCount() - 1;
    }

    /** M key: FILLED <-> HOLLOW. A persistent preference, unaffected by clear(). */
    public void cycleFill() {
        options = options.cycleFill();
        revision++;
    }

    /** Captured from the main hand at first anchor (place gestures only). */
    public void setMaterial(BlockStateRef material) {
        this.material = material;
        revision++;
    }

    public void clear() {
        anchors.clear();
        state = State.IDLE;
        breakMode = false;
        revision++;
    }

    /** Forces the next preview rebuild (e.g. after a commit changed the world under it). */
    public void bumpRevision() { revision++; }
}
```

`ToolManager.java` — add the shape tool alongside the existing fields/methods:

```java
    private final ShapeTool shapeTool = new ShapeTool();

    public void toggleEnabled() {
        enabled = !enabled;
        if (!enabled) {
            pathTool.clear();  // leaving builder mode discards the in-progress path
            shapeTool.clear(); // ... and any in-progress shape gesture
        }
    }

    public void cycle(int dir) {
        ToolType[] all = ToolType.values();
        int i = (selected.ordinal() + Integer.signum(dir) + all.length) % all.length;
        selected = all[i];
        shapeTool.clear(); // switching tools abandons the gesture (anchors are mode-specific)
    }

    public boolean isShapeToolActive() { return enabled && selected.shapeMode() != null; }

    public ShapeTool currentShape() { return shapeTool; }

    /** The ShapeMode of the selected tool, or null when a bespoke tool is selected. */
    public com.lwos.shape.ShapeMode activeShapeMode() { return selected.shapeMode(); }
```

(Keep the existing `toggleEnabled` body's comment style — replace the method rather than duplicating it.)

- [ ] **Step 4: Run tests, including the whole pure suite (ToolType consumers must still compile — `ToolWheelOverlay` still references `ordinal()`-based icon math and compiles because the constants exist; it is re-pointed in Task 7)**

Run: `./gradlew test --tests "com.lwos.tool.*"`
Expected: PASS. Then `./gradlew compileJava` — if `ToolType.color()` removal breaks any client class, delete the dead usage (the wheel stopped using color in the icons follow-up).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(tool): ShapeTool gesture state machine; ToolType grows six shape tools"
```

---

### Task 6: Spring utility

**Files:**
- Create: `src/main/java/com/lwos/client/anim/Spring.java`
- Create: `src/main/java/com/lwos/client/anim/SpringVec3.java`
- Test: `src/test/java/com/lwos/client/anim/SpringTest.java`

**Interfaces:**
- Produces `Spring`: `Spring(float zeta, float hz)`, `snapTo(float v)`, `setTarget(float t)`, `update(float dtSeconds)`, `value()`, `target()`, `boolean isSettled()` (|value-target| and |velocity| under 1e-3). Constants `ZETA = 0.8f`, `HZ = 3.0f` for the house feel.
- Produces `SpringVec3`: three `Spring`s, `snapTo(double x,y,z)`, `setTarget(double x,y,z)`, `update(float dt)`, `x()/y()/z()`.
- No Minecraft imports (lives under client for ownership, but pure math — unit-testable headless).

- [ ] **Step 1: Write the failing test**

```java
package com.lwos.client.anim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpringTest {
    @Test
    void convergesToTarget() {
        Spring s = new Spring(Spring.ZETA, Spring.HZ);
        s.snapTo(0f);
        s.setTarget(1f);
        for (int i = 0; i < 600; i++) s.update(1f / 60f); // 10 simulated seconds
        assertEquals(1f, s.value(), 1e-3);
        assertTrue(s.isSettled());
    }

    @Test
    void underdampedOvershoots() {
        Spring s = new Spring(0.8f, 3.0f);
        s.snapTo(0f);
        s.setTarget(1f);
        float max = 0f;
        for (int i = 0; i < 300; i++) { s.update(1f / 60f); max = Math.max(max, s.value()); }
        assertTrue(max > 1.005f, "zeta=0.8 must visibly overshoot, peaked at " + max);
        assertTrue(max < 1.10f, "overshoot should stay subtle, peaked at " + max);
    }

    @Test
    void largeDtClampedStable() {
        Spring s = new Spring(Spring.ZETA, Spring.HZ);
        s.snapTo(0f);
        s.setTarget(1f);
        for (int i = 0; i < 100; i++) s.update(5f); // absurd dt, must not explode
        assertTrue(Float.isFinite(s.value()));
        assertEquals(1f, s.value(), 0.05f);
    }

    @Test
    void snapKillsMotion() {
        Spring s = new Spring(Spring.ZETA, Spring.HZ);
        s.setTarget(5f);
        s.update(0.1f);
        s.snapTo(2f);
        assertEquals(2f, s.value(), 0f);
        assertEquals(2f, s.target(), 0f);
        assertTrue(s.isSettled());
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (compile failure)

- [ ] **Step 3: Implement**

`Spring.java`:

```java
package com.lwos.client.anim;

/**
 * Underdamped spring follower (spec §3): x'' = w^2 (target - x) - 2 z w x', integrated
 * semi-implicitly with real frame time. The house motion feel is ZETA/HZ; the shape
 * preview and (sub-project C) the 2D UI tweens share it. Presentation-only by contract —
 * never feeds plan geometry. No Minecraft imports; headless-testable.
 */
public final class Spring {
    /** House damping ratio (slight underdamp = one visible bounce). */
    public static final float ZETA = 0.8f;
    /** House response frequency in Hz. */
    public static final float HZ = 3.0f;
    /** Max integration step; larger frame gaps are clamped for stability. */
    private static final float MAX_DT = 0.05f;
    private static final float SETTLE_EPS = 1e-3f;

    private final float omega; // angular frequency, 2*pi*hz
    private final float zeta;
    private float value;
    private float velocity;
    private float target;

    public Spring(float zeta, float hz) {
        this.zeta = zeta;
        this.omega = (float) (2.0 * Math.PI * hz);
    }

    public void snapTo(float v) {
        value = v;
        target = v;
        velocity = 0f;
    }

    public void setTarget(float t) { target = t; }

    public void update(float dtSeconds) {
        float dt = Math.min(dtSeconds, MAX_DT);
        if (dt <= 0f) return;
        float accel = omega * omega * (target - value) - 2f * zeta * omega * velocity;
        velocity += accel * dt;   // semi-implicit Euler: velocity first,
        value += velocity * dt;   // then position from the NEW velocity — stable at 3 Hz
    }

    public float value() { return value; }
    public float target() { return target; }

    public boolean isSettled() {
        return Math.abs(value - target) < SETTLE_EPS && Math.abs(velocity) < SETTLE_EPS;
    }
}
```

`SpringVec3.java`:

```java
package com.lwos.client.anim;

/** Three springs, one per axis — drives spring-following 3D corners (spec §3). */
public final class SpringVec3 {
    private final Spring x = new Spring(Spring.ZETA, Spring.HZ);
    private final Spring y = new Spring(Spring.ZETA, Spring.HZ);
    private final Spring z = new Spring(Spring.ZETA, Spring.HZ);

    public void snapTo(double px, double py, double pz) {
        x.snapTo((float) px);
        y.snapTo((float) py);
        z.snapTo((float) pz);
    }

    public void setTarget(double px, double py, double pz) {
        x.setTarget((float) px);
        y.setTarget((float) py);
        z.setTarget((float) pz);
    }

    public void update(float dtSeconds) {
        x.update(dtSeconds);
        y.update(dtSeconds);
        z.update(dtSeconds);
    }

    public double x() { return x.value(); }
    public double y() { return y.value(); }
    public double z() { return z.value(); }
}
```

Note the 5-second-dt test passes because `update` clamps each call to 50 ms; the loop then converges normally.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.client.anim.SpringTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(client): underdamped Spring/SpringVec3 (zeta 0.8, 3 Hz house motion)"
```

---

### Task 7: Texgen shape icons + JournalTheme + wheel re-pointing

**Files:**
- Modify: `tools/texgen/generate.py` (append inside `gen_widgets`, after the wax scribble ring, before `return img`)
- Modify: `src/main/java/com/lwos/ui/theme/JournalTheme.java`
- Modify: `src/main/java/com/lwos/client/ToolWheelOverlay.java`
- Regenerate: `src/main/resources/assets/lwos/textures/gui/journal/widgets.png`

**Interfaces:**
- Produces: `JournalTheme.TOOL_ICON_ROW2_V = 80` and `static void blitToolIcon(GuiGraphics g, int iconIndex, int x, int y)` — resolves indices 0..4 to (index*16, 64) and 5..8 to ((index-5)*16, 80).
- Consumes: `ToolType.iconIndex()` from Task 5.

- [ ] **Step 1: Verify texgen baseline**

Run: `python tools/texgen/generate.py --verify`
Expected: `OK` for all four sheets. If MISMATCH, STOP — the committed art has drifted; investigate before touching the script.

- [ ] **Step 2: Append the four new icons**

In `gen_widgets`, after the wax-scribble-ring block and before `return img`, append (uses only `d`, no rng draws — but appending after all rng consumers is still the rule):

```python
    # --- shape tool icons row 2 (0,80)..(64,96), 16x16, ToolType.iconIndex() order 5..8 ---
    # WALL (0,80): brick courses in a rectangle
    ox, oy = 0, 80
    d.rectangle([ox + 2, oy + 3, ox + 13, oy + 12], outline=INK + (255,))
    for row, yy in ((0, 6), (1, 9)):
        d.line([(ox + 3, oy + yy), (ox + 12, oy + yy)], fill=INK + (200,))
        for bx in range(ox + 5 + 3 * row, ox + 13, 4):
            d.line([(bx, oy + yy - 2), (bx, oy + yy - 1)], fill=INK + (170,))

    # FLOOR (16,80): receding grid plane
    ox, oy = 16, 80
    d.polygon([(ox + 2, oy + 12), (ox + 6, oy + 5), (ox + 13, oy + 5), (ox + 13, oy + 12)],
              outline=INK + (255,))
    d.line([(ox + 4, oy + 12), (ox + 8, oy + 5)], fill=INK + (170,))
    d.line([(ox + 8, oy + 12), (ox + 10, oy + 5)], fill=INK + (170,))
    d.line([(ox + 3, oy + 9), (ox + 13, oy + 9)], fill=INK + (170,))

    # CUBE (32,80): isometric cube, hidden edges dashed
    ox, oy = 32, 80
    d.polygon([(ox + 8, oy + 2), (ox + 13, oy + 5), (ox + 13, oy + 11),
               (ox + 8, oy + 14), (ox + 3, oy + 11), (ox + 3, oy + 5)],
              outline=INK + (255,))
    d.line([(ox + 3, oy + 5), (ox + 8, oy + 8)], fill=INK + (255,))
    d.line([(ox + 13, oy + 5), (ox + 8, oy + 8)], fill=INK + (255,))
    d.line([(ox + 8, oy + 8), (ox + 8, oy + 14)], fill=INK + (255,))

    # SPHERE (48,80): circle with equator + meridian ellipses
    ox, oy = 48, 80
    d.ellipse([ox + 2, oy + 2, ox + 13, oy + 13], outline=INK + (255,))
    d.arc([ox + 2, oy + 6, ox + 13, oy + 9], 0, 180, fill=INK + (180,))
    d.arc([ox + 6, oy + 2, ox + 9, oy + 13], 90, 270, fill=INK + (180,))
```

- [ ] **Step 3: Regenerate and re-verify**

Run: `python tools/texgen/generate.py && python tools/texgen/generate.py --verify`
Expected: `wrote ... widgets.png`, then all `OK`. Only `widgets.png` may change in git status.

- [ ] **Step 4: JournalTheme + wheel**

Add to `JournalTheme` next to the existing `TOOL_ICON_*` constants:

```java
    public static final int TOOL_ICON_ROW2_V = 80; // shape icons, iconIndex 5..8

    /** Blits the 16x16 tool glyph for a ToolType.iconIndex() slot (rows at v=64 and v=80). */
    public static void blitToolIcon(net.minecraft.client.gui.GuiGraphics g, int iconIndex, int x, int y) {
        int u = (iconIndex % 5) * TOOL_ICON_SIZE;
        int v = iconIndex < 5 ? TOOL_ICON_V : TOOL_ICON_ROW2_V;
        blitRegion(g, u, v, TOOL_ICON_SIZE, TOOL_ICON_SIZE, x, y);
    }
```

In `ToolWheelOverlay.render`, replace the icon blit (the `JournalTheme.blitRegion(g, JournalTheme.TOOL_ICON_U + i * ...)` call) with:

```java
            JournalTheme.blitToolIcon(g, tools[i].iconIndex(), x - 8, y - 8);
```

- [ ] **Step 5: Build + commit**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

```bash
git add -A && git commit -m "feat(ui): shape tool icons (row 2) in texgen + iconIndex-driven wheel blits"
```

---

### Task 8: ShapeAim + ShapeRenderer (preview with springs)

**Files:**
- Create: `src/main/java/com/lwos/client/ShapeAim.java`
- Create: `src/main/java/com/lwos/client/ShapeRenderer.java`
- Modify: `src/main/java/com/lwos/client/PreviewRenderer.java` (transform overload)

**Interfaces:**
- Produces `ShapeAim` (client-bound, static): given `Vec3 eye, Vec3 look`, `GridPos` context —
  - `GridPos aimLine(Vec3 eye, Vec3 look, GridPos anchor)` — snapped endpoint on the best axis line through the anchor (least ray-to-line distance; null if all behind camera).
  - `GridPos aimPlaneY(Vec3 eye, Vec3 look, GridPos anchor)` — ray ∩ horizontal plane `y = anchor.y` (null if parallel/behind).
  - `GridPos aimWall(Vec3 eye, Vec3 look, GridPos anchor, boolean normalIsX)` — ray ∩ vertical plane through the anchor.
  - `Integer aimHeight(Vec3 eye, Vec3 look, GridPos corner)` — closest-approach Y on the vertical line through corner (for cube extrusion).
  - All results extent-clamped to ±`ShapeGeometry.MAX_EXTENT` around the anchor.
- Produces `ShapeRenderer` (event subscriber like `BrushRenderer`): publishes `volatile boolean hasTarget`, `volatile GridPos aimTarget`, `volatile boolean wallNormalIsX` (latched at anchor time by `LwosInputHandler` via `latchWallNormal()`); builds the preview plan (anchors + live aim as the final anchor) through a private `PreviewPlanCache`; renders via the `PreviewRenderer` overload with the spring transform; owns the springs (anchor bounce `Spring`, `SpringVec3` min/max corners).
- Produces `PreviewRenderer.render(EditPlan, PoseStack, Vec3 cam, MultiBufferSource, Transform t)` overload plus `record Transform(double sx, double sy, double sz, double ox, double oy, double oz)` with `static Transform IDENTITY` and `double mapX/Y/Z(double)`; the existing 4-arg `render` delegates with `IDENTITY`.

- [ ] **Step 1: PreviewRenderer overload**

Add inside `PreviewRenderer`:

```java
    /** Per-axis affine presentation map (spring layer): p' = p * s + o. Identity = exact plan. */
    public record Transform(double sx, double sy, double sz, double ox, double oy, double oz) {
        public static final Transform IDENTITY = new Transform(1, 1, 1, 0, 0, 0);
        public double mapX(double x) { return x * sx + ox; }
        public double mapY(double y) { return y * sy + oy; }
        public double mapZ(double z) { return z * sz + oz; }
    }
```

Change the 4-arg `render` to delegate: `render(plan, ps, cam, buffers, Transform.IDENTITY);` and add the 5-arg version — identical body except:

- block translate becomes `ps.translate(t.mapX(pos.x()) - cam.x, t.mapY(pos.y()) - cam.y, t.mapZ(pos.z()) - cam.z);`
- block scale becomes `ps.scale(1.01f * (float) t.sx(), 1.01f * (float) t.sy(), 1.01f * (float) t.sz());`
- carve outline corners map through `t` the same way (`t.mapX(pos.x()) - cam.x` … and `t.mapX(pos.x() + 1) - cam.x` for the far corner, per axis).

The double-precision camera-relative rule is unchanged: map in double world space first, subtract `cam` in double, only then hand floats to the matrix.

- [ ] **Step 2: ShapeAim**

```java
package com.lwos.client;

import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeGeometry;
import net.minecraft.world.phys.Vec3;

/**
 * Construction-plane targeting (spec §2): after the first anchor, aim stops raycasting
 * terrain and intersects the camera ray with the shape's construction plane/axis so
 * shapes stretch into open air. All doubles until the final snap; results clamped to
 * the shape extent cap around the anchor.
 */
public final class ShapeAim {
    private static final double EPS = 1e-5;
    /** Cap on the ray parameter so near-parallel aims can't produce kilometer hits. */
    private static final double MAX_T = 256.0;

    private ShapeAim() { }

    /** Endpoint on the axis line (through anchor) the ray passes closest to. */
    public static GridPos aimLine(Vec3 eye, Vec3 look, GridPos anchor) {
        GridPos best = null;
        double bestDist = Double.MAX_VALUE;
        double[][] axes = { {1, 0, 0}, {0, 1, 0}, {0, 0, 1} };
        for (double[] ax : axes) {
            double[] r = closestOnLine(eye, look, anchor, ax[0], ax[1], ax[2]);
            if (r == null) continue;
            if (r[1] < bestDist) {
                bestDist = r[1];
                int d = (int) Math.round(r[0]);
                best = clamp(anchor, new GridPos(
                        anchor.x() + (int) (ax[0] * d),
                        anchor.y() + (int) (ax[1] * d),
                        anchor.z() + (int) (ax[2] * d)));
            }
        }
        return best;
    }

    /** Ray ∩ horizontal plane y = anchor.y (block-center plane), snapped and clamped. */
    public static GridPos aimPlaneY(Vec3 eye, Vec3 look, GridPos anchor) {
        if (Math.abs(look.y) < EPS) return null;
        double t = (anchor.y() + 0.5 - eye.y) / look.y;
        if (t <= 0 || t > MAX_T) return null;
        return clamp(anchor, new GridPos(
                (int) Math.floor(eye.x + look.x * t),
                anchor.y(),
                (int) Math.floor(eye.z + look.z * t)));
    }

    /** Ray ∩ vertical plane through the anchor (normal X or Z), snapped and clamped. */
    public static GridPos aimWall(Vec3 eye, Vec3 look, GridPos anchor, boolean normalIsX) {
        double denom = normalIsX ? look.x : look.z;
        if (Math.abs(denom) < EPS) return null;
        double planeCoord = (normalIsX ? anchor.x() : anchor.z()) + 0.5;
        double t = (planeCoord - (normalIsX ? eye.x : eye.z)) / denom;
        if (t <= 0 || t > MAX_T) return null;
        return clamp(anchor, normalIsX
                ? new GridPos(anchor.x(), (int) Math.floor(eye.y + look.y * t), (int) Math.floor(eye.z + look.z * t))
                : new GridPos((int) Math.floor(eye.x + look.x * t), (int) Math.floor(eye.y + look.y * t), anchor.z()));
    }

    /** Closest-approach Y on the vertical line through corner — cube extrusion height. */
    public static Integer aimHeight(Vec3 eye, Vec3 look, GridPos corner) {
        double[] r = closestOnLine(eye, look, corner, 0, 1, 0);
        if (r == null) return null;
        int dy = (int) Math.round(r[0]);
        dy = Math.max(-ShapeGeometry.MAX_EXTENT, Math.min(ShapeGeometry.MAX_EXTENT, dy));
        return corner.y() + dy;
    }

    /**
     * Closest points between the camera ray and an axis line through base.
     * Returns { line parameter s, distance between closest points }, or null when the
     * closest approach is behind the camera.
     */
    private static double[] closestOnLine(Vec3 eye, Vec3 look, GridPos base, double ax, double ay, double az) {
        double bx = base.x() + 0.5, by = base.y() + 0.5, bz = base.z() + 0.5;
        double wx = eye.x - bx, wy = eye.y - by, wz = eye.z - bz;
        double a = look.lengthSqr();
        double b = look.x * ax + look.y * ay + look.z * az;
        double c = 1.0; // axis is unit
        double d = look.x * wx + look.y * wy + look.z * wz;
        double e = ax * wx + ay * wy + az * wz;
        double denom = a * c - b * b;
        if (Math.abs(denom) < EPS) return null; // ray parallel to the axis
        double t = (b * e - c * d) / denom;     // along the ray
        double s = (a * e - b * d) / denom;     // along the axis line
        if (t <= 0 || t > MAX_T) return null;
        double px = eye.x + look.x * t - (bx + ax * s);
        double py = eye.y + look.y * t - (by + ay * s);
        double pz = eye.z + look.z * t - (bz + az * s);
        return new double[] { s, Math.sqrt(px * px + py * py + pz * pz) };
    }

    private static GridPos clamp(GridPos anchor, GridPos p) {
        int dx = Math.max(-ShapeGeometry.MAX_EXTENT, Math.min(ShapeGeometry.MAX_EXTENT, p.x() - anchor.x()));
        int dy = Math.max(-ShapeGeometry.MAX_EXTENT, Math.min(ShapeGeometry.MAX_EXTENT, p.y() - anchor.y()));
        int dz = Math.max(-ShapeGeometry.MAX_EXTENT, Math.min(ShapeGeometry.MAX_EXTENT, p.z() - anchor.z()));
        return new GridPos(anchor.x() + dx, anchor.y() + dy, anchor.z() + dz);
    }
}
```

- [ ] **Step 3: ShapeRenderer**

```java
package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.client.anim.Spring;
import com.lwos.client.anim.SpringVec3;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeMode;
import com.lwos.shape.ShapePlanBuilder;
import com.lwos.tool.ShapeTool;
import com.lwos.tool.ToolManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Live shape preview (spec §5): terrain-raycasts the hover target before the first
 * anchor, construction-plane-aims after it, builds the plan through the debounced cache,
 * and renders it with the spring presentation layer (spec §3): anchor outline bounces in,
 * the mesh's bounds chase the exact extents with the house underdamped spring.
 *
 * <p>The aim target is published via volatile statics (BrushRenderer pattern) so
 * LwosInputHandler commits exactly what the ghost shows.
 */
@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShapeRenderer {
    private static final double MAX_TARGET_DISTANCE = 96.0;

    // Published each frame for LwosInputHandler.
    public static volatile boolean hasTarget = false;
    public static volatile GridPos aimTarget = null;
    /** Wall construction plane normal, latched at anchor time from the look direction. */
    public static volatile boolean wallNormalIsX = false;

    private static final PreviewPlanCache CACHE = new PreviewPlanCache();

    private record ShapeKey(long revision, GridPos aim, int modeOrdinal) { }

    // Spring presentation state (reset whenever the gesture restarts).
    private static final Spring anchorBounce = new Spring(Spring.ZETA, Spring.HZ);
    private static final SpringVec3 springMin = new SpringVec3();
    private static final SpringVec3 springMax = new SpringVec3();
    private static long lastGestureRevision = -1;
    private static long lastFrameNanos = 0;

    private ShapeRenderer() { }

    /** Called by LwosInputHandler when the first WALL anchor is placed. */
    public static void latchWallNormal(Vec3 look) {
        wallNormalIsX = Math.abs(look.x) >= Math.abs(look.z);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        hasTarget = false;

        ToolManager tm = ToolManager.get();
        if (!tm.isShapeToolActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        ShapeTool tool = tm.currentShape();
        ShapeMode mode = tm.activeShapeMode();
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);

        GridPos aim = computeAim(mc, tool, mode, eye, look);
        if (aim == null) { renderAnchorsOnly(event, tool); return; }
        aimTarget = aim;
        hasTarget = true;

        float dt = frameDt();

        // Hover/anchor bounce: retrigger the scale spring whenever the gesture (re)starts.
        if (tool.revision() != lastGestureRevision) {
            lastGestureRevision = tool.revision();
            anchorBounce.snapTo(0f);
            anchorBounce.setTarget(1f);
        }
        anchorBounce.update(dt);

        if (tool.state() == ShapeTool.State.IDLE) {
            // No gesture yet: just the bouncing hover outline on the would-be anchor.
            renderOutline(event, aim, anchorBounce.value(), false);
            return;
        }

        // Full anchor list = committed anchors + the live aim as the final anchor.
        List<GridPos> anchors = new ArrayList<>(tool.anchors());
        anchors.add(aim);
        ShapeKey key = new ShapeKey(tool.revision(), aim, mode.ordinal());
        long now = System.currentTimeMillis();
        if (CACHE.needsRebuild(key, now)) {
            CACHE.accept(key, ShapePlanBuilder.build(
                    anchors, mode, tool.options(), tool.material(), tool.breakMode(),
                    ForgeWorldView.INSTANCE), now);
        }
        EditPlan plan = CACHE.last();
        if (plan == null || plan.isEmpty()) return;

        // Exact bounds of the plan -> spring targets; the drawn mesh chases them.
        double[] bounds = planBounds(plan);
        springMin.setTarget(bounds[0], bounds[1], bounds[2]);
        springMax.setTarget(bounds[3], bounds[4], bounds[5]);
        springMin.update(dt);
        springMax.update(dt);

        PreviewRenderer.Transform t = boundsTransform(bounds);
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PreviewRenderer.render(plan, ps, cam, buffers, t);
        // Outline box at the spring bounds (green place / red break).
        drawBoundsBox(event, tool.breakMode());
        buffers.endBatch(RenderType.lines());
    }

    /** Maps exact plan bounds onto the current spring bounds, per axis. */
    private static PreviewRenderer.Transform boundsTransform(double[] b) {
        double ex = Math.max(b[3] - b[0], 1e-6), ey = Math.max(b[4] - b[1], 1e-6), ez = Math.max(b[5] - b[2], 1e-6);
        double sx = (springMax.x() - springMin.x()) / ex;
        double sy = (springMax.y() - springMin.y()) / ey;
        double sz = (springMax.z() - springMin.z()) / ez;
        return new PreviewRenderer.Transform(sx, sy, sz,
                springMin.x() - b[0] * sx, springMin.y() - b[1] * sy, springMin.z() - b[2] * sz);
    }

    private static double[] planBounds(EditPlan plan) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (GridPos p : plan.changes().keySet()) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x() + 1);
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y() + 1);
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z() + 1);
        }
        return new double[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    private static GridPos computeAim(Minecraft mc, ShapeTool tool, ShapeMode mode, Vec3 eye, Vec3 look) {
        if (tool.state() == ShapeTool.State.IDLE) return terrainAnchor(mc, eye, look, tool.breakMode());
        GridPos a = tool.anchors().get(0);
        return switch (mode) {
            case LINE -> ShapeAim.aimLine(eye, look, a);
            case FLOOR, CIRCLE, SPHERE -> ShapeAim.aimPlaneY(eye, look, a);
            case WALL -> ShapeAim.aimWall(eye, look, a, wallNormalIsX);
            case CUBE -> tool.state() == ShapeTool.State.ANCHORED
                    ? ShapeAim.aimPlaneY(eye, look, a)
                    : cubeExtrusionAim(tool, eye, look);
        };
    }

    private static GridPos cubeExtrusionAim(ShapeTool tool, Vec3 eye, Vec3 look) {
        GridPos b = tool.anchors().get(1);
        Integer y = ShapeAim.aimHeight(eye, look, b);
        return y == null ? null : new GridPos(b.x(), y, b.z());
    }

    /** Terrain raycast for the first anchor: place = face-adjacent pos, break = hit block. */
    public static GridPos terrainAnchor(Minecraft mc, Vec3 eye, Vec3 look, boolean forBreak) {
        Vec3 end = eye.add(look.x * MAX_TARGET_DISTANCE, look.y * MAX_TARGET_DISTANCE, look.z * MAX_TARGET_DISTANCE);
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = forBreak ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        return new GridPos(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Bounce-scaled wireframe outline around one block cell. */
    private static void renderOutline(RenderLevelStageEvent event, GridPos p, float scale, boolean red) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double cx = p.x() + 0.5 - cam.x, cy = p.y() + 0.5 - cam.y, cz = p.z() + 0.5 - cam.z;
        double h = 0.5 * Math.max(scale, 0.01) + 0.01;
        AABB box = new AABB(cx - h, cy - h, cz - h, cx + h, cy + h, cz + h);
        LevelRenderer.renderLineBox(ps, buffers.getBuffer(RenderType.lines()), box,
                red ? 1.0f : 0.3f, red ? 0.2f : 1.0f, 0.3f, 1.0f);
        buffers.endBatch(RenderType.lines());
    }

    /** Committed-anchor gizmos while the aim is off-plane (e.g. looking at the sky). */
    private static void renderAnchorsOnly(RenderLevelStageEvent event, ShapeTool tool) {
        for (GridPos p : tool.anchors()) renderOutline(event, p, 1f, tool.breakMode());
    }

    private static void drawBoundsBox(RenderLevelStageEvent event, boolean breakMode) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        AABB box = new AABB(springMin.x() - cam.x, springMin.y() - cam.y, springMin.z() - cam.z,
                            springMax.x() - cam.x, springMax.y() - cam.y, springMax.z() - cam.z);
        LevelRenderer.renderLineBox(ps, buffers.getBuffer(RenderType.lines()), box,
                breakMode ? 1.0f : 0.3f, breakMode ? 0.2f : 1.0f, 0.3f, 1.0f);
    }

    private static float frameDt() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 1f / 60f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return dt;
    }

    /** Snaps the extent springs to the current exact bounds (called when a gesture starts). */
    public static void snapSpringsTo(GridPos p) {
        springMin.snapTo(p.x(), p.y(), p.z());
        springMax.snapTo(p.x() + 1.0, p.y() + 1.0, p.z() + 1.0);
    }
}
```

Note: `snapSpringsTo` is called by the input handler at first anchor so the volume visibly grows OUT of the anchor block (spec: "expands and moves towards the point").

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(client): shape preview — construction-plane aim, spring-chased bounds, bounce-in anchors"
```

---

### Task 9: Input wiring + HUD

**Files:**
- Modify: `src/main/java/com/lwos/client/LwosInputHandler.java`
- Modify: `src/main/java/com/lwos/client/ModeHudOverlay.java`

**Interfaces:**
- Consumes: `ShapeRenderer.hasTarget/aimTarget/latchWallNormal/snapSpringsTo/terrainAnchor`, `ToolManager.isShapeToolActive()/currentShape()/activeShapeMode()`, `ShapeTool` API (Task 5), `ShapeRequestPacket` (Task 10 — write the send-call now; it compiles after Task 10, so Tasks 9 and 10 build+commit together if needed, or stub the send last).
  To keep every commit green: implement Task 10's packet FIRST if you prefer; this plan orders HUD/input before networking only for readability — **build both, then commit both** (subjects below).

- [ ] **Step 1: Input — use (right-click) and attack (left-click)**

In `LwosInputHandler.onUse`, before the path-tool guard, add shape handling:

```java
        if (tm.isShapeToolActive()) {
            handleShapeClick(tm, false);
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
```

In `onAttack`, generalize: the existing body handles the terrain brush; add before it:

```java
        if (ToolManager.get().isShapeToolActive()) {
            event.setSwingHand(false);
            event.setCanceled(true); // the shape tool owns left-click — never swing/break vanilla-style
            handleShapeClick(ToolManager.get(), true);
            return;
        }
```

New private method:

```java
    /** Drives the shape gesture: anchor clicks accumulate; the final click commits (spec §2). */
    private static void handleShapeClick(ToolManager tm, boolean asBreak) {
        com.lwos.tool.ShapeTool tool = tm.currentShape();
        com.lwos.shape.ShapeMode mode = tm.activeShapeMode();
        Minecraft mc = Minecraft.getInstance();

        if (tool.state() == com.lwos.tool.ShapeTool.State.IDLE) {
            // First anchor: terrain raycast; place-gestures also need a block in hand.
            Vec3 eye = mc.player.getEyePosition(1.0f);
            Vec3 look = mc.player.getViewVector(1.0f);
            com.lwos.plan.GridPos anchor = ShapeRenderer.terrainAnchor(mc, eye, look, asBreak);
            if (anchor == null) return;
            if (!asBreak) {
                com.lwos.plan.BlockStateRef material = heldBlock(mc);
                if (material == null) {
                    mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("Hold a block to build shapes"), true);
                    return;
                }
                tool.setMaterial(material);
            }
            tool.addAnchor(anchor, asBreak);
            if (mode == com.lwos.shape.ShapeMode.WALL) ShapeRenderer.latchWallNormal(look);
            ShapeRenderer.snapSpringsTo(anchor);
            return;
        }

        // Mid-gesture: a click of the opposite intent cancels (ShapeTool enforces it).
        if (asBreak != tool.breakMode()) {
            tool.addAnchor(new com.lwos.plan.GridPos(0, 0, 0), asBreak); // rejected -> clears
            return;
        }
        if (!ShapeRenderer.hasTarget) return; // aiming at nothing valid: click does nothing
        com.lwos.plan.GridPos aim = ShapeRenderer.aimTarget;

        if (tool.isComplete(mode)) {
            // Final click: commit with the live aim as the last anchor.
            java.util.List<com.lwos.plan.GridPos> anchors = new java.util.ArrayList<>(tool.anchors());
            anchors.add(aim);
            LwosMod.CHANNEL.sendToServer(new com.lwos.apply.net.ShapeRequestPacket(
                    anchors, mode.ordinal(), tool.options().toJson(),
                    toMaterialJson(tool.material()), tool.breakMode()));
            tool.clear();
            tool.bumpRevision(); // world changed under any lingering preview
        } else {
            tool.addAnchor(aim, asBreak); // cube: base corner locked, extrusion begins
        }
    }

    /** BlockStateRef of the main-hand BlockItem, or null when not holding a placeable block. */
    private static com.lwos.plan.BlockStateRef heldBlock(Minecraft mc) {
        net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return null;
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
        return id == null ? null : new com.lwos.plan.BlockStateRef(id.toString());
    }

    private static String toMaterialJson(com.lwos.plan.BlockStateRef ref) {
        return ref.id(); // v1 materials are bare ids; properties ride defaults (spec §5)
    }
```

- [ ] **Step 2: Key routing**

In `onKey`, make M and Esc per-tool (`TOGGLE_TERRAIN_MODE` is the M mapping, `CANCEL_PATH` the Esc-like cancel):

```java
        while (LwosKeyMappings.TOGGLE_TERRAIN_MODE.consumeClick()) {
            // Same key, per-tool meaning: brush op cycle, shape fill cycle, path terrain-mode cycle.
            if (tm.isTerrainToolActive()) tm.currentBrush().cycleOp();
            else if (tm.isShapeToolActive()) tm.currentShape().cycleFill();
            else tm.currentPath().toggleTerrainMode();
        }
```

and

```java
        while (LwosKeyMappings.CANCEL_PATH.consumeClick()) {
            if (tm.isShapeToolActive()) tm.currentShape().clear();
            else tm.currentPath().clear();
        }
```

Also bump the shape tool alongside the brush on undo/redo (`tm.currentShape().bumpRevision();` next to the existing `tm.currentBrush().bumpRevision();` calls).

- [ ] **Step 3: HUD**

In `ModeHudOverlay.render`, add a branch before the path branch:

```java
        } else if (tm.isShapeToolActive()) {
            com.lwos.tool.ShapeTool shape = tm.currentShape();
            String fill = tm.activeShapeMode().supportsFill()
                    ? (shape.options().hollow() ? " · Hollow" : " · Filled") : "";
            String intent = shape.state() != com.lwos.tool.ShapeTool.State.IDLE && shape.breakMode()
                    ? " · BREAK" : "";
            text = tm.selected().displayName() + fill + intent;
        }
```

- [ ] **Step 4: Compile (with Task 10 done), then commit**

```bash
git add -A && git commit -m "feat(client): shape gesture input — click anchors, M fill cycle, HUD readout"
```

---

### Task 10: LwosServerConfig + ShapeRequestPacket + survival apply

**Files:**
- Create: `src/main/java/com/lwos/LwosServerConfig.java`
- Create: `src/main/java/com/lwos/apply/net/ShapeRequestPacket.java`
- Modify: `src/main/java/com/lwos/apply/PlacementEngine.java` (drops overload)
- Modify: `src/main/java/com/lwos/LwosMod.java` (config + packet registration)
- Test: `src/test/java/com/lwos/apply/ShapeRequestCodecTest.java` — only if the existing packet tests run headless (check `src/test/java/com/lwos/apply/`); Forge `FriendlyByteBuf` needs a booted registry in some setups. If no existing packet test pattern exists headless, SKIP the codec unit test and cover codec symmetry by code review — do not fight the harness.

**Interfaces:**
- Produces `LwosServerConfig`: `static ForgeConfigSpec SPEC`, `static BooleanValue SURVIVAL_MODE` (default false), `static IntValue MAX_BLOCKS_PER_COMMIT` (default 32768, range 1..1_000_000). Registered in `LwosMod` constructor: `ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, LwosServerConfig.SPEC);`
- Produces `ShapeRequestPacket(List<GridPos> anchors, int modeOrdinal, String optionsJson, String materialId, boolean breakMode)` with `encode/decode/handle` and registration `id 4` in `LwosMod.registerPackets`.
- Produces `PlacementEngine.apply(ServerLevel, EditPlan, boolean canReplaceBedrock, boolean dropBrokenBlocks)` — new overload; the old 3-arg delegates with `false`. When dropping: for REMOVE-kind changes, `Block.dropResources(currentState, level, pos)` before `setBlock`.

- [ ] **Step 1: LwosServerConfig**

```java
package com.lwos;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server config (lwos-server.toml, spec §7). survivalMode makes shape commits consume
 * inventory items and drop broken blocks; maxBlocksPerCommit hard-caps every shape
 * commit regardless of mode. Enforcement is entirely server-side.
 */
public final class LwosServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SURVIVAL_MODE;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_COMMIT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("shapes");
        SURVIVAL_MODE = b
                .comment("When true, shape placements consume matching items from the player's",
                         "inventory (whole commit rejected on shortfall) and shape breaks drop items.")
                .define("survivalMode", false);
        MAX_BLOCKS_PER_COMMIT = b
                .comment("Hard cap on blocks a single shape commit may change.")
                .defineInRange("maxBlocksPerCommit", 32768, 1, 1_000_000);
        b.pop();
        SPEC = b.build();
    }

    private LwosServerConfig() { }
}
```

In `LwosMod` constructor (next to existing setup):

```java
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.SERVER, LwosServerConfig.SPEC);
```

- [ ] **Step 2: PlacementEngine drops overload**

Replace the 3-arg `apply` with:

```java
    public static List<BlockSnapshot> apply(ServerLevel level, EditPlan plan, boolean canReplaceBedrock) {
        return apply(level, plan, canReplaceBedrock, false);
    }

    /**
     * @param dropBrokenBlocks survival-mode shape breaks: REMOVE-kind changes drop their
     *                         block's loot before the air write (spec §7).
     */
    public static List<BlockSnapshot> apply(ServerLevel level, EditPlan plan,
                                            boolean canReplaceBedrock, boolean dropBrokenBlocks) {
        List<BlockSnapshot> priors = new ArrayList<>();
        for (PlannedChange change : plan.changes().values()) {
            BlockState state = resolve(change.state());
            if (state == null) continue; // unknown block id — skip rather than crash
            GridPos p = change.pos();
            BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
            BlockState current = level.getBlockState(pos);
            if (!canReplaceBedrock && current.is(Blocks.BEDROCK)) continue;
            if (dropBrokenBlocks && change.kind() == com.lwos.plan.ChangeKind.REMOVE && !current.isAir()) {
                Block.dropResources(current, level, pos, level.getBlockEntity(pos));
            }
            priors.add(new BlockSnapshot(p, toRef(current)));
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        return priors;
    }
```

(The old body read `level.getBlockState(pos)` twice conceptually — keep the single `current` local as above.)

- [ ] **Step 3: ShapeRequestPacket**

```java
package com.lwos.apply.net;

import com.lwos.LwosServerConfig;
import com.lwos.apply.PlacementEngine;
import com.lwos.apply.ServerWorldView;
import com.lwos.plan.BlockStateRef;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeGeometry;
import com.lwos.shape.ShapeMode;
import com.lwos.shape.ShapeOptions;
import com.lwos.shape.ShapePlanBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client -> server "commit this shape" request (spec §6). Intent only: anchors, mode,
 * options, material id, break flag — never a block list. The server re-derives the plan
 * via the same {@link ShapePlanBuilder#build} the preview used (preview == apply), then
 * enforces the survival rules (spec §7) before applying.
 */
public record ShapeRequestPacket(List<GridPos> anchors, int modeOrdinal, String optionsJson,
                                 String materialId, boolean breakMode) {
    /** Max anchor distance from the committing player (matches client reach + shape extent). */
    private static final double MAX_ANCHOR_DISTANCE = 192.0;

    public static void encode(ShapeRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.anchors().size());
        for (GridPos p : msg.anchors()) {
            buf.writeInt(p.x());
            buf.writeInt(p.y());
            buf.writeInt(p.z());
        }
        buf.writeVarInt(msg.modeOrdinal());
        buf.writeUtf(msg.optionsJson(), 256);
        buf.writeUtf(msg.materialId(), 256);
        buf.writeBoolean(msg.breakMode());
    }

    public static ShapeRequestPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 1 || n > 3) throw new IllegalArgumentException("shape anchor count out of range: " + n);
        List<GridPos> anchors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) anchors.add(new GridPos(buf.readInt(), buf.readInt(), buf.readInt()));
        int mode = buf.readVarInt();
        if (mode < 0 || mode >= ShapeMode.values().length) {
            throw new IllegalArgumentException("shape mode ordinal out of range: " + mode);
        }
        return new ShapeRequestPacket(anchors, mode, buf.readUtf(256), buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(ShapeRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            // Authoritative access gate: the client-side check is UX only.
            if (!com.lwos.LwosAccess.isAllowed(sender.getGameProfile().getName())) return;

            ShapeMode mode = ShapeMode.values()[msg.modeOrdinal()];
            if (msg.anchors().size() != mode.clickCount()) return;
            if (!anchorsValid(sender, msg.anchors())) return;

            ServerLevel level = sender.serverLevel();
            ShapeOptions options = ShapeOptions.fromJson(msg.optionsJson());
            BlockStateRef material = new BlockStateRef(msg.materialId());

            EditPlan plan = ShapePlanBuilder.build(msg.anchors(), mode, options, material,
                    msg.breakMode(), new ServerWorldView(level));
            if (plan.isEmpty()) return;

            int cap = LwosServerConfig.MAX_BLOCKS_PER_COMMIT.get();
            if (plan.size() > cap) {
                sender.displayClientMessage(Component.literal(
                        "Shape too large: " + plan.size() + " blocks (cap " + cap + ")"), true);
                return;
            }

            boolean survival = LwosServerConfig.SURVIVAL_MODE.get() && !sender.isCreative();
            if (survival && !msg.breakMode() && !consumeMaterials(sender, material, plan.size())) {
                return; // consumeMaterials reported the shortfall
            }

            List<com.lwos.apply.UndoHistory.BlockSnapshot> priors = PlacementEngine.apply(
                    level, plan, sender.isCreative(), survival && msg.breakMode());
            if (!priors.isEmpty()) com.lwos.apply.LwosServerState.UNDO.push(sender.getUUID(), priors);
            sender.displayClientMessage(Component.literal(
                    (msg.breakMode() ? "Broke " : "Placed ") + priors.size() + " blocks"), true);
        });
        context.setPacketHandled(true);
    }

    private static boolean anchorsValid(ServerPlayer sender, List<GridPos> anchors) {
        GridPos first = anchors.get(0);
        double dx = first.x() - sender.getX(), dy = first.y() - sender.getY(), dz = first.z() - sender.getZ();
        if (dx * dx + dy * dy + dz * dz > MAX_ANCHOR_DISTANCE * MAX_ANCHOR_DISTANCE) return false;
        for (GridPos p : anchors) {
            if (sender.serverLevel().isOutsideBuildHeight(p.y())) return false;
            if (Math.abs(p.x() - first.x()) > ShapeGeometry.MAX_EXTENT
                    || Math.abs(p.y() - first.y()) > ShapeGeometry.MAX_EXTENT
                    || Math.abs(p.z() - first.z()) > ShapeGeometry.MAX_EXTENT) return false;
        }
        return true;
    }

    /**
     * Survival place cost: `count` items matching the material's block, taken from the
     * player's inventory. All-or-nothing (spec §7 "no half-shapes"): on shortfall nothing
     * is consumed and the commit is rejected with an actionbar message.
     */
    private static boolean consumeMaterials(ServerPlayer player, BlockStateRef material, int count) {
        var block = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(material.id()));
        if (block == null) return false;
        var item = block.asItem();
        if (item == net.minecraft.world.item.Items.AIR) return false;

        int have = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() == block) have += stack.getCount();
        }
        if (have < count) {
            player.displayClientMessage(Component.literal(
                    "Need " + count + " × " + item.getDescription().getString() + " (have " + have + ")"), true);
            return false;
        }
        int remaining = count;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining == 0) break;
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() == block) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.getInventory().setChanged();
        return true;
    }
}
```

- [ ] **Step 4: Register the packet**

In `LwosMod.registerPackets` after the brush registration:

```java
        CHANNEL.registerMessage(id++, ShapeRequestPacket.class,
                ShapeRequestPacket::encode, ShapeRequestPacket::decode, ShapeRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
```

- [ ] **Step 5: Full build + all tests**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew build`
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(net,apply): ShapeRequestPacket with survival-mode enforcement + server config"
```

---

### Task 11: Verification, push, playtest checklist

- [ ] **Step 1: Purity grep** (shape package must be Minecraft-free)

Run: `grep -rn "net\.minecraft\|com\.mojang\|net\.minecraftforge" src/main/java/com/lwos/shape/ src/main/java/com/lwos/tool/ && echo LEAK || echo CLEAN`
Expected: `CLEAN` (the known `geometry/Vec3d.java` javadoc false-positive doesn't apply to these dirs).

- [ ] **Step 2: Full build**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Push**

```bash
git push origin main
```

- [ ] **Step 4: Record the manual playtest checklist** (for the next runClient session; do not block the push on it)

Append to this plan file's end, committed:

```markdown
## Manual playtest checklist (pending)
- [ ] Each shape (Line/Wall/Floor/Cube/Circle/Sphere): place at small + large size; preview matches commit.
- [ ] Left-click break for each shape; red preview; drops only when survivalMode=true.
- [ ] Mid-air stretching past terrain; extent clamp at 64.
- [ ] M cycles Filled/Hollow; HUD plate updates; Line unaffected.
- [ ] Cube 3-click flow: base rectangle then vertical extrusion.
- [ ] Mixed-intent click cancels the gesture.
- [ ] Undo/redo across shape commits (Ctrl+Z / Ctrl+Y).
- [ ] survivalMode=true on dev server: item consumption, shortfall rejection message, cap message.
- [ ] Spring feel: anchor bounce-in, extent chase with slight overshoot, settles < ~0.5 s.
- [ ] Far from origin (±1,000,000): preview stays textured (no white slivers).
- [ ] GUI scales 1–4: wheel shows 8 icons, HUD text readable.
```

## Manual playtest checklist (pending)
- [ ] Each shape (Line/Wall/Floor/Cube/Circle/Sphere): place at small + large size; preview matches commit.
- [ ] Left-click break for each shape; red preview; drops only when survivalMode=true.
- [ ] Mid-air stretching past terrain; extent clamp at 64.
- [ ] M cycles Filled/Hollow; HUD plate updates; Line unaffected.
- [ ] Cube 3-click flow: base rectangle then vertical extrusion.
- [ ] Mixed-intent click cancels the gesture.
- [ ] Undo/redo across shape commits (Ctrl+Z / Ctrl+Y).
- [ ] survivalMode=true on dev server: item consumption, shortfall rejection message, cap message.
- [ ] Spring feel: anchor bounce-in, extent chase with slight overshoot, settles < ~0.5 s.
- [ ] Far from origin (±1,000,000): preview stays textured (no white slivers).
- [ ] GUI scales 1–4: wheel shows 8 icons, HUD text readable.
