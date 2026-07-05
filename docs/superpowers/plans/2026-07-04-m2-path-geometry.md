# Milestone 2 — Path Geometry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Path tool a constant, player-adjustable **width**, make the curve **hug terrain height** between control points instead of just interpolating raw click heights, and produce the project's **first real `EditPlan`** — an immutable, deterministic, pure data structure describing which ground columns the path covers — with **zero blocks changed** (M2 stays computational; M3 is where blocks first get placed on commit).

**Architecture:** Extends the M1 pure geometry core with a read-only `WorldView` interface (`geometry/`) that lets pure code ask "what's the surface height at this column?" without importing Minecraft, a `TerrainSampler` that snaps sampled points to that surface, and a `PathMask` that unions per-sample discs (radius = width/2) into an occupancy field. A new pure `plan/` package (`GridPos`, `ChangeKind`, `PlannedChange`, `EditPlan`, `EditPlanBuilder`) runs geometry → mask → plan as a single deterministic pipeline. On the Forge side, `ForgeWorldView` adapts `WorldView` to the live Minecraft level (client-only, manually verified, not unit tested — same split M1 established for Forge glue), and `PathRenderer` is extended to draw the width ribbon and the plan's footprint as debug lines only.

**Tech Stack:** Java 17, MinecraftForge 1.20.1 (Forge 47.4.10), ForgeGradle, JUnit 5 (Jupiter), JOML (bundled with MC), LWJGL/GLFW (bundled). Same as M1 — no new dependencies.

## Global Constraints

- **Loader/version:** MinecraftForge, Minecraft **1.20.1**, Forge **47.4.10**, Java **17**. (Spec §2)
- **Group / base package:** `com.lwos`. **Mod id:** `lwos`.
- **Compute/apply boundary (spec §3.6, hard rule), extended to `plan/`:** classes in `com.lwos.geometry.*` MUST import only `java.*` and other `com.lwos.geometry.*` types — never `net.minecraft.*`, `net.minecraftforge.*`, `org.joml.*`, or `com.mojang.*`. **M2 extends this same rule to `com.lwos.plan.*`**: it may import only `java.*`, `com.lwos.geometry.*`, and other `com.lwos.plan.*` types. This is why `plan/`'s block coordinate type is a local `GridPos` record, not Minecraft's `BlockPos` — the mapping to `net.minecraft.core.BlockPos` is deferred to M3's `apply/` layer, which is allowed to depend on Minecraft. `com.lwos.tool.*` continues to import only `java.*` and `com.lwos.geometry.*`.
- **Determinism (spec §3.2):** same control points + same width/spacing + same `WorldView` answers → byte-identical `EditPlan`. No stage may read wall-clock or `Random`. M2 introduces no randomness.
- **Zero placement (spec §9, M2 is still computational):** M2 must not write, remove, or modify any block or terrain. `EditPlan` is produced and rendered as debug lines/outlines only — there is still no `PlacementEngine`, no translucent preview mesh (that's M3). Blocks are first placed on commit in M3.
- **Client-guarded:** all new Forge event subscribers / glue classes that touch client-only classes are registered/scoped under `Dist.CLIENT`.

---

### Task 1: `PathSample` + width-aware sampling + ribbon edges (pure)

Attach a width to each resampled centerline point, and add a pure helper that turns a width-carrying centerline into left/right boundary curves (used later purely for rendering the width visually — no new occupancy logic here).

**Files:**
- Create: `src/main/java/com/lwos/geometry/PathSample.java`
- Create: `src/main/java/com/lwos/geometry/PathRibbon.java`
- Modify: `src/main/java/com/lwos/geometry/PathSampler.java` (add `sampleWithWidth`)
- Test: `src/test/java/com/lwos/geometry/PathSamplerTest.java` (add cases)
- Test: `src/test/java/com/lwos/geometry/PathRibbonTest.java`

**Interfaces:**
- Consumes: `Vec3d` (existing), `PathSampler.sample(List<Vec3d>, int)` (existing, unchanged).
- Produces:
  - `record PathSample(Vec3d position, double width)`.
  - `static List<PathSample> PathSampler.sampleWithWidth(List<Vec3d> controlPoints, double spacing, double width)` — resamples via the existing `sample(controlPoints, spacing)` centerline logic, then wraps every point with the given constant `width`.
  - `class PathRibbon` with `record Edges(List<Vec3d> left, List<Vec3d> right)` and `static Edges compute(List<PathSample> samples)` — for each sample, offsets it perpendicular to the local direction (estimated from neighbors) by `±width/2` in the XZ plane.

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/com/lwos/geometry/PathSamplerTest.java` (inside the existing `PathSamplerTest` class, after the existing tests):

```java
    @Test
    void sampleWithWidthCarriesConstantWidth() {
        List<Vec3d> controls = List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0));
        List<PathSample> out = PathSampler.sampleWithWidth(controls, 2.0, 4.0);
        assertFalse(out.isEmpty());
        for (PathSample s : out) {
            assertEquals(4.0, s.width(), EPS);
        }
        assertEquals(0.0, out.get(0).position().x(), EPS);
        assertEquals(10.0, out.get(out.size() - 1).position().x(), EPS);
    }
```

Create `src/test/java/com/lwos/geometry/PathRibbonTest.java`:

```java
package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PathRibbonTest {
    private static final double EPS = 1e-6;

    @Test
    void straightLineAlongXOffsetsInZ() {
        List<PathSample> samples = List.of(
                new PathSample(new Vec3d(0, 0, 0), 4.0),
                new PathSample(new Vec3d(5, 0, 0), 4.0),
                new PathSample(new Vec3d(10, 0, 0), 4.0));
        PathRibbon.Edges edges = PathRibbon.compute(samples);
        assertEquals(3, edges.left().size());
        assertEquals(3, edges.right().size());
        for (int i = 0; i < 3; i++) {
            assertEquals(2.0, edges.left().get(i).z(), EPS);
            assertEquals(-2.0, edges.right().get(i).z(), EPS);
            assertEquals(samples.get(i).position().x(), edges.left().get(i).x(), EPS);
        }
    }

    @Test
    void singleSampleDoesNotCrash() {
        List<PathSample> samples = List.of(new PathSample(new Vec3d(1, 2, 3), 2.0));
        PathRibbon.Edges edges = PathRibbon.compute(samples);
        assertEquals(1, edges.left().size());
        assertEquals(1, edges.right().size());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.lwos.geometry.PathSamplerTest" --tests "com.lwos.geometry.PathRibbonTest"`
Expected: FAIL / compile error — `PathSample`, `PathRibbon`, and `sampleWithWidth` do not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/lwos/geometry/PathSample.java`:

```java
package com.lwos.geometry;

/** A resampled centerline point carrying a path width (spec §4.2, "PathSampler carries width/profile/bank per sample"). */
public record PathSample(Vec3d position, double width) { }
```

Add to `src/main/java/com/lwos/geometry/PathSampler.java` (inside the `PathSampler` class, after `sample`):

```java
    /** Resamples the curve, attaching a constant width to every sample (M2: width is constant, not yet variable). */
    public static List<PathSample> sampleWithWidth(List<Vec3d> controlPoints, double spacing, double width) {
        List<Vec3d> centerline = sample(controlPoints, spacing);
        List<PathSample> out = new ArrayList<>(centerline.size());
        for (Vec3d p : centerline) out.add(new PathSample(p, width));
        return out;
    }
```

Create `src/main/java/com/lwos/geometry/PathRibbon.java`:

```java
package com.lwos.geometry;

import java.util.ArrayList;
import java.util.List;

/** Left/right boundary curves offset by half-width, for visualizing path width. Pure and deterministic (spec §3.2, §3.6). */
public final class PathRibbon {
    private PathRibbon() { }

    public record Edges(List<Vec3d> left, List<Vec3d> right) { }

    public static Edges compute(List<PathSample> samples) {
        int n = samples.size();
        List<Vec3d> left = new ArrayList<>(n);
        List<Vec3d> right = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Vec3d p = samples.get(i).position();
            Vec3d prev = samples.get(Math.max(0, i - 1)).position();
            Vec3d next = samples.get(Math.min(n - 1, i + 1)).position();
            double dx = next.x() - prev.x();
            double dz = next.z() - prev.z();
            double len = Math.sqrt(dx * dx + dz * dz);
            double nx, nz;
            if (len <= 1e-9) { nx = 1; nz = 0; } else { nx = -dz / len; nz = dx / len; }
            double half = samples.get(i).width() / 2.0;
            left.add(new Vec3d(p.x() + nx * half, p.y(), p.z() + nz * half));
            right.add(new Vec3d(p.x() - nx * half, p.y(), p.z() - nz * half));
        }
        return new Edges(left, right);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.lwos.geometry.PathSamplerTest" --tests "com.lwos.geometry.PathRibbonTest"`
Expected: PASS (PathSamplerTest now 5 tests, PathRibbonTest 2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/geometry/PathSample.java src/main/java/com/lwos/geometry/PathRibbon.java src/main/java/com/lwos/geometry/PathSampler.java src/test/java/com/lwos/geometry/PathSamplerTest.java src/test/java/com/lwos/geometry/PathRibbonTest.java
git commit -m "feat(geometry): add PathSample width + PathRibbon boundary curves"
```

---

### Task 2: `WorldView` + `TerrainSampler` (pure, surface-following)

A minimal read-only interface pure code can query for ground height, plus a pure sampler that snaps sample points onto that surface — the "path hugs terrain" behavior (spec §4.2 `TerrainSampler`, §6).

**Files:**
- Create: `src/main/java/com/lwos/geometry/WorldView.java`
- Create: `src/main/java/com/lwos/geometry/TerrainSampler.java`
- Test: `src/test/java/com/lwos/geometry/TerrainSamplerTest.java`

**Interfaces:**
- Consumes: `PathSample`, `Vec3d` (Task 1 / existing).
- Produces:
  - `interface WorldView { int surfaceHeight(int x, int z); }` — the only door pure code has into world data. **Never implemented by anything that imports Minecraft in this package** (the Forge-side implementation lives in `com.lwos.client`, added in Task 8).
  - `static List<PathSample> TerrainSampler.snapToSurface(List<PathSample> samples, WorldView view, double verticalOffset)` — replaces each sample's Y with `view.surfaceHeight(floor(x), floor(z)) + verticalOffset`, keeping X/Z and width unchanged.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/geometry/TerrainSamplerTest.java`:

```java
package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TerrainSamplerTest {
    private static final double EPS = 1e-9;

    /** A simple sloped "world": height rises by 1 every 2 blocks along x, flat in z. */
    private static final class SlopedWorldView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) {
            return 64 + Math.floorDiv(x, 2);
        }
    }

    @Test
    void snapsYToSurfaceHeightPlusOffset() {
        List<PathSample> in = List.of(
                new PathSample(new Vec3d(0.4, 999, 0.2), 3.0),
                new PathSample(new Vec3d(3.9, -50, 0.9), 3.0));
        List<PathSample> out = TerrainSampler.snapToSurface(in, new SlopedWorldView(), 1.0);

        assertEquals(65.0, out.get(0).position().y(), EPS); // 64 + floor(0/2) + 1
        assertEquals(0.4, out.get(0).position().x(), EPS);
        assertEquals(0.2, out.get(0).position().z(), EPS);
        assertEquals(3.0, out.get(0).width(), EPS);

        assertEquals(66.0, out.get(1).position().y(), EPS); // 64 + floor(3/2) + 1 = 64+1+1
    }

    @Test
    void emptyInEmptyOut() {
        assertTrue(TerrainSampler.snapToSurface(List.of(), new SlopedWorldView(), 0.0).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.lwos.geometry.TerrainSamplerTest"`
Expected: FAIL / compile error — `WorldView` and `TerrainSampler` do not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/lwos/geometry/WorldView.java`:

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
}
```

Create `src/main/java/com/lwos/geometry/TerrainSampler.java`:

```java
package com.lwos.geometry;

import java.util.ArrayList;
import java.util.List;

/** Snaps sample points onto the world surface so the path hugs terrain (spec §4.2, §6). Pure and deterministic. */
public final class TerrainSampler {
    private TerrainSampler() { }

    public static List<PathSample> snapToSurface(List<PathSample> samples, WorldView view, double verticalOffset) {
        List<PathSample> out = new ArrayList<>(samples.size());
        for (PathSample s : samples) {
            Vec3d p = s.position();
            int x = (int) Math.floor(p.x());
            int z = (int) Math.floor(p.z());
            double y = view.surfaceHeight(x, z) + verticalOffset;
            out.add(new PathSample(new Vec3d(p.x(), y, p.z()), s.width()));
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.geometry.TerrainSamplerTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/geometry/WorldView.java src/main/java/com/lwos/geometry/TerrainSampler.java src/test/java/com/lwos/geometry/TerrainSamplerTest.java
git commit -m "feat(geometry): add WorldView interface and terrain-following TerrainSampler"
```

---

### Task 3: `ColumnPos` + `PathMask` (pure, disc-union occupancy)

Union per-sample discs (radius = width/2) into a 2D column occupancy field with a soft edge distance, so later stages (and M5's organic engine) know which ground columns the path covers (spec §4.2 `PathMask`, §6).

**Files:**
- Create: `src/main/java/com/lwos/geometry/ColumnPos.java`
- Create: `src/main/java/com/lwos/geometry/PathMask.java`
- Test: `src/test/java/com/lwos/geometry/PathMaskTest.java`

**Interfaces:**
- Consumes: `PathSample` (Task 1).
- Produces:
  - `record ColumnPos(int x, int z)`.
  - `class PathMask` with `static PathMask build(List<PathSample> samples)`, `boolean isInside(int x, int z)`, `double edgeDistance(int x, int z)` (signed: negative = inside the disc union, `Double.POSITIVE_INFINITY` if untracked), `Set<ColumnPos> insideColumns()`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/geometry/PathMaskTest.java`:

```java
package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PathMaskTest {

    @Test
    void singlePointDiscCoversExpectedColumns() {
        // One sample at (0,y,0) with width 2 -> radius 1. Column (0,0) center is (0.5,0.5), distance ~0.707 < 1 -> inside.
        List<PathSample> samples = List.of(new PathSample(new Vec3d(0, 64, 0), 2.0));
        PathMask mask = PathMask.build(samples);
        assertTrue(mask.isInside(0, 0));
        assertTrue(mask.edgeDistance(0, 0) <= 0.0);
    }

    @Test
    void farAwayColumnIsOutside() {
        List<PathSample> samples = List.of(new PathSample(new Vec3d(0, 64, 0), 2.0));
        PathMask mask = PathMask.build(samples);
        assertFalse(mask.isInside(100, 100));
        assertEquals(Double.POSITIVE_INFINITY, mask.edgeDistance(100, 100));
    }

    @Test
    void emptySamplesGiveEmptyMask() {
        PathMask mask = PathMask.build(List.of());
        assertTrue(mask.insideColumns().isEmpty());
        assertFalse(mask.isInside(0, 0));
    }

    @Test
    void wideStraightLineCoversAFullBand() {
        List<PathSample> samples = List.of(
                new PathSample(new Vec3d(0, 64, 0), 6.0),
                new PathSample(new Vec3d(10, 64, 0), 6.0));
        PathMask mask = PathMask.build(samples);
        Set<ColumnPos> inside = mask.insideColumns();
        // Radius 3 around a line from x=0..10 at z=0: column (5, 0) and (5, 2) should both be inside; (5, 10) should not.
        assertTrue(inside.contains(new ColumnPos(5, 0)));
        assertTrue(inside.contains(new ColumnPos(5, 2)));
        assertFalse(inside.contains(new ColumnPos(5, 10)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.lwos.geometry.PathMaskTest"`
Expected: FAIL / compile error — `ColumnPos` and `PathMask` do not exist.

- [ ] **Step 3: Write minimal implementation**

> **Implementation note (added post-execution):** the point-disc-only union shown below cannot satisfy test #4 (`wideStraightLineCoversAFullBand`), which passes only two sparse samples — column (5,0) sits ~1.5 blocks outside both endpoint discs. The shipped code (commit `46b792f`) therefore also takes the distance to the **line segment** joining consecutive samples (standard clamped point-to-segment distance, `r = (w0+w1)/4`), which covers the path between sparse samples. Treat that as the authoritative behavior; the pseudocode below is the pre-execution draft.

Create `src/main/java/com/lwos/geometry/ColumnPos.java`:

```java
package com.lwos.geometry;

/** An (x, z) ground column, independent of height. */
public record ColumnPos(int x, int z) { }
```

Create `src/main/java/com/lwos/geometry/PathMask.java`:

```java
package com.lwos.geometry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Union of per-sample discs (radius = width/2) into a column occupancy field with a
 * signed edge distance (negative = inside), the "soft edge" the organic engine will
 * feather in M5 (spec §4.2, §6). Pure and deterministic.
 */
public final class PathMask {
    // Columns whose disc-union distance is within this halo of the edge are tracked at all
    // (a small buffer just outside 0 so future edge-feathering stages have neighbors to read).
    private static final double EDGE_HALO = 0.5;

    private final Map<ColumnPos, Double> edgeDistance;

    private PathMask(Map<ColumnPos, Double> edgeDistance) {
        this.edgeDistance = edgeDistance;
    }

    public static PathMask build(List<PathSample> samples) {
        Map<ColumnPos, Double> dist = new HashMap<>();
        if (samples.isEmpty()) return new PathMask(dist);

        double maxRadius = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (PathSample s : samples) {
            maxRadius = Math.max(maxRadius, s.width() / 2.0);
            int x = (int) Math.floor(s.position().x());
            int z = (int) Math.floor(s.position().z());
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        int pad = (int) Math.ceil(maxRadius) + 1;
        minX -= pad; maxX += pad; minZ -= pad; maxZ += pad;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double cx = x + 0.5;
                double cz = z + 0.5;
                double best = Double.POSITIVE_INFINITY;
                for (PathSample s : samples) {
                    double dx = cx - s.position().x();
                    double dz = cz - s.position().z();
                    double d = Math.sqrt(dx * dx + dz * dz) - s.width() / 2.0;
                    if (d < best) best = d;
                }
                if (best <= EDGE_HALO) dist.put(new ColumnPos(x, z), best);
            }
        }
        return new PathMask(dist);
    }

    public boolean isInside(int x, int z) {
        Double d = edgeDistance.get(new ColumnPos(x, z));
        return d != null && d <= 0.0;
    }

    public double edgeDistance(int x, int z) {
        Double d = edgeDistance.get(new ColumnPos(x, z));
        return d == null ? Double.POSITIVE_INFINITY : d;
    }

    public Set<ColumnPos> insideColumns() {
        Set<ColumnPos> out = new HashSet<>();
        for (Map.Entry<ColumnPos, Double> e : edgeDistance.entrySet()) {
            if (e.getValue() <= 0.0) out.add(e.getKey());
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.geometry.PathMaskTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/geometry/ColumnPos.java src/main/java/com/lwos/geometry/PathMask.java src/test/java/com/lwos/geometry/PathMaskTest.java
git commit -m "feat(geometry): add ColumnPos and disc-union PathMask"
```

---

### Task 4: `plan/` data types — `GridPos`, `ChangeKind`, `PlannedChange`, `EditPlan` (pure)

The immutable contract between compute and (later) apply/preview (spec §5). M2 only ever produces `ChangeKind.TERRAIN` entries — `PLACE`/`REMOVE` and real block states arrive with the organic engine in M5.

**Files:**
- Create: `src/main/java/com/lwos/plan/GridPos.java`
- Create: `src/main/java/com/lwos/plan/ChangeKind.java`
- Create: `src/main/java/com/lwos/plan/PlannedChange.java`
- Create: `src/main/java/com/lwos/plan/EditPlan.java`
- Test: `src/test/java/com/lwos/plan/EditPlanTest.java`

**Interfaces:**
- Consumes: nothing outside `java.*`.
- Produces:
  - `record GridPos(int x, int y, int z)` — the pure stand-in for `net.minecraft.core.BlockPos` (see Global Constraints); M3's `apply/` layer maps `GridPos` → `BlockPos` when it exists.
  - `enum ChangeKind { PLACE, REMOVE, TERRAIN }`.
  - `record PlannedChange(GridPos pos, ChangeKind kind)`.
  - `class EditPlan` with constructor `EditPlan(Map<GridPos, PlannedChange> changes)` (defensively copied + unmodifiable), `Map<GridPos, PlannedChange> changes()`, `int size()`, `boolean isEmpty()`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/plan/EditPlanTest.java`:

```java
package com.lwos.plan;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EditPlanTest {

    @Test
    void wrapsChangesAndReportsSize() {
        GridPos pos = new GridPos(1, 64, 2);
        PlannedChange change = new PlannedChange(pos, ChangeKind.TERRAIN);
        Map<GridPos, PlannedChange> map = new LinkedHashMap<>();
        map.put(pos, change);

        EditPlan plan = new EditPlan(map);
        assertEquals(1, plan.size());
        assertFalse(plan.isEmpty());
        assertEquals(change, plan.changes().get(pos));
    }

    @Test
    void emptyPlanIsEmpty() {
        EditPlan plan = new EditPlan(Map.of());
        assertTrue(plan.isEmpty());
        assertEquals(0, plan.size());
    }

    @Test
    void changesMapIsUnmodifiable() {
        EditPlan plan = new EditPlan(Map.of());
        assertThrows(UnsupportedOperationException.class,
                () -> plan.changes().put(new GridPos(0, 0, 0), new PlannedChange(new GridPos(0, 0, 0), ChangeKind.PLACE)));
    }

    @Test
    void mutatingSourceMapAfterConstructionDoesNotAffectPlan() {
        Map<GridPos, PlannedChange> map = new LinkedHashMap<>();
        EditPlan plan = new EditPlan(map);
        map.put(new GridPos(0, 0, 0), new PlannedChange(new GridPos(0, 0, 0), ChangeKind.REMOVE));
        assertTrue(plan.isEmpty()); // defensive copy taken at construction time
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.lwos.plan.EditPlanTest"`
Expected: FAIL / compile error — `GridPos`, `ChangeKind`, `PlannedChange`, `EditPlan` do not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/lwos/plan/GridPos.java`:

```java
package com.lwos.plan;

/** Pure block-coordinate stand-in for net.minecraft.core.BlockPos (see plan's Minecraft-free boundary rule). */
public record GridPos(int x, int y, int z) { }
```

Create `src/main/java/com/lwos/plan/ChangeKind.java`:

```java
package com.lwos.plan;

/** Preview coloring convention (spec §5): PLACE=green, REMOVE=red, TERRAIN=blue. */
public enum ChangeKind { PLACE, REMOVE, TERRAIN }
```

Create `src/main/java/com/lwos/plan/PlannedChange.java`:

```java
package com.lwos.plan;

/** One entry of an EditPlan. Target block state is added in M5 once the organic engine chooses materials. */
public record PlannedChange(GridPos pos, ChangeKind kind) { }
```

Create `src/main/java/com/lwos/plan/EditPlan.java`:

```java
package com.lwos.plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable contract between compute, preview, and apply (spec §5). Built only by
 * EditPlanBuilder; never mutated after construction.
 */
public final class EditPlan {
    private final Map<GridPos, PlannedChange> changes;

    public EditPlan(Map<GridPos, PlannedChange> changes) {
        this.changes = Collections.unmodifiableMap(new LinkedHashMap<>(changes));
    }

    public Map<GridPos, PlannedChange> changes() { return changes; }

    public int size() { return changes.size(); }

    public boolean isEmpty() { return changes.isEmpty(); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.plan.EditPlanTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/plan/GridPos.java src/main/java/com/lwos/plan/ChangeKind.java src/main/java/com/lwos/plan/PlannedChange.java src/main/java/com/lwos/plan/EditPlan.java src/test/java/com/lwos/plan/EditPlanTest.java
git commit -m "feat(plan): add GridPos, ChangeKind, PlannedChange, and EditPlan"
```

---

### Task 5: `EditPlanBuilder` — the first real pipeline (pure)

Runs Spline → width sampling → terrain snapping → mask → `EditPlan`, as a single deterministic function (spec §4.3 rule 2: "`EditPlanBuilder` is the only code that runs the pipeline").

**Files:**
- Create: `src/main/java/com/lwos/plan/EditPlanBuilder.java`
- Test: `src/test/java/com/lwos/plan/EditPlanBuilderTest.java`

**Interfaces:**
- Consumes: `Vec3d`, `WorldView`, `PathSampler.sampleWithWidth`, `TerrainSampler.snapToSurface`, `PathMask` (Tasks 1–3); `GridPos`, `ChangeKind`, `PlannedChange`, `EditPlan` (Task 4).
- Produces: `static EditPlan EditPlanBuilder.build(List<Vec3d> controlPoints, double spacing, double width, WorldView view)`. Every entry in the result has `ChangeKind.TERRAIN` and a `GridPos.y()` equal to `view.surfaceHeight(x, z)` for that column (M2 doesn't offset height — that arrives with M4's terrain modification).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/plan/EditPlanBuilderTest.java`:

```java
package com.lwos.plan;

import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditPlanBuilderTest {

    private static final class FlatWorldView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 70; }
    }

    @Test
    void producesNonEmptyTerrainPlanAlongTheLine() {
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(10, 70, 0));
        EditPlan plan = EditPlanBuilder.build(controls, 1.0, 4.0, new FlatWorldView());

        assertFalse(plan.isEmpty());
        for (PlannedChange change : plan.changes().values()) {
            assertEquals(ChangeKind.TERRAIN, change.kind());
            assertEquals(70, change.pos().y());
        }
        // A column at the midpoint of the line should be covered.
        assertTrue(plan.changes().containsKey(new GridPos(5, 70, 0)));
    }

    @Test
    void singleControlPointStillProducesAPlan() {
        EditPlan plan = EditPlanBuilder.build(List.of(new Vec3d(0, 70, 0)), 1.0, 2.0, new FlatWorldView());
        assertFalse(plan.isEmpty());
    }

    @Test
    void sameInputsProduceIdenticalPlan_determinism() {
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(4, 70, 2), new Vec3d(9, 70, -3));
        WorldView view = new FlatWorldView();

        EditPlan first = EditPlanBuilder.build(controls, 0.5, 5.0, view);
        EditPlan second = EditPlanBuilder.build(controls, 0.5, 5.0, view);

        assertEquals(first.changes(), second.changes());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.lwos.plan.EditPlanBuilderTest"`
Expected: FAIL / compile error — `EditPlanBuilder` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/lwos/plan/EditPlanBuilder.java`:

```java
package com.lwos.plan;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import com.lwos.geometry.PathSample;
import com.lwos.geometry.PathSampler;
import com.lwos.geometry.TerrainSampler;
import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the M2 pipeline: Spline+width sampling -> terrain snap -> disc-union mask -> EditPlan.
 * The only code that runs this pipeline (spec §4.3) — nothing else recomputes geometry.
 * Pure and deterministic: same inputs + same WorldView answers -> identical EditPlan.
 */
public final class EditPlanBuilder {
    private EditPlanBuilder() { }

    public static EditPlan build(List<Vec3d> controlPoints, double spacing, double width, WorldView view) {
        List<PathSample> raw = PathSampler.sampleWithWidth(controlPoints, spacing, width);
        List<PathSample> grounded = TerrainSampler.snapToSurface(raw, view, 0.0);
        PathMask mask = PathMask.build(grounded);

        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (ColumnPos c : mask.insideColumns()) {
            int y = view.surfaceHeight(c.x(), c.z());
            GridPos pos = new GridPos(c.x(), y, c.z());
            changes.put(pos, new PlannedChange(pos, ChangeKind.TERRAIN));
        }
        return new EditPlan(changes);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.plan.EditPlanBuilderTest"`
Expected: PASS (3 tests). Then run the full suite: `./gradlew test` → all geometry + plan + tool tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/plan/EditPlanBuilder.java src/test/java/com/lwos/plan/EditPlanBuilderTest.java
git commit -m "feat(plan): add EditPlanBuilder running the geometry-to-plan pipeline"
```

---

### Task 6: Adjustable width on `PathTool` (pure)

Give the tool session a constant, clamped width the player can change.

**Files:**
- Modify: `src/main/java/com/lwos/tool/PathTool.java`
- Modify: `src/test/java/com/lwos/tool/ToolSessionTest.java` (add cases)

**Interfaces:**
- Consumes: nothing new.
- Produces: `PathTool.width()` (`double`, default `3.0`), `PathTool.setWidth(double)` (clamped to `[1.0, 15.0]`). Width is **not** reset by `clear()` — clearing points shouldn't discard the player's chosen width.

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/com/lwos/tool/ToolSessionTest.java` (inside the class, after the existing tests):

```java
    @Test
    void defaultWidthIsThree() {
        PathTool t = new PathTool();
        assertEquals(3.0, t.width(), 1e-9);
    }

    @Test
    void setWidthClampsToValidRange() {
        PathTool t = new PathTool();
        t.setWidth(7.5);
        assertEquals(7.5, t.width(), 1e-9);
        t.setWidth(100.0);
        assertEquals(15.0, t.width(), 1e-9);
        t.setWidth(-5.0);
        assertEquals(1.0, t.width(), 1e-9);
    }

    @Test
    void clearDoesNotResetWidth() {
        PathTool t = new PathTool();
        t.setWidth(9.0);
        t.addPoint(new Vec3d(0, 0, 0));
        t.clear();
        assertEquals(9.0, t.width(), 1e-9);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.lwos.tool.ToolSessionTest"`
Expected: FAIL / compile error — `width()`/`setWidth(double)` do not exist on `PathTool`.

- [ ] **Step 3: Write minimal implementation**

Modify `src/main/java/com/lwos/tool/PathTool.java` — add fields/methods (leave existing `nodes`, `state`, `addPoint`, `deleteLast`, `clear` untouched):

```java
    private static final double MIN_WIDTH = 1.0;
    private static final double MAX_WIDTH = 15.0;

    private double width = 3.0;

    public double width() { return width; }

    public void setWidth(double w) {
        width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, w));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.tool.ToolSessionTest"`
Expected: PASS (10 tests total).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/tool/PathTool.java src/test/java/com/lwos/tool/ToolSessionTest.java
git commit -m "feat(tool): add adjustable, clamped width to PathTool"
```

---

### Task 7: Width adjustment key mappings

Register two new keybinds (`[` decreases, `]` increases width) so the player can change width without leaving the keyboard.

**Files:**
- Modify: `src/main/java/com/lwos/client/LwosKeyMappings.java`
- Modify: `src/main/java/com/lwos/client/LwosClientModEvents.java`
- Modify: `src/main/resources/assets/lwos/lang/en_us.json`

**Interfaces:**
- Consumes: `LwosMod.MODID` (existing).
- Produces: `LwosKeyMappings.WIDTH_UP`, `LwosKeyMappings.WIDTH_DOWN` (`public static final KeyMapping`), registered the same way as the existing three mappings.

- [ ] **Step 1: Add the new key mappings**

Modify `src/main/java/com/lwos/client/LwosKeyMappings.java` — add inside the class, after `CANCEL_PATH`:

```java
    public static final KeyMapping WIDTH_UP = new KeyMapping(
            "key.lwos.width_up", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);

    public static final KeyMapping WIDTH_DOWN = new KeyMapping(
            "key.lwos.width_down", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);
```

- [ ] **Step 2: Register them**

Modify `src/main/java/com/lwos/client/LwosClientModEvents.java` — add inside `onRegisterKeyMappings`, after the existing three `event.register(...)` calls:

```java
        event.register(LwosKeyMappings.WIDTH_UP);
        event.register(LwosKeyMappings.WIDTH_DOWN);
```

- [ ] **Step 3: Add lang entries**

Modify `src/main/resources/assets/lwos/lang/en_us.json` to:

```json
{
  "key.categories.lwos": "LWOS Builder Tools",
  "key.lwos.toggle_mode": "Toggle Builder Mode",
  "key.lwos.delete_point": "Delete Last Point",
  "key.lwos.cancel_path": "Cancel Path",
  "key.lwos.width_up": "Increase Path Width",
  "key.lwos.width_down": "Decrease Path Width"
}
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual in-game verification**

Run: `./gradlew runClient`. Options → Controls → **"LWOS Builder Tools"** category. Confirm two new entries: **Increase Path Width (])** and **Decrease Path Width ([)** with readable translated names. Close the game.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/lwos/client/LwosKeyMappings.java src/main/java/com/lwos/client/LwosClientModEvents.java src/main/resources/assets/lwos/lang/en_us.json
git commit -m "feat(client): register width adjustment key mappings"
```

---

### Task 8: Wire width keys + `ForgeWorldView` adapter

Hook the new keybinds into `ToolManager`'s current path, and add the Forge-side `WorldView` implementation that reads real terrain height from the client level. This is Forge glue — not unit tested, matching the M1 pattern for `com.lwos.client.*`.

**Files:**
- Modify: `src/main/java/com/lwos/client/LwosInputHandler.java`
- Create: `src/main/java/com/lwos/client/ForgeWorldView.java`

**Interfaces:**
- Consumes: `LwosKeyMappings.WIDTH_UP` / `WIDTH_DOWN` (Task 7); `ToolManager.currentPath()`, `PathTool.width()`/`setWidth(double)` (Task 6); `com.lwos.geometry.WorldView` (Task 2).
- Produces: `ForgeWorldView.INSTANCE implements com.lwos.geometry.WorldView`, consumed by `PathRenderer` in Task 9.

- [ ] **Step 1: Wire the width keys**

Modify `src/main/java/com/lwos/client/LwosInputHandler.java` — add inside `onKey`, after the existing `DELETE_POINT`/`CANCEL_PATH` lines:

```java
        while (LwosKeyMappings.WIDTH_UP.consumeClick()) {
            tm.currentPath().setWidth(tm.currentPath().width() + 1.0);
        }
        while (LwosKeyMappings.WIDTH_DOWN.consumeClick()) {
            tm.currentPath().setWidth(tm.currentPath().width() - 1.0);
        }
```

- [ ] **Step 2: Write the Forge `WorldView` adapter**

Create `src/main/java/com/lwos/client/ForgeWorldView.java`:

```java
package com.lwos.client;

import com.lwos.geometry.WorldView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Adapts the live client level to the pure geometry.WorldView interface (spec §4.3 boundary rule). */
public final class ForgeWorldView implements WorldView {
    public static final ForgeWorldView INSTANCE = new ForgeWorldView();

    private ForgeWorldView() { }

    @Override
    public int surfaceHeight(int x, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return 64;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Manual in-game verification (temporary log probe)**

Because `PathRenderer` doesn't consume width/terrain yet (Task 9), verify with a quick log line. Temporarily add to the end of the `onKey` method in `LwosInputHandler`:

```java
if (LwosKeyMappings.WIDTH_UP.isDown() || LwosKeyMappings.WIDTH_DOWN.isDown()) {
    com.mojang.logging.LogUtils.getLogger().info("LWOS width={}", tm.currentPath().width());
}
```

Run `./gradlew runClient`, enter a creative world, press **B** to enable, then tap **]** a few times → log shows `width=4.0`, `5.0`, `6.0`… tap **[** → width decreases; keep tapping past 15 and below 1 → confirm it clamps and doesn't keep climbing/falling in the log. Then **remove the temporary log line** and rebuild (`./gradlew build`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/client/LwosInputHandler.java src/main/java/com/lwos/client/ForgeWorldView.java
git commit -m "feat(client): wire width keys and add ForgeWorldView terrain adapter"
```

---

### Task 9: `PathRenderer` — terrain-hugging curve, width ribbon, and plan footprint (M2 definition of done)

Extend the M1 renderer: the centerline now snaps to real terrain height between control points, a width ribbon shows the path's edges, and the `EditPlan`'s covered columns are outlined as debug boxes (blue, matching the spec's `TERRAIN` preview-color convention) — all still debug lines only, no blocks and no mesh.

**Files:**
- Modify: `src/main/java/com/lwos/client/PathRenderer.java`

**Interfaces:**
- Consumes: `ToolManager`, `PathTool` (existing); `PathSampler.sampleWithWidth`, `TerrainSampler.snapToSurface`, `PathRibbon.compute` (Tasks 1–2); `EditPlanBuilder.build`, `GridPos`, `ChangeKind` (Tasks 4–5); `ForgeWorldView.INSTANCE` (Task 8).
- Produces: updated in-world rendering only. No new types other tasks depend on.

- [ ] **Step 1: Rewrite the curve section to hug terrain and add the width ribbon**

Modify `src/main/java/com/lwos/client/PathRenderer.java` — replace the body of `onRenderLevel` (everything from `// Smooth curve...` to the end of that block, keeping the gizmo loop above it and the `ps.popPose()`/`buffers.endBatch(...)` lines below it as-is) with:

```java
        // Terrain-hugging curve + width ribbon (M2: replaces the M1 raw-interpolated curve).
        List<Vec3d> positions = new ArrayList<>(nodes.size());
        for (PathNode node : nodes) positions.add(node.position());

        double width = tm.currentPath().width();
        List<com.lwos.geometry.PathSample> raw =
                com.lwos.geometry.PathSampler.sampleWithWidth(positions, SAMPLE_SPACING, width);
        List<com.lwos.geometry.PathSample> grounded =
                com.lwos.geometry.TerrainSampler.snapToSurface(raw, ForgeWorldView.INSTANCE, 0.05);

        List<Vec3d> centerline = new ArrayList<>(grounded.size());
        for (com.lwos.geometry.PathSample s : grounded) centerline.add(s.position());
        for (int i = 0; i < centerline.size() - 1; i++) {
            addLine(lines, mat, nor, centerline.get(i), centerline.get(i + 1), 0, 255, 0);
        }

        com.lwos.geometry.PathRibbon.Edges edges = com.lwos.geometry.PathRibbon.compute(grounded);
        for (int i = 0; i < edges.left().size() - 1; i++) {
            addLine(lines, mat, nor, edges.left().get(i), edges.left().get(i + 1), 255, 255, 0);
            addLine(lines, mat, nor, edges.right().get(i), edges.right().get(i + 1), 255, 255, 0);
        }

        // EditPlan footprint (blue, matching the spec's TERRAIN preview color) — debug outlines only.
        com.lwos.plan.EditPlan plan = com.lwos.plan.EditPlanBuilder.build(
                positions, SAMPLE_SPACING, width, ForgeWorldView.INSTANCE);
        for (com.lwos.plan.PlannedChange change : plan.changes().values()) {
            com.lwos.plan.GridPos p = change.pos();
            AABB box = new AABB(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1);
            LevelRenderer.renderLineBox(ps, lines, box, 0.2f, 0.4f, 1.0f, 0.6f);
        }
```

- [ ] **Step 2: Generalize `addLine` to take a color**

Modify `src/main/java/com/lwos/client/PathRenderer.java` — replace the existing `addLine` method (which hardcodes green) with:

```java
    private static void addLine(VertexConsumer c, Matrix4f mat, Matrix3f nor, Vec3d a, Vec3d b, int r, int g, int b2) {
        float nx = (float) (b.x() - a.x());
        float ny = (float) (b.y() - a.y());
        float nz = (float) (b.z() - a.z());
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return;
        nx /= len; ny /= len; nz /= len;
        c.vertex(mat, (float) a.x(), (float) a.y(), (float) a.z())
                .color(r, g, b2, 255).normal(nor, nx, ny, nz).endVertex();
        c.vertex(mat, (float) b.x(), (float) b.y(), (float) b.z())
                .color(r, g, b2, 255).normal(nor, nx, ny, nz).endVertex();
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Manual in-game verification — the full M2 definition of done**

Run `./gradlew runClient`, enter a creative world with some elevation change (a hillside or staircase of blocks works well):

1. Press **B** to enable builder mode; Alt+Scroll to **Path**, release Alt.
2. Right-click 3+ points that span a slope or steps, with gaps between them (not touching every block). Confirm the **green centerline** now follows the ground height between points — it should step/slope with the terrain, not draw a straight interpolation that clips through or floats above blocks.
3. Confirm **two yellow lines** run parallel to the green centerline, roughly `width/2` to each side — the width ribbon.
4. Confirm a band of **translucent blue debug boxes** sits on the ground under and around the path, matching the ribbon's width.
5. Press **]** several times → ribbon and blue footprint visibly widen live. Press **[** several times → they narrow; confirm it stops shrinking around 1 block wide and stops growing around 15.
6. Press **Z** / **X** still work as in M1 (delete last point / clear path).
7. Press **B** to disable → all rendering (gizmos, centerline, ribbon, footprint) disappears; right-click behaves normally.
8. **Confirm no blocks were ever placed, broken, or changed** anywhere in this milestone.

- [ ] **Step 5: Run the full automated suite one more time**

Run: `./gradlew test`
Expected: all geometry + plan + tool tests PASS (Tasks 1–6).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/lwos/client/PathRenderer.java
git commit -m "feat(client): render terrain-hugging curve, width ribbon, and EditPlan footprint"
```

---

## Definition of Done (Milestone 2)

- `./gradlew build` and `./gradlew test` both succeed; all geometry/plan/tool unit tests pass, including the `EditPlanBuilder` determinism test.
- In a creative world with elevation changes: the Path tool's centerline hugs terrain height between control points instead of straight-line-interpolating raw click heights.
- The path has a constant, player-adjustable width (`[`/`]`, clamped 1–15 blocks), visualized as a yellow ribbon and a translucent blue footprint of the `EditPlan`'s covered columns.
- A real `EditPlan` is produced by `EditPlanBuilder` (pure, deterministic — same inputs always produce the identical plan) — but nothing is applied to the world.
- **Zero blocks are placed, removed, or modified anywhere in this milestone.**
- No `net.minecraft.*` import exists in any `com.lwos.geometry.*`, `com.lwos.plan.*`, or `com.lwos.tool.*` class.
