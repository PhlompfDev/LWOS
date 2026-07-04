# Milestone 1 — Interaction Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a working, visible builder-tool loop — select the Path tool from an Alt+Scroll wheel, right-click to place control points, watch a smooth Catmull-Rom curve update live in-world, delete/cancel points — with **zero blocks changed**.

**Architecture:** A pure, headless-testable geometry core (`Vec3d`, `CatmullRomSpline`, `PathSampler`) that never imports Minecraft, plus a pure tool-session state layer (`ToolType`, `PathTool`, `ToolManager`). A thin Forge client layer wires input (Alt+Scroll cycle, right-click place, delete/cancel keys) and rendering (radial tool-wheel HUD overlay, in-world debug line rendering of the curve and control-point gizmos) onto that core. This mirrors the spec's compute/apply split: the compute classes are TDD'd; the Forge glue is compile-gated and manually verified in-game.

**Tech Stack:** Java 17, MinecraftForge 1.20.1 (Forge 47.4.10), ForgeGradle, JUnit 5 (Jupiter), JOML (bundled with MC), LWJGL/GLFW (bundled).

## Global Constraints

- **Loader/version:** MinecraftForge, Minecraft **1.20.1**, Forge **47.4.10**, Java **17**. (Spec §2)
- **Group / base package:** `com.lwos`. **Mod id:** `lwos`.
- **Compute/apply boundary (spec §3.6, hard rule):** classes in package `com.lwos.geometry.*` MUST import only `java.*` and other `com.lwos.geometry.*` types — **never** `net.minecraft.*`, `net.minecraftforge.*`, `org.joml.*`, or `com.mojang.*`. This is what keeps them headlessly unit-testable. `com.lwos.tool.*` may import only `java.*` and `com.lwos.geometry.*` (no Minecraft either).
- **Determinism (spec §3.2):** M1 introduces no randomness at all — spline/sampler output is a pure function of the control points. No stage may read wall-clock or `Random`.
- **Zero placement (spec §9, M1 definition of done):** M1 must not write, remove, or modify any block or terrain. Rendering is debug lines only. There is no `EditPlan`, no preview mesh, no `PlacementEngine` in this milestone.
- **Client-guarded:** all Forge event subscribers that touch client-only classes are registered under `Dist.CLIENT` so the mod never crashes a dedicated server if present.

---

### Task 1: Project bootstrap (Forge MDK + JUnit + empty mod loads)

Stand up a Forge 1.20.1 project that compiles, launches to the main menu with the mod loaded, and can run JUnit tests.

**Files:**
- Create (from MDK): `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- Create: `src/main/java/com/lwos/LwosMod.java`
- Create: `src/main/resources/META-INF/mods.toml`
- Create: `src/main/resources/pack.mcmeta`
- Create: `src/main/resources/assets/lwos/lang/en_us.json`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `com.lwos.LwosMod.MODID` (`public static final String MODID = "lwos"`); a compiling, runnable ForgeGradle project with a working `test` task.

- [ ] **Step 1: Get the Forge 1.20.1 MDK**

Download the **Forge 1.20.1 — 47.4.10 MDK** from https://files.minecraftforge.net/ (the "Mdk" download) and unzip its contents into the project root. This provides `build.gradle`, `settings.gradle`, `gradle.properties`, the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`), and a `src/` skeleton. Delete the MDK's example mod class and example resources under `src/main/java` and `src/main/resources` (keep the wrapper and gradle files). We will author our own below.

- [ ] **Step 2: Set project coordinates in `gradle.properties`**

Edit these keys (leave the rest of the MDK values as-is):

```properties
## Environment Properties
minecraft_version=1.20.1
forge_version=47.4.10
mapping_channel=official
mapping_version=1.20.1

## Mod Properties
mod_id=lwos
mod_name=LWOS Builder Tools
mod_license=All Rights Reserved
mod_version=0.1.0
mod_group_id=com.lwos
mod_authors=monster
mod_description=Interactive building tools for beautiful, hand-built-looking builds.
```

- [ ] **Step 3: Add JUnit 5 to `build.gradle`**

In `build.gradle`, add to the `dependencies { }` block:

```gradle
    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

And add this top-level block (anywhere after the `dependencies` block):

```gradle
test {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Write `mods.toml`**

Create `src/main/resources/META-INF/mods.toml`:

```toml
modLoader="javafml"
loaderVersion="[47,)"
license="All Rights Reserved"

[[mods]]
modId="lwos"
version="0.1.0"
displayName="LWOS Builder Tools"
authors="monster"
description='''
Interactive building tools for beautiful, hand-built-looking builds.
'''

[[dependencies.lwos]]
    modId="forge"
    mandatory=true
    versionRange="[47,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.lwos]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.20.1,1.21)"
    ordering="NONE"
    side="BOTH"
```

- [ ] **Step 5: Write `pack.mcmeta`**

Create `src/main/resources/pack.mcmeta`:

```json
{
  "pack": {
    "description": "LWOS Builder Tools resources",
    "pack_format": 15
  }
}
```

- [ ] **Step 6: Write the language file**

Create `src/main/resources/assets/lwos/lang/en_us.json`:

```json
{
  "key.categories.lwos": "LWOS Builder Tools",
  "key.lwos.toggle_mode": "Toggle Builder Mode",
  "key.lwos.delete_point": "Delete Last Point",
  "key.lwos.cancel_path": "Cancel Path"
}
```

- [ ] **Step 7: Write the main mod class**

Create `src/main/java/com/lwos/LwosMod.java`:

```java
package com.lwos;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(LwosMod.MODID)
public class LwosMod {
    public static final String MODID = "lwos";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LwosMod() {
        LOGGER.info("LWOS Builder Tools loading");
        // Client-side registrations are wired by @Mod.EventBusSubscriber classes
        // added in later tasks (guarded by Dist.CLIENT).
    }
}
```

- [ ] **Step 8: Build and run the test task**

Run: `./gradlew build` (use `gradlew.bat build` on Windows cmd/PowerShell)
Expected: `BUILD SUCCESSFUL`. There are no tests yet, so the `test` task is up-to-date/no-ops.

- [ ] **Step 9: Launch the client and confirm the mod loads**

Run: `./gradlew runClient`
Expected: the game reaches the main menu. In the logs you see `LWOS Builder Tools loading`. Open Mods list → "LWOS Builder Tools" appears. Close the game. **No blocks or worlds are required for this check.**

- [ ] **Step 10: Commit**

```bash
git init
git add .
git commit -m "chore: bootstrap Forge 1.20.1 project with JUnit and empty mod"
```

---

### Task 2: `Vec3d` value type + `PathNode` (pure)

The dependency-free 3D point used everywhere in `geometry/` and `tool/`, plus the control-point wrapper.

**Files:**
- Create: `src/main/java/com/lwos/geometry/Vec3d.java`
- Create: `src/main/java/com/lwos/geometry/PathNode.java`
- Test: `src/test/java/com/lwos/geometry/Vec3dTest.java`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces:
  - `record Vec3d(double x, double y, double z)` with:
    - `Vec3d add(Vec3d o)`, `Vec3d sub(Vec3d o)`, `Vec3d scale(double s)`
    - `double length()`, `double distance(Vec3d o)`
  - `record PathNode(Vec3d position)` with accessor `position()`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/geometry/Vec3dTest.java`:

```java
package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Vec3dTest {
    private static final double EPS = 1e-9;

    @Test
    void addSubScale() {
        Vec3d a = new Vec3d(1, 2, 3);
        Vec3d b = new Vec3d(4, 5, 6);
        assertEquals(new Vec3d(5, 7, 9), a.add(b));
        assertEquals(new Vec3d(-3, -3, -3), a.sub(b));
        assertEquals(new Vec3d(2, 4, 6), a.scale(2));
    }

    @Test
    void lengthAndDistance() {
        assertEquals(5.0, new Vec3d(3, 4, 0).length(), EPS);
        assertEquals(5.0, new Vec3d(0, 0, 0).distance(new Vec3d(0, 3, 4)), EPS);
    }

    @Test
    void pathNodeHoldsPosition() {
        Vec3d p = new Vec3d(7, 8, 9);
        assertEquals(p, new PathNode(p).position());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.lwos.geometry.Vec3dTest"`
Expected: FAIL / compile error — `Vec3d` and `PathNode` do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/lwos/geometry/Vec3d.java`:

```java
package com.lwos.geometry;

/** Dependency-free 3D point. MUST NOT import net.minecraft.* / org.joml.* (spec §3.6). */
public record Vec3d(double x, double y, double z) {
    public Vec3d add(Vec3d o) { return new Vec3d(x + o.x, y + o.y, z + o.z); }
    public Vec3d sub(Vec3d o) { return new Vec3d(x - o.x, y - o.y, z - o.z); }
    public Vec3d scale(double s) { return new Vec3d(x * s, y * s, z * s); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public double distance(Vec3d o) { return sub(o).length(); }
}
```

Create `src/main/java/com/lwos/geometry/PathNode.java`:

```java
package com.lwos.geometry;

/**
 * A control point placed by the player. M1 uses only position; width, height
 * offset and bank angle (spec §4.2) are added in later milestones.
 */
public record PathNode(Vec3d position) { }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.geometry.Vec3dTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/geometry/Vec3d.java src/main/java/com/lwos/geometry/PathNode.java src/test/java/com/lwos/geometry/Vec3dTest.java
git commit -m "feat(geometry): add Vec3d value type and PathNode"
```

---

### Task 3: `CatmullRomSpline` (pure, centripetal)

Produce a smooth polyline through the control points using a **centripetal** Catmull-Rom spline (alpha = 0.5), which avoids the cusps/self-intersections uniform Catmull-Rom produces at sharp turns. Endpoints are duplicated so the curve passes through the first and last control points.

**Files:**
- Create: `src/main/java/com/lwos/geometry/CatmullRomSpline.java`
- Test: `src/test/java/com/lwos/geometry/CatmullRomSplineTest.java`

**Interfaces:**
- Consumes: `Vec3d` (Task 2).
- Produces: `static List<Vec3d> CatmullRomSpline.sample(List<Vec3d> controlPoints, int samplesPerSegment)` — returns a dense ordered polyline. Empty in → empty out; 1 point in → that single point; `samplesPerSegment` is clamped to ≥ 1. First output ≈ first control point, last output ≈ last control point.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/geometry/CatmullRomSplineTest.java`:

```java
package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CatmullRomSplineTest {
    private static final double EPS = 1e-6;

    @Test
    void emptyInEmptyOut() {
        assertTrue(CatmullRomSpline.sample(List.of(), 8).isEmpty());
    }

    @Test
    void singlePointReturnsItself() {
        Vec3d p = new Vec3d(1, 2, 3);
        List<Vec3d> out = CatmullRomSpline.sample(List.of(p), 8);
        assertEquals(1, out.size());
        assertEquals(p, out.get(0));
    }

    @Test
    void passesThroughEndpoints() {
        Vec3d a = new Vec3d(0, 0, 0);
        Vec3d d = new Vec3d(9, 1, 2);
        List<Vec3d> out = CatmullRomSpline.sample(
                List.of(a, new Vec3d(3, 2, 0), new Vec3d(6, -1, 1), d), 16);
        assertEquals(a.x, out.get(0).x, EPS);
        assertEquals(a.y, out.get(0).y, EPS);
        assertEquals(a.z, out.get(0).z, EPS);
        Vec3d last = out.get(out.size() - 1);
        assertEquals(d.x, last.x, EPS);
        assertEquals(d.y, last.y, EPS);
        assertEquals(d.z, last.z, EPS);
    }

    @Test
    void collinearPointsStayCollinear() {
        List<Vec3d> out = CatmullRomSpline.sample(
                List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(2, 0, 0)), 10);
        double prevX = Double.NEGATIVE_INFINITY;
        for (Vec3d p : out) {
            assertEquals(0.0, p.y(), EPS);
            assertEquals(0.0, p.z(), EPS);
            assertTrue(p.x() >= prevX - EPS, "x should be monotonically non-decreasing");
            prevX = p.x();
        }
    }

    @Test
    void noNaNOnDuplicateConsecutivePoints() {
        List<Vec3d> out = CatmullRomSpline.sample(
                List.of(new Vec3d(0, 0, 0), new Vec3d(0, 0, 0), new Vec3d(1, 0, 0)), 8);
        for (Vec3d p : out) {
            assertFalse(Double.isNaN(p.x()) || Double.isNaN(p.y()) || Double.isNaN(p.z()));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.lwos.geometry.CatmullRomSplineTest"`
Expected: FAIL / compile error — `CatmullRomSpline` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/lwos/geometry/CatmullRomSpline.java`:

```java
package com.lwos.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Centripetal Catmull-Rom spline (alpha = 0.5). Pure and deterministic
 * (spec §3.2, §3.6). Endpoints are duplicated so the curve interpolates the
 * first and last control points.
 */
public final class CatmullRomSpline {
    private static final double ALPHA = 0.5;

    private CatmullRomSpline() { }

    public static List<Vec3d> sample(List<Vec3d> controlPoints, int samplesPerSegment) {
        int sps = Math.max(1, samplesPerSegment);
        List<Vec3d> out = new ArrayList<>();
        if (controlPoints == null || controlPoints.isEmpty()) return out;
        if (controlPoints.size() == 1) {
            out.add(controlPoints.get(0));
            return out;
        }
        int n = controlPoints.size();
        out.add(controlPoints.get(0));
        for (int i = 0; i < n - 1; i++) {
            Vec3d p0 = controlPoints.get(Math.max(0, i - 1));
            Vec3d p1 = controlPoints.get(i);
            Vec3d p2 = controlPoints.get(i + 1);
            Vec3d p3 = controlPoints.get(Math.min(n - 1, i + 2));
            appendSegment(out, p0, p1, p2, p3, sps);
        }
        return out;
    }

    // Appends samples for the p1..p2 span (excluding p1, including p2) so joints aren't duplicated.
    private static void appendSegment(List<Vec3d> out, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, int sps) {
        double t0 = 0.0;
        double t1 = t0 + knot(p0, p1);
        double t2 = t1 + knot(p1, p2);
        double t3 = t2 + knot(p2, p3);

        // Degenerate middle span (p1 == p2): emit p2 once, nothing to interpolate.
        if (t2 - t1 <= 1e-12) {
            out.add(p2);
            return;
        }
        for (int s = 1; s <= sps; s++) {
            double t = t1 + (t2 - t1) * ((double) s / sps);
            out.add(point(p0, p1, p2, p3, t0, t1, t2, t3, t));
        }
    }

    private static double knot(Vec3d a, Vec3d b) {
        double d = a.distance(b);
        return Math.pow(d, ALPHA);
    }

    private static Vec3d point(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3,
                               double t0, double t1, double t2, double t3, double t) {
        Vec3d a1 = lerpKnot(p0, p1, t0, t1, t);
        Vec3d a2 = lerpKnot(p1, p2, t1, t2, t);
        Vec3d a3 = lerpKnot(p2, p3, t2, t3, t);
        Vec3d b1 = lerpKnot(a1, a2, t0, t2, t);
        Vec3d b2 = lerpKnot(a2, a3, t1, t3, t);
        return lerpKnot(b1, b2, t1, t2, t);
    }

    // Linear interpolation on a knot interval; falls back to the endpoint if the interval is zero-length.
    private static Vec3d lerpKnot(Vec3d a, Vec3d b, double ta, double tb, double t) {
        double denom = tb - ta;
        if (Math.abs(denom) <= 1e-12) return b;
        double w = (t - ta) / denom;
        return a.scale(1 - w).add(b.scale(w));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.geometry.CatmullRomSplineTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/geometry/CatmullRomSpline.java src/test/java/com/lwos/geometry/CatmullRomSplineTest.java
git commit -m "feat(geometry): add centripetal Catmull-Rom spline"
```

---

### Task 4: `PathSampler` (pure, even arc-length spacing)

Resample the dense spline polyline to roughly even spacing along its arc length. This is the "walk the spline at fixed spacing" sampler the spec pulls forward for M1; later milestones consume it to build the path mask.

**Files:**
- Create: `src/main/java/com/lwos/geometry/PathSampler.java`
- Test: `src/test/java/com/lwos/geometry/PathSamplerTest.java`

**Interfaces:**
- Consumes: `Vec3d` (Task 2), `CatmullRomSpline` (Task 3).
- Produces:
  - `static List<Vec3d> PathSampler.sample(List<Vec3d> controlPoints, double spacing)` — builds the dense spline (24 samples/segment) then resamples to ~`spacing`-block intervals. Always includes the first and last curve points.
  - `static List<Vec3d> PathSampler.resample(List<Vec3d> polyline, double spacing)` — the reusable resampler on an arbitrary polyline.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/geometry/PathSamplerTest.java`:

```java
package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PathSamplerTest {
    private static final double EPS = 1e-6;

    @Test
    void resampleStraightLineIsEvenlySpaced() {
        List<Vec3d> line = List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0));
        List<Vec3d> out = PathSampler.resample(line, 2.0);
        // Endpoints preserved.
        assertEquals(0.0, out.get(0).x(), EPS);
        assertEquals(10.0, out.get(out.size() - 1).x(), EPS);
        // Interior spacing ~2.0 (last step may be shorter).
        for (int i = 0; i < out.size() - 2; i++) {
            assertEquals(2.0, out.get(i).distance(out.get(i + 1)), 1e-3);
        }
    }

    @Test
    void spacingLargerThanLengthGivesEndpointsOnly() {
        List<Vec3d> line = List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0));
        List<Vec3d> out = PathSampler.resample(line, 5.0);
        assertEquals(2, out.size());
        assertEquals(new Vec3d(0, 0, 0), out.get(0));
        assertEquals(new Vec3d(1, 0, 0), out.get(1));
    }

    @Test
    void emptyAndSinglePolyline() {
        assertTrue(PathSampler.resample(List.of(), 1.0).isEmpty());
        Vec3d p = new Vec3d(2, 2, 2);
        assertEquals(List.of(p), PathSampler.resample(List.of(p), 1.0));
    }

    @Test
    void sampleFromControlPointsHitsEndpoints() {
        List<Vec3d> out = PathSampler.sample(
                List.of(new Vec3d(0, 0, 0), new Vec3d(5, 3, 0), new Vec3d(10, 0, 0)), 0.5);
        assertTrue(out.size() > 3);
        assertEquals(0.0, out.get(0).x(), EPS);
        assertEquals(10.0, out.get(out.size() - 1).x(), EPS);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.lwos.geometry.PathSamplerTest"`
Expected: FAIL / compile error — `PathSampler` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/lwos/geometry/PathSampler.java`:

```java
package com.lwos.geometry;

import java.util.ArrayList;
import java.util.List;

/** Resamples a curve to even arc-length spacing. Pure and deterministic (spec §3.2, §3.6). */
public final class PathSampler {
    private static final int SAMPLES_PER_SEGMENT = 24;

    private PathSampler() { }

    public static List<Vec3d> sample(List<Vec3d> controlPoints, double spacing) {
        return resample(CatmullRomSpline.sample(controlPoints, SAMPLES_PER_SEGMENT), spacing);
    }

    public static List<Vec3d> resample(List<Vec3d> polyline, double spacing) {
        List<Vec3d> out = new ArrayList<>();
        if (polyline == null || polyline.isEmpty()) return out;
        if (polyline.size() == 1) {
            out.add(polyline.get(0));
            return out;
        }
        double step = Math.max(1e-6, spacing);
        out.add(polyline.get(0));
        double carried = 0.0; // distance accumulated since the last emitted point
        for (int i = 0; i < polyline.size() - 1; i++) {
            Vec3d a = polyline.get(i);
            Vec3d b = polyline.get(i + 1);
            double segLen = a.distance(b);
            if (segLen <= 1e-12) continue;
            double distIntoSeg = step - carried; // where the next emission falls within this segment
            while (distIntoSeg <= segLen + 1e-9) {
                double w = distIntoSeg / segLen;
                out.add(a.scale(1 - w).add(b.scale(w)));
                distIntoSeg += step;
            }
            carried = segLen - (distIntoSeg - step);
        }
        Vec3d last = polyline.get(polyline.size() - 1);
        if (out.get(out.size() - 1).distance(last) > 1e-6) out.add(last);
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.geometry.PathSamplerTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/geometry/PathSampler.java src/test/java/com/lwos/geometry/PathSamplerTest.java
git commit -m "feat(geometry): add even-spacing PathSampler over the spline"
```

---

### Task 5: Tool session state — `ToolType`, `PathTool`, `ToolManager` (pure)

The client-side session model: which tool is selected, whether builder mode is enabled, and the current Path tool's control points + state machine. Kept free of Minecraft imports so it is fully unit-tested.

**Files:**
- Create: `src/main/java/com/lwos/tool/ToolType.java`
- Create: `src/main/java/com/lwos/tool/PathTool.java`
- Create: `src/main/java/com/lwos/tool/ToolManager.java`
- Test: `src/test/java/com/lwos/tool/ToolSessionTest.java`

**Interfaces:**
- Consumes: `Vec3d`, `PathNode` (Task 2).
- Produces:
  - `enum ToolType { PATH, LINE, CIRCLE, FILL, TERRAIN_BLEND }` each with `String displayName()` and `int color()` (0xRRGGBB, used by the wheel).
  - `class PathTool`: `enum State { IDLE, PLACING, PREVIEW }`; `State state()`; `List<PathNode> nodes()` (unmodifiable); `void addPoint(Vec3d)`; `void deleteLast()`; `void clear()`.
  - `class ToolManager` (singleton): `static ToolManager get()`; `boolean isEnabled()`; `void toggleEnabled()`; `void cycle(int dir)`; `ToolType selected()`; `boolean isPathToolActive()`; `PathTool currentPath()`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/tool/ToolSessionTest.java`:

```java
package com.lwos.tool;

import com.lwos.geometry.PathNode;
import com.lwos.geometry.Vec3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolSessionTest {

    @BeforeEach
    void reset() {
        // Return the singleton to a known state between tests.
        ToolManager tm = ToolManager.get();
        tm.currentPath().clear();
        while (tm.selected() != ToolType.PATH) tm.cycle(1);
        if (tm.isEnabled()) tm.toggleEnabled();
    }

    @Test
    void pathToolStartsIdleAndEmpty() {
        PathTool t = new PathTool();
        assertEquals(PathTool.State.IDLE, t.state());
        assertTrue(t.nodes().isEmpty());
    }

    @Test
    void addPointMovesToPlacing() {
        PathTool t = new PathTool();
        t.addPoint(new Vec3d(1, 0, 1));
        assertEquals(PathTool.State.PLACING, t.state());
        assertEquals(1, t.nodes().size());
        assertEquals(new Vec3d(1, 0, 1), t.nodes().get(0).position());
    }

    @Test
    void deleteLastReturnsToIdleWhenEmptied() {
        PathTool t = new PathTool();
        t.addPoint(new Vec3d(0, 0, 0));
        t.addPoint(new Vec3d(1, 0, 0));
        t.deleteLast();
        assertEquals(1, t.nodes().size());
        assertEquals(PathTool.State.PLACING, t.state());
        t.deleteLast();
        assertTrue(t.nodes().isEmpty());
        assertEquals(PathTool.State.IDLE, t.state());
    }

    @Test
    void deleteLastOnEmptyIsSafe() {
        PathTool t = new PathTool();
        t.deleteLast();
        assertTrue(t.nodes().isEmpty());
        assertEquals(PathTool.State.IDLE, t.state());
    }

    @Test
    void nodesListIsUnmodifiable() {
        PathTool t = new PathTool();
        t.addPoint(new Vec3d(0, 0, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> t.nodes().add(new PathNode(new Vec3d(1, 1, 1))));
    }

    @Test
    void cycleWrapsThroughAllToolTypes() {
        ToolManager tm = ToolManager.get();
        assertEquals(ToolType.PATH, tm.selected());
        tm.cycle(1);
        assertEquals(ToolType.LINE, tm.selected());
        // A full loop (values().length tools) returns to the same tool.
        for (int i = 0; i < ToolType.values().length; i++) tm.cycle(1);
        assertEquals(ToolType.LINE, tm.selected());
        tm.cycle(-1);
        assertEquals(ToolType.PATH, tm.selected());
        tm.cycle(-1);
        assertEquals(ToolType.TERRAIN_BLEND, tm.selected()); // wrap backwards
    }

    @Test
    void pathToolActiveOnlyWhenEnabledAndPathSelected() {
        ToolManager tm = ToolManager.get();
        assertFalse(tm.isPathToolActive()); // disabled by default
        tm.toggleEnabled();
        assertTrue(tm.isPathToolActive());
        tm.cycle(1); // now LINE
        assertFalse(tm.isPathToolActive());
    }

}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.lwos.tool.ToolSessionTest"`
Expected: FAIL / compile error — `ToolType`, `PathTool`, `ToolManager` do not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/lwos/tool/ToolType.java`:

```java
package com.lwos.tool;

/** The tools shown in the Alt+Scroll wheel. Only PATH has behavior in M1. */
public enum ToolType {
    PATH("Path", 0x4CAF50),
    LINE("Line", 0x2196F3),
    CIRCLE("Circle", 0xFFC107),
    FILL("Fill", 0x9C27B0),
    TERRAIN_BLEND("Terrain Blend", 0x795548);

    private final String displayName;
    private final int color;

    ToolType(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() { return displayName; }
    public int color() { return color; }
}
```

Create `src/main/java/com/lwos/tool/PathTool.java`:

```java
package com.lwos.tool;

import com.lwos.geometry.PathNode;
import com.lwos.geometry.Vec3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Path tool session state machine. Pure — no Minecraft imports (spec §3.6).
 * M1 uses IDLE and PLACING; PREVIEW is reserved for M3 (block preview) and is
 * never entered in M1 (no blocks are placed).
 */
public class PathTool {
    public enum State { IDLE, PLACING, PREVIEW }

    private final List<PathNode> nodes = new ArrayList<>();
    private State state = State.IDLE;

    public State state() { return state; }

    public List<PathNode> nodes() { return Collections.unmodifiableList(nodes); }

    public void addPoint(Vec3d position) {
        nodes.add(new PathNode(position));
        state = State.PLACING;
    }

    public void deleteLast() {
        if (!nodes.isEmpty()) nodes.remove(nodes.size() - 1);
        if (nodes.isEmpty()) state = State.IDLE;
    }

    public void clear() {
        nodes.clear();
        state = State.IDLE;
    }
}
```

Create `src/main/java/com/lwos/tool/ToolManager.java`:

```java
package com.lwos.tool;

/**
 * Client-side session singleton: selected tool, builder-mode enabled flag, and
 * the current Path tool. Pure — no Minecraft imports (spec §3.6). World picking
 * and rendering live in the Forge client layer, which calls into this.
 */
public class ToolManager {
    private static final ToolManager INSTANCE = new ToolManager();

    public static ToolManager get() { return INSTANCE; }

    private boolean enabled = false;
    private ToolType selected = ToolType.PATH;
    private final PathTool pathTool = new PathTool();

    private ToolManager() { }

    public boolean isEnabled() { return enabled; }

    public void toggleEnabled() {
        enabled = !enabled;
        if (!enabled) pathTool.clear(); // leaving builder mode discards the in-progress path
    }

    public void cycle(int dir) {
        ToolType[] all = ToolType.values();
        int i = (selected.ordinal() + Integer.signum(dir) + all.length) % all.length;
        selected = all[i];
    }

    public ToolType selected() { return selected; }

    public boolean isPathToolActive() { return enabled && selected == ToolType.PATH; }

    public PathTool currentPath() { return pathTool; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.lwos.tool.ToolSessionTest"`
Expected: PASS. Then run the full suite to confirm nothing regressed: `./gradlew test` → all geometry + tool tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/tool/ src/test/java/com/lwos/tool/
git commit -m "feat(tool): add ToolType, PathTool state machine, and ToolManager"
```

---

### Task 6: Key mappings registration

Register three keybinds (toggle builder mode, delete last point, cancel path) and confirm they appear in Options → Controls.

**Files:**
- Create: `src/main/java/com/lwos/client/LwosKeyMappings.java`
- Create: `src/main/java/com/lwos/client/LwosClientModEvents.java`

**Interfaces:**
- Consumes: `LwosMod.MODID` (Task 1).
- Produces:
  - `LwosKeyMappings.TOGGLE_MODE`, `LwosKeyMappings.DELETE_POINT`, `LwosKeyMappings.CANCEL_PATH` (all `public static final KeyMapping`).
  - `LwosClientModEvents` — a `Dist.CLIENT`, MOD-bus `@EventBusSubscriber` that registers the mappings via `RegisterKeyMappingsEvent`.

- [ ] **Step 1: Write the key mappings holder**

Create `src/main/java/com/lwos/client/LwosKeyMappings.java`:

```java
package com.lwos.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class LwosKeyMappings {
    public static final String CATEGORY = "key.categories.lwos";

    public static final KeyMapping TOGGLE_MODE = new KeyMapping(
            "key.lwos.toggle_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    public static final KeyMapping DELETE_POINT = new KeyMapping(
            "key.lwos.delete_point", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    public static final KeyMapping CANCEL_PATH = new KeyMapping(
            "key.lwos.cancel_path", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private LwosKeyMappings() { }
}
```

- [ ] **Step 2: Write the MOD-bus client subscriber that registers them**

Create `src/main/java/com/lwos/client/LwosClientModEvents.java`:

```java
package com.lwos.client;

import com.lwos.LwosMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LwosClientModEvents {
    private LwosClientModEvents() { }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(LwosKeyMappings.TOGGLE_MODE);
        event.register(LwosKeyMappings.DELETE_POINT);
        event.register(LwosKeyMappings.CANCEL_PATH);
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Manual in-game verification**

Run: `./gradlew runClient`. At the main menu → Options → Controls. Scroll to the **"LWOS Builder Tools"** category. Confirm three entries: **Toggle Builder Mode (B)**, **Delete Last Point (Z)**, **Cancel Path (X)** — each with the correct default key and readable (translated, not raw `key.lwos.*`) names. Close the game.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/client/LwosKeyMappings.java src/main/java/com/lwos/client/LwosClientModEvents.java
git commit -m "feat(client): register toggle/delete/cancel key mappings"
```

---

### Task 7: Client input handling — Alt+Scroll cycle, right-click place, delete/cancel

Wire input to `ToolManager`: press **B** to toggle builder mode; hold **Alt** and scroll to cycle the selected tool (without moving the hotbar); right-click to place a control point at the crosshair; **Z** deletes the last point; **X** clears the path.

**Files:**
- Create: `src/main/java/com/lwos/client/LwosInputHandler.java`

**Interfaces:**
- Consumes: `ToolManager` (Task 5), `Vec3d` (Task 2), `LwosKeyMappings` (Task 6).
- Produces: a `Dist.CLIENT`, FORGE-bus `@EventBusSubscriber` handling `InputEvent.Key`, `InputEvent.MouseScrollingEvent`, and `InputEvent.InteractionKeyMappingTriggered`. No new types other tasks depend on.

- [ ] **Step 1: Write the input handler**

Create `src/main/java/com/lwos/client/LwosInputHandler.java`:

```java
package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.geometry.Vec3d;
import com.lwos.tool.ToolManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LwosInputHandler {
    private LwosInputHandler() { }

    private static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.screen == null && mc.player != null;
    }

    private static boolean altHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!inWorld()) return;
        ToolManager tm = ToolManager.get();
        while (LwosKeyMappings.TOGGLE_MODE.consumeClick()) tm.toggleEnabled();
        if (!tm.isEnabled()) return;
        while (LwosKeyMappings.DELETE_POINT.consumeClick()) tm.currentPath().deleteLast();
        while (LwosKeyMappings.CANCEL_PATH.consumeClick()) tm.currentPath().clear();
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!inWorld()) return;
        ToolManager tm = ToolManager.get();
        if (!tm.isEnabled() || !altHeld()) return;
        double delta = event.getScrollDelta();
        if (delta != 0) {
            tm.cycle(delta > 0 ? 1 : -1);
            event.setCanceled(true); // don't move the hotbar selection
        }
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !inWorld()) return;
        ToolManager tm = ToolManager.get();
        if (!tm.isPathToolActive()) return;
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            Vec3 loc = hit.getLocation();
            tm.currentPath().addPoint(new Vec3d(loc.x, loc.y, loc.z));
            event.setSwingHand(false);
            event.setCanceled(true); // suppress vanilla use while placing points
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual in-game verification (temporary log probe)**

Because Task 8/9 (visible wheel + curve) aren't in yet, verify state changes with a quick log line. Temporarily add to the end of `onUse` (inside the `if` block, after `addPoint`):

```java
com.mojang.logging.LogUtils.getLogger().info("LWOS points={}", tm.currentPath().nodes().size());
```

Run `./gradlew runClient`, create/enter a creative world, then:
- Press **B**. Right-click a block → log shows `LWOS points=1`; place two more → `2`, `3`. (Builder enabled.)
- Press **Z** → next right-click log shows the count dropped by one (delete worked).
- Press **X** → next right-click shows `points=1` (path cleared, then one placed).
- Hold **Alt** + scroll → hotbar selection does **not** change (scroll cancelled).
- Press **B** again (disable). Right-click → **no** log line, and vanilla right-click behaves normally.
- **Confirm no blocks were placed or broken at any point.**

Then **remove the temporary log line** and rebuild (`./gradlew build`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/lwos/client/LwosInputHandler.java
git commit -m "feat(client): wire Alt+Scroll cycle, right-click place, delete/cancel input"
```

---

### Task 8: Radial tool-wheel HUD overlay

Draw the Alt+Scroll tool wheel: while builder mode is enabled and Alt is held, show the tool names arranged radially around the screen center with the selected tool highlighted.

**Files:**
- Create: `src/main/java/com/lwos/client/ToolWheelOverlay.java`
- Modify: `src/main/java/com/lwos/client/LwosClientModEvents.java` (register the overlay)

**Interfaces:**
- Consumes: `ToolManager`, `ToolType` (Task 5).
- Produces: `ToolWheelOverlay.INSTANCE` (an `IGuiOverlay`), registered via `RegisterGuiOverlaysEvent`.

- [ ] **Step 1: Write the overlay**

Create `src/main/java/com/lwos/client/ToolWheelOverlay.java`:

```java
package com.lwos.client;

import com.lwos.tool.ToolManager;
import com.lwos.tool.ToolType;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.lwjgl.glfw.GLFW;

public final class ToolWheelOverlay implements IGuiOverlay {
    public static final ToolWheelOverlay INSTANCE = new ToolWheelOverlay();

    private static final int RADIUS = 60;

    private ToolWheelOverlay() { }

    private static boolean altHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        ToolManager tm = ToolManager.get();
        if (mc.screen != null || !tm.isEnabled() || !altHeld()) return;

        Font font = mc.font;
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        // Dim the screen slightly behind the wheel.
        g.fill(cx - RADIUS - 40, cy - RADIUS - 20, cx + RADIUS + 40, cy + RADIUS + 20, 0x80000000);

        ToolType[] tools = ToolType.values();
        int n = tools.length;
        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i / n) - Math.PI / 2; // first tool at top
            int x = cx + (int) Math.round(Math.cos(angle) * RADIUS);
            int y = cy + (int) Math.round(Math.sin(angle) * RADIUS);
            boolean sel = tools[i] == tm.selected();
            int color = sel ? 0xFF00FF00 : (0xFF000000 | tools[i].color());
            String label = (sel ? "> " : "") + tools[i].displayName() + (sel ? " <" : "");
            g.drawCenteredString(font, label, x, y - font.lineHeight / 2, color);
        }
        g.drawCenteredString(font, "Scroll to change tool", cx, cy - 4, 0xFFFFFFFF);
    }
}
```

- [ ] **Step 2: Register the overlay in `LwosClientModEvents`**

Add the import and handler to `src/main/java/com/lwos/client/LwosClientModEvents.java`:

```java
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
```

Add this method inside the class:

```java
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("tool_wheel", ToolWheelOverlay.INSTANCE);
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Manual in-game verification**

Run `./gradlew runClient`, enter a creative world:
- Press **B** (enable). Hold **Alt** → the radial wheel appears with all five tool names around the center; **Path** is highlighted green with `>` `<` markers.
- While holding Alt, scroll → the highlight moves through the tools and wraps around; the hotbar does not move.
- Release Alt → the wheel disappears.
- Press **B** (disable) → holding Alt shows no wheel.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/client/ToolWheelOverlay.java src/main/java/com/lwos/client/LwosClientModEvents.java
git commit -m "feat(client): radial Alt+Scroll tool wheel overlay"
```

---

### Task 9: In-world path renderer — gizmos + smooth curve (M1 definition of done)

Render the placed control points as small box gizmos and the smooth centripetal Catmull-Rom curve through them as a green line strip. This completes the M1 loop: place points → see the curve update live.

**Files:**
- Create: `src/main/java/com/lwos/client/PathRenderer.java`

**Interfaces:**
- Consumes: `ToolManager`, `PathTool` (Task 5); `PathNode`, `Vec3d` (Task 2); `PathSampler` (Task 4).
- Produces: a `Dist.CLIENT`, FORGE-bus `@EventBusSubscriber` handling `RenderLevelStageEvent`. No new types other tasks depend on.

- [ ] **Step 1: Write the renderer**

Create `src/main/java/com/lwos/client/PathRenderer.java`:

```java
package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.geometry.PathNode;
import com.lwos.geometry.Vec3d;
import com.lwos.tool.ToolManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PathRenderer {
    private static final double SAMPLE_SPACING = 0.25; // blocks between curve samples
    private static final double GIZMO = 0.15;          // half-size of a control-point box

    private PathRenderer() { }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        ToolManager tm = ToolManager.get();
        if (!tm.isPathToolActive()) return;

        List<PathNode> nodes = tm.currentPath().nodes();
        if (nodes.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f mat = ps.last().pose();
        Matrix3f nor = ps.last().normal();

        // Control-point gizmos (white boxes).
        for (PathNode node : nodes) {
            Vec3d p = node.position();
            AABB box = new AABB(p.x() - GIZMO, p.y() - GIZMO, p.z() - GIZMO,
                                p.x() + GIZMO, p.y() + GIZMO, p.z() + GIZMO);
            LevelRenderer.renderLineBox(ps, lines, box, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        // Smooth curve (green line strip through evenly-spaced samples).
        List<Vec3d> positions = new ArrayList<>(nodes.size());
        for (PathNode node : nodes) positions.add(node.position());
        List<Vec3d> curve = com.lwos.geometry.PathSampler.sample(positions, SAMPLE_SPACING);
        for (int i = 0; i < curve.size() - 1; i++) {
            addLine(lines, mat, nor, curve.get(i), curve.get(i + 1));
        }

        ps.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void addLine(VertexConsumer c, Matrix4f mat, Matrix3f nor, Vec3d a, Vec3d b) {
        float nx = (float) (b.x() - a.x());
        float ny = (float) (b.y() - a.y());
        float nz = (float) (b.z() - a.z());
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return;
        nx /= len; ny /= len; nz /= len;
        c.vertex(mat, (float) a.x(), (float) a.y(), (float) a.z())
                .color(0, 255, 0, 255).normal(nor, nx, ny, nz).endVertex();
        c.vertex(mat, (float) b.x(), (float) b.y(), (float) b.z())
                .color(0, 255, 0, 255).normal(nor, nx, ny, nz).endVertex();
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual in-game verification — the full M1 definition of done**

Run `./gradlew runClient`, enter a creative world:
1. Press **B** to enable builder mode; hold **Alt**, scroll until **Path** is highlighted, release Alt.
2. Right-click three or more spots on the ground. Each shows a small white box gizmo, and a **smooth green curve** connects them — curving through the points, not straight-segmented.
3. Place another point → the curve updates live to include it.
4. Press **Z** → the last point and its curve section disappear.
5. Press **X** → all points and the curve disappear.
6. Press **B** to disable → gizmos/curve are gone and right-click behaves normally.
7. **Confirm: no blocks were ever placed, broken, or changed.**

- [ ] **Step 4: Run the full automated suite one more time**

Run: `./gradlew test`
Expected: all geometry + tool tests PASS (Tasks 2–5).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/client/PathRenderer.java
git commit -m "feat(client): render control-point gizmos and live Catmull-Rom curve"
```

---

## Definition of Done (Milestone 1)

- `./gradlew build` and `./gradlew test` both succeed; all geometry/tool unit tests pass.
- In a creative world: enable builder mode (B), pick Path from the Alt+Scroll wheel, right-click to place points, and see a smooth curve update live through the control-point gizmos.
- Delete last point (Z) and cancel path (X) work; disabling builder mode (B) restores normal play.
- **Zero blocks are placed, removed, or modified anywhere in this milestone.**
- No `net.minecraft.*` import exists in any `com.lwos.geometry.*` or `com.lwos.tool.*` class.
