# Path Edge System Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the path builder controllable rugged edges, sparse-and-clustered edge-block scatter, and an outward meld into surrounding terrain — by decoupling edge erosion from path width, adding real coverage/cluster/reach controls, and guaranteeing a protected spine.

**Architecture:** The pure `EditPlanBuilder` pipeline (Spline → sample → snap → mask → edge-shape → gradient → blend → scatter → `EditPlan`) is preserved. Edge erosion becomes an absolute block amount whose depth is bounded only by a hard core-protection clamp (not by the path's half-width). A new `EdgeScatterEngine` replaces `EdgeBandEngine` and drives edge-block placement from `edgeCoverage` (density) and `edgeClusterSize` (patch size), across an inner feather band *and* an outward `edgeReach` band. All controls live in `PathStyle`, which already rides the wire as JSON, so `preview ≡ apply` is unchanged.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1 (Forge 47.4.10), Gson, JUnit 5, Gradle. Build: `./gradlew build`. Tests: `./gradlew test`. Single test class: `./gradlew test --tests "<FQCN>"`.

## Global Constraints

- **Purity boundary:** `com.lwos.plan`, `com.lwos.config`, `com.lwos.geometry`, `com.lwos.organic` MUST NOT import `net.minecraft.*`, `com.mojang.*`, or `net.minecraftforge.*`. They are headlessly unit-tested. Minecraft-bound code lives only in `com.lwos.client`, `com.lwos.apply`, `com.lwos.apply.net`, `com.lwos.ui`.
- **Determinism:** same `(controlPoints, spacing, width, mode, style)` + same `WorldView` answers → byte-identical `EditPlan`. No wall-clock reads, no unseeded `java.util.Random`. The operation seed is derived from the control points inside `EditPlanBuilder` and never crosses the wire.
- **preview ≡ apply:** the client preview and the server apply both call the same `EditPlanBuilder.build(...)` with the same `PathStyle`. Every new control must be a field on `PathStyle` so it serializes in `styleJson`; no new packet fields.
- **Windows/Git Bash:** shell is Git Bash. Run Gradle as `./gradlew` from the repo root.

---

### Task 1: `EdgeScatterEngine` — density + cluster-driven edge scatter (pure)

New pure engine that decides, per column, whether to place an edge-palette block, driven by coverage (how many) and cluster size (how patchy), across the inner feather band and the outward reach band. Built and tested in isolation; wired into the builder in Task 3. `EdgeBandEngine` is left in place for now so the build stays green.

**Files:**
- Create: `src/main/java/com/lwos/organic/EdgeScatterEngine.java`
- Test: `src/test/java/com/lwos/organic/EdgeScatterEngineTest.java`

**Interfaces:**
- Consumes: `com.lwos.geometry.PathMask` (`edgeDistance(int,int)`, `edgeDistances()`, `of(Map)`), `com.lwos.geometry.ColumnPos`, `com.lwos.organic.Palette`, `com.lwos.organic.GradientEngine` (`blockAt(int,int,int)`), `com.lwos.organic.noise.PerlinNoise` (`noise(double,double)`), `com.lwos.plan.BlockStateRef`.
- Produces: `new EdgeScatterEngine(long seed, double blendDepth, double edgeReach, double coverage, double clusterSize, Palette edgePalette)` and `Optional<BlockStateRef> scatterBlockAt(PathMask mask, int x, int z)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/organic/EdgeScatterEngineTest.java`:

```java
package com.lwos.organic;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import com.lwos.plan.BlockStateRef;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EdgeScatterEngineTest {

    private static final long SEED = 0xC0FFEEL;

    /** A straight path along the x-axis centred on z=0 with the given half-width; signed distance is
     *  |z_center| - halfWidth. Tracks columns from -pad..+pad blocks beyond the rim on each side. */
    private static PathMask straightBand(double halfWidth, int pad) {
        Map<ColumnPos, Double> d = new HashMap<>();
        int zLimit = (int) Math.ceil(halfWidth) + pad + 1;
        for (int x = 0; x <= 20; x++) {
            for (int z = -zLimit; z <= zLimit; z++) {
                d.put(new ColumnPos(x, z), Math.abs(z + 0.5) - halfWidth);
            }
        }
        return PathMask.of(d);
    }

    private static Palette edgePalette() {
        return new Palette(java.util.List.of(
                new Palette.Entry(new BlockStateRef("minecraft:coarse_dirt"), 1.0, 0.1, 4.0)));
    }

    private static Set<ColumnPos> scattered(EdgeScatterEngine e, PathMask mask) {
        Set<ColumnPos> hits = new HashSet<>();
        for (ColumnPos c : mask.edgeDistances().keySet()) {
            if (e.scatterBlockAt(mask, c.x(), c.z()).isPresent()) hits.add(c);
        }
        return hits;
    }

    @Test
    void zeroCoveragePlacesNothing() {
        PathMask mask = straightBand(2.0, 3);
        EdgeScatterEngine e = new EdgeScatterEngine(SEED, 2.0, 3.0, 0.0, 4.0, edgePalette());
        assertTrue(scattered(e, mask).isEmpty(), "coverage 0 must place no edge blocks");
    }

    @Test
    void coverageIsMonotonic() {
        PathMask mask = straightBand(2.0, 3);
        int low = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.3, 4.0, edgePalette()), mask).size();
        int high = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 1.0, 4.0, edgePalette()), mask).size();
        assertTrue(high >= low, "more coverage must never scatter fewer blocks (" + high + " >= " + low + ")");
        assertTrue(high > 0, "full coverage must scatter some blocks");
    }

    @Test
    void scatterIsSparseNotSolid() {
        PathMask mask = straightBand(2.0, 3);
        EdgeScatterEngine e = new EdgeScatterEngine(SEED, 2.0, 3.0, 0.5, 4.0, edgePalette());
        Set<ColumnPos> hits = scattered(e, mask);
        long candidates = mask.edgeDistances().values().stream()
                .filter(d -> d > -2.0 && d <= 3.0).count();
        assertTrue(hits.size() > 0 && hits.size() < candidates,
                "scatter must be sparse: some but not all candidates (" + hits.size() + " of " + candidates + ")");
    }

    @Test
    void clusterSizeChangesThePattern() {
        PathMask mask = straightBand(2.0, 3);
        Set<ColumnPos> fine = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.6, 2.0, edgePalette()), mask);
        Set<ColumnPos> broad = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.6, 12.0, edgePalette()), mask);
        assertNotEquals(fine, broad, "changing cluster size must change which columns scatter");
    }

    @Test
    void reachPlacesBlocksOutsideTheRim() {
        PathMask mask = straightBand(2.0, 4);
        EdgeScatterEngine withReach = new EdgeScatterEngine(SEED, 2.0, 4.0, 1.0, 4.0, edgePalette());
        boolean anyOutside = scattered(withReach, mask).stream()
                .anyMatch(c -> mask.edgeDistance(c.x(), c.z()) > 0.0);
        assertTrue(anyOutside, "edgeReach > 0 must scatter blocks onto terrain outside the rim");

        EdgeScatterEngine noReach = new EdgeScatterEngine(SEED, 2.0, 0.0, 1.0, 4.0, edgePalette());
        boolean noneOutside = scattered(noReach, mask).stream()
                .noneMatch(c -> mask.edgeDistance(c.x(), c.z()) > 0.0);
        assertTrue(noneOutside, "edgeReach 0 must place nothing outside the rim");
    }

    @Test
    void deepInteriorAndBeyondReachAreNeverCandidates() {
        Map<ColumnPos, Double> d = new HashMap<>();
        d.put(new ColumnPos(0, 0), -5.0);  // deep inside, past blendDepth
        d.put(new ColumnPos(1, 0), 9.0);   // far outside, past edgeReach
        PathMask mask = PathMask.of(d);
        EdgeScatterEngine e = new EdgeScatterEngine(SEED, 2.0, 3.0, 1.0, 4.0, edgePalette());
        assertEquals(Optional.empty(), e.scatterBlockAt(mask, 0, 0));
        assertEquals(Optional.empty(), e.scatterBlockAt(mask, 1, 0));
    }

    @Test
    void isDeterministic() {
        PathMask mask = straightBand(2.0, 3);
        Set<ColumnPos> a = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.5, 4.0, edgePalette()), mask);
        Set<ColumnPos> b = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.5, 4.0, edgePalette()), mask);
        assertEquals(a, b, "same seed + inputs must scatter identically");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.lwos.organic.EdgeScatterEngineTest"`
Expected: FAIL — compilation error, `EdgeScatterEngine` does not exist.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/lwos/organic/EdgeScatterEngine.java`:

```java
package com.lwos.organic;

import com.lwos.geometry.PathMask;
import com.lwos.organic.noise.PerlinNoise;
import com.lwos.plan.BlockStateRef;

import java.util.Optional;

/**
 * Decides, per column, whether to place an edge-palette block, giving the builder direct control
 * over how many edge blocks appear ({@code coverage}) and how patchy they are ({@code clusterSize}).
 * Supersedes {@link EdgeBandEngine}: the scatter band spans the inner feather skirt
 * ({@code -blendDepth < d <= 0}) AND an outward reach onto surrounding terrain
 * ({@code 0 < d <= edgeReach}), so the path melds into the ground instead of ending on a clean line.
 *
 * <p>Placement at column {@code (x,z)} with signed edge distance {@code d}:
 * place iff {@code coverage * falloff(d) > coin(x,z)}, where {@code falloff} peaks at the rim
 * ({@code d ≈ 0}) and smoothsteps to 0 at each band limit, and {@code coin} is a seeded Perlin field
 * sampled at frequency {@code 1/clusterSize} — so cluster size actually drives the patch size
 * (the field {@link EdgeBandEngine} left fixed). The kept column's block is chosen by a
 * {@link GradientEngine} over the edge palette so the scatter still clusters by material.
 *
 * <p>Pure and deterministic: same seed + inputs → byte-identical decisions.
 */
public final class EdgeScatterEngine {

    /** Keeps the keep/drop coin field independent of the material-choice fields. */
    private static final long COIN_SALT = 0x51ED270B7C3D5A11L;

    private final double blendDepth;   // inner band depth in blocks (>= 0)
    private final double edgeReach;    // outward band depth in blocks (>= 0)
    private final double coverage;     // baseline density in [0, 1]
    private final double clusterScale; // 1 / clusterSize; the coin sampling frequency
    private final GradientEngine material;
    private final PerlinNoise coin;

    public EdgeScatterEngine(long seed, double blendDepth, double edgeReach,
                             double coverage, double clusterSize, Palette edgePalette) {
        if (clusterSize <= 0) {
            throw new IllegalArgumentException("clusterSize must be > 0, got " + clusterSize);
        }
        this.blendDepth = Math.max(0.0, blendDepth);
        this.edgeReach = Math.max(0.0, edgeReach);
        this.coverage = Math.max(0.0, Math.min(1.0, coverage));
        this.clusterScale = 1.0 / clusterSize;
        this.material = new GradientEngine(seed, edgePalette);
        this.coin = new PerlinNoise(seed ^ COIN_SALT);
    }

    /**
     * The edge block for {@code (x,z)}, or empty to leave the terrain. Empty outside the scatter band
     * and whenever the clustered coin beats the coverage-scaled fill probability.
     */
    public Optional<BlockStateRef> scatterBlockAt(PathMask mask, int x, int z) {
        double d = mask.edgeDistance(x, z);
        double fill = coverage * falloff(d);
        if (fill <= 0.0) return Optional.empty();
        double sample = (coin.noise(x * clusterScale, z * clusterScale) + 1.0) / 2.0; // [0, 1)
        if (sample >= fill) return Optional.empty();
        return Optional.of(material.blockAt(x, 0, z));
    }

    /**
     * Density shaping: 1.0 at the rim ({@code d = 0}), smoothstepping to 0.0 at the inner limit
     * ({@code d = -blendDepth}) and the outer limit ({@code d = edgeReach}); 0.0 outside the band.
     */
    double falloff(double d) {
        if (d <= 0.0) {
            if (blendDepth <= 0.0 || d <= -blendDepth) return 0.0;
            double t = (blendDepth + d) / blendDepth; // 0 at inner limit, 1 at the rim
            return smoothstep(t);
        }
        if (edgeReach <= 0.0 || d >= edgeReach) return 0.0;
        double t = 1.0 - d / edgeReach; // 1 at the rim, 0 at the outer limit
        return smoothstep(t);
    }

    private static double smoothstep(double t) { return t * t * (3 - 2 * t); }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.lwos.organic.EdgeScatterEngineTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lwos/organic/EdgeScatterEngine.java src/test/java/com/lwos/organic/EdgeScatterEngineTest.java
git commit -m "feat(organic): EdgeScatterEngine with coverage + cluster-driven edge scatter

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Migrate `PathStyle` to the absolute edge knobs + wire the UI

Replace the fraction-of-band edge scalars with absolute-block controls and expose them in the panel.
This is the model flip; it touches config + all its callers so the build stays green. The
`EditPlanBuilder` change here is minimal (rename getters, absolute feather, keep the erosion cap and
`EdgeBandEngine`); Task 3 does the real erosion/scatter rework. The three brand-new knobs
(`edgeCoverage`, `edgeClusterSize`, `edgeReach`) are stored and editable but do not affect output
until Task 3 — an intentional dormant step.

**Files:**
- Modify: `src/main/java/com/lwos/config/PathStyle.java` (full rewrite of fields/constructor/factories/JSON)
- Modify: `src/main/java/com/lwos/plan/EditPlanBuilder.java:87-89` (two lines: erosion + feather resolution)
- Modify: `src/main/java/com/lwos/ui/PathStyleEdits.java` (rebuild + setters)
- Modify: `src/main/java/com/lwos/ui/PathStylePanel.java:104-117` (slider block)
- Modify: `src/main/java/com/lwos/ui/PathStylePanelInput.java:163-175` (applySlider switch)
- Modify: `src/test/java/com/lwos/config/PathStyleTest.java` (new getters)

**Interfaces:**
- Consumes: nothing new from Task 1.
- Produces: `new PathStyle(List<Entry> core, List<Entry> edge, double edgeErosion, double edgeFeatureSize, double coreProtect, double blendDepth, double edgeCoverage, double edgeClusterSize, double edgeReach, double defaultClusterSize)`; getters `edgeErosion()`, `edgeFeatureSize()`, `coreProtect()`, `blendDepth()`, `edgeCoverage()`, `edgeClusterSize()`, `edgeReach()`, `defaultClusterSize()`, `core()`, `edge()`, `toCorePalette()`, `toEdgePalette()`; factories `defaults()`, `neutral()`; `toJson()`/`fromJson(String)`. `PathStyleEdits` setters: `setEdgeErosion`, `setEdgeFeatureSize`, `setCoreProtect`, `setBlendDepth`, `setEdgeCoverage`, `setEdgeClusterSize`, `setEdgeReach`, `setClusterSize`.

- [ ] **Step 1: Update `PathStyleTest` to the new getters (failing test)**

Replace the body of `src/test/java/com/lwos/config/PathStyleTest.java` methods `neutralIsATrueIdentity` and `defaultsHaveCoreAndEdgePalettes` (leave `jsonRoundTripPreservesEverything`, `fromJsonRejectsNonPositiveWeightEagerly`, `toCorePaletteMapsEveryEntry` unchanged):

```java
    @Test
    void neutralIsATrueIdentity() {
        PathStyle s = PathStyle.neutral();
        assertEquals(0.0, s.edgeErosion(), "neutral does not erode the edge");
        assertEquals(0.0, s.blendDepth(), "neutral does not feather");
        assertEquals(0.0, s.edgeCoverage(), "neutral scatters no edge blocks");
        assertEquals(0.0, s.edgeReach(), "neutral does not reach onto terrain");
        assertEquals(1.0, s.coreProtect(), "neutral protects the whole footprint");
        assertEquals(1, s.core().size());
        assertEquals("minecraft:dirt_path", s.core().get(0).id());
        assertTrue(s.edge().isEmpty(), "neutral has no edge shoulder");
        assertTrue(s.toEdgePalette().isEmpty());
    }

    @Test
    void defaultsHaveCoreAndEdgePalettes() {
        PathStyle s = PathStyle.defaults();
        assertTrue(s.core().size() > 1, "defaults offer clustered core materials");
        assertFalse(s.edge().isEmpty(), "defaults include an edge shoulder");
        assertTrue(s.blendDepth() > 0, "defaults feather the edge");
        assertTrue(s.edgeErosion() > 0, "defaults erode the edge");
        assertTrue(s.edgeCoverage() > 0, "defaults scatter edge blocks");
        assertTrue(s.edgeReach() > 0, "defaults meld onto surrounding terrain");
        assertTrue(s.coreProtect() > 0 && s.coreProtect() < 1, "defaults keep a protected spine but allow a band");
        assertTrue(s.toEdgePalette().isPresent());
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.lwos.config.PathStyleTest"`
Expected: FAIL — compilation error, `edgeErosion()`/`blendDepth()`/`edgeCoverage()`/`edgeReach()` do not exist.

- [ ] **Step 3: Rewrite `PathStyle` fields, constructor, factories, and JSON**

Replace the field block, constructor, getters, factories, `toJson`/`fromJson`, `equals`/`hashCode`, and the `Dto` in `src/main/java/com/lwos/config/PathStyle.java`. Keep `Entry`, `toCorePalette`, `toEdgePalette`, `toPalette`, `clamp01`, `toRaw`, `parseEntries` as they are. The new members:

```java
    private final List<Entry> core;
    private final List<Entry> edge;
    private final double edgeErosion;      // ragged-edge depth, in blocks (>= 0), uncapped by width
    private final double edgeFeatureSize;  // erosion feature size, in blocks (>= 1e-3)
    private final double coreProtect;      // protected inner spine, fraction of half-width (0..1)
    private final double blendDepth;       // inward feather skirt, in blocks (>= 0)
    private final double edgeCoverage;     // edge-scatter density (0..1)
    private final double edgeClusterSize;  // edge-scatter patch size, in blocks (>= 1e-3)
    private final double edgeReach;        // outward scatter onto terrain, in blocks (>= 0)
    private final double defaultClusterSize;

    public PathStyle(List<Entry> core, List<Entry> edge, double edgeErosion, double edgeFeatureSize,
                     double coreProtect, double blendDepth, double edgeCoverage, double edgeClusterSize,
                     double edgeReach, double defaultClusterSize) {
        if (core == null || core.isEmpty()) {
            throw new IllegalArgumentException("core palette must have at least one entry");
        }
        this.core = List.copyOf(core);
        this.edge = edge == null ? List.of() : List.copyOf(edge);
        this.edgeErosion = Math.max(0.0, edgeErosion);
        this.edgeFeatureSize = Math.max(1e-3, edgeFeatureSize);
        this.coreProtect = clamp01(coreProtect);
        this.blendDepth = Math.max(0.0, blendDepth);
        this.edgeCoverage = clamp01(edgeCoverage);
        this.edgeClusterSize = Math.max(1e-3, edgeClusterSize);
        this.edgeReach = Math.max(0.0, edgeReach);
        this.defaultClusterSize = defaultClusterSize;
        // Eager validation: reject a bad palette entry now, not later on the render/apply thread.
        toCorePalette();
        toEdgePalette();
    }

    public List<Entry> core() { return core; }
    public List<Entry> edge() { return edge; }

    /** Ragged-edge depth, in blocks. Bounded only by the core-protection clamp, not by path width. */
    public double edgeErosion() { return edgeErosion; }

    /** Erosion feature size, in blocks (larger = broad bays, smaller = fine rugged teeth). */
    public double edgeFeatureSize() { return edgeFeatureSize; }

    /** Protected inner spine as a fraction of the half-width (0..1); never eroded or feathered. */
    public double coreProtect() { return coreProtect; }

    /** Inward feather skirt depth, in blocks. 0 = hard edge. */
    public double blendDepth() { return blendDepth; }

    /** Edge-scatter density (0..1): how many edge blocks appear. 0 = none, 1 = solid shoulder. */
    public double edgeCoverage() { return edgeCoverage; }

    /** Edge-scatter patch size, in blocks: big = broad clumps, small = fine speckle. */
    public double edgeClusterSize() { return edgeClusterSize; }

    /** How far edge blocks scatter outward onto surrounding terrain, in blocks. 0 = none. */
    public double edgeReach() { return edgeReach; }

    public double defaultClusterSize() { return defaultClusterSize; }
```

Replace the factories:

```java
    /** The real organic look: multi-material core, coarse-dirt/moss edge shoulder, rugged + melded. */
    public static PathStyle defaults() {
        double cluster = 5.0;
        List<Entry> core = List.of(
                new Entry("minecraft:dirt_path", 3.0, 0.1, cluster),
                new Entry("minecraft:coarse_dirt", 1.0, 0.1, cluster),
                new Entry("minecraft:gravel", 0.6, 0.1, cluster));
        List<Entry> edge = List.of(
                new Entry("minecraft:coarse_dirt", 1.0, 0.1, cluster),
                new Entry("minecraft:moss_block", 0.6, 0.1, cluster));
        // core, edge, edgeErosion, edgeFeatureSize, coreProtect, blendDepth, edgeCoverage,
        // edgeClusterSize, edgeReach, defaultClusterSize
        return new PathStyle(core, edge, 1.5, 5.0, 0.4, 2.0, 0.5, 4.0, 2.0, cluster);
    }

    /** True identity: no erosion, no scatter, no reach, fully protected core, single dirt_path. */
    public static PathStyle neutral() {
        return new PathStyle(List.of(new Entry("minecraft:dirt_path", 1.0, 0.1, 5.0)),
                List.of(), 0.0, 5.0, 1.0, 0.0, 0.0, 4.0, 0.0, 5.0);
    }
```

Replace `toJson` body assignments, `fromJson`, the `equals`/`hashCode`, and the `Dto`:

```java
    public String toJson() {
        Dto dto = new Dto();
        dto.core = toRaw(core);
        dto.edge = toRaw(edge);
        dto.edgeErosion = edgeErosion;
        dto.edgeFeatureSize = edgeFeatureSize;
        dto.coreProtect = coreProtect;
        dto.blendDepth = blendDepth;
        dto.edgeCoverage = edgeCoverage;
        dto.edgeClusterSize = edgeClusterSize;
        dto.edgeReach = edgeReach;
        dto.defaultClusterSize = defaultClusterSize;
        return GSON.toJson(dto);
    }

    /**
     * Parses a style from JSON. Missing scalars fall back to {@link #defaults()} (old saved styles
     * lacking the new fields simply load with default edges). A missing/empty core palette falls back
     * to the default core. Pure — no IO. Throws {@link IllegalArgumentException} on an invalid entry
     * (weight/clusterSize <= 0) via the constructor's eager validation.
     */
    public static PathStyle fromJson(String json) {
        Dto raw = GSON.fromJson(json, Dto.class);
        if (raw == null) return defaults();
        PathStyle d = defaults();
        double erosion = raw.edgeErosion != null ? raw.edgeErosion : d.edgeErosion;
        double featureSize = raw.edgeFeatureSize != null ? raw.edgeFeatureSize : d.edgeFeatureSize;
        double core0 = raw.coreProtect != null ? raw.coreProtect : d.coreProtect;
        double blend = raw.blendDepth != null ? raw.blendDepth : d.blendDepth;
        double coverage = raw.edgeCoverage != null ? raw.edgeCoverage : d.edgeCoverage;
        double edgeCluster = raw.edgeClusterSize != null ? raw.edgeClusterSize : d.edgeClusterSize;
        double reach = raw.edgeReach != null ? raw.edgeReach : d.edgeReach;
        double cluster = raw.defaultClusterSize != null ? raw.defaultClusterSize : d.defaultClusterSize;
        List<Entry> core = parseEntries(raw.core, cluster);
        List<Entry> edge = parseEntries(raw.edge, cluster);
        if (core.isEmpty()) core = d.core;
        return new PathStyle(core, edge, erosion, featureSize, core0, blend, coverage, edgeCluster,
                reach, cluster);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PathStyle p)) return false;
        return Double.compare(p.edgeErosion, edgeErosion) == 0
                && Double.compare(p.edgeFeatureSize, edgeFeatureSize) == 0
                && Double.compare(p.coreProtect, coreProtect) == 0
                && Double.compare(p.blendDepth, blendDepth) == 0
                && Double.compare(p.edgeCoverage, edgeCoverage) == 0
                && Double.compare(p.edgeClusterSize, edgeClusterSize) == 0
                && Double.compare(p.edgeReach, edgeReach) == 0
                && Double.compare(p.defaultClusterSize, defaultClusterSize) == 0
                && core.equals(p.core) && edge.equals(p.edge);
    }

    @Override public int hashCode() {
        return Objects.hash(core, edge, edgeErosion, edgeFeatureSize, coreProtect, blendDepth,
                edgeCoverage, edgeClusterSize, edgeReach, defaultClusterSize);
    }

    private static final class Dto {
        List<RawEntry> core;
        List<RawEntry> edge;
        Double edgeErosion;
        Double edgeFeatureSize;
        Double coreProtect;
        Double blendDepth;
        Double edgeCoverage;
        Double edgeClusterSize;
        Double edgeReach;
        Double defaultClusterSize;
    }
```

Also update the class Javadoc's "Width-relative knobs" paragraph to describe the new absolute knobs (`edgeErosion`, `blendDepth`, `edgeReach` in blocks; `coreProtect` the guaranteed spine; `edgeCoverage`/`edgeClusterSize` drive scatter).

- [ ] **Step 4: Update `EditPlanBuilder` to compile against the new getters**

In `src/main/java/com/lwos/plan/EditPlanBuilder.java`, replace the three resolution lines (currently 87-89):

```java
        double erosionAmp = style.edgeRoughness() * edgeBandWidth; // <= edgeBandWidth (roughness in [0,1])
        double featherBand = style.featherDepth() * edgeBandWidth;
        double edgeScale = 1.0 / Math.max(1e-3, style.edgeFeatureSize());
```

with:

```java
        // TEMP (Task 2): erosion still capped at the band and feather now absolute, so the build stays
        // green with the existing EdgeBandEngine. Task 3 removes the cap, adds the core-protection
        // clamp, and swaps in EdgeScatterEngine + edgeReach.
        double erosionAmp = Math.min(style.edgeErosion(), edgeBandWidth);
        double featherBand = style.blendDepth();
        double edgeScale = 1.0 / Math.max(1e-3, style.edgeFeatureSize());
```

- [ ] **Step 5: Rewrite `PathStyleEdits` rebuild + setters**

Replace `rebuild`, `setFeatherDepth`, `setEdgeRoughness`, `setEdgeFeatureSize`, `setCoreProtect`, and `setClusterSize` in `src/main/java/com/lwos/ui/PathStyleEdits.java`, and add the three new setters. The slot/weight/remove helpers stay as-is:

```java
    private static PathStyle rebuild(List<PathStyle.Entry> core, List<PathStyle.Entry> edge, PathStyle s) {
        return new PathStyle(core, edge, s.edgeErosion(), s.edgeFeatureSize(), s.coreProtect(),
                s.blendDepth(), s.edgeCoverage(), s.edgeClusterSize(), s.edgeReach(), s.defaultClusterSize());
    }

    public static void setEdgeErosion(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(rebuild(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()), v, s.edgeFeatureSize(),
                        s.coreProtect(), s.blendDepth(), s.edgeCoverage(), s.edgeClusterSize(),
                        s.edgeReach(), s.defaultClusterSize())));
    }

    public static void setEdgeFeatureSize(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                s.edgeErosion(), Math.max(0.001, v), s.coreProtect(), s.blendDepth(), s.edgeCoverage(),
                s.edgeClusterSize(), s.edgeReach(), s.defaultClusterSize()));
    }

    public static void setCoreProtect(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                s.edgeErosion(), s.edgeFeatureSize(), v, s.blendDepth(), s.edgeCoverage(),
                s.edgeClusterSize(), s.edgeReach(), s.defaultClusterSize()));
    }

    public static void setBlendDepth(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                s.edgeErosion(), s.edgeFeatureSize(), s.coreProtect(), v, s.edgeCoverage(),
                s.edgeClusterSize(), s.edgeReach(), s.defaultClusterSize()));
    }

    public static void setEdgeCoverage(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                s.edgeErosion(), s.edgeFeatureSize(), s.coreProtect(), s.blendDepth(), v,
                s.edgeClusterSize(), s.edgeReach(), s.defaultClusterSize()));
    }

    public static void setEdgeClusterSize(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                s.edgeErosion(), s.edgeFeatureSize(), s.coreProtect(), s.blendDepth(), s.edgeCoverage(),
                Math.max(0.5, v), s.edgeReach(), s.defaultClusterSize()));
    }

    public static void setEdgeReach(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                s.edgeErosion(), s.edgeFeatureSize(), s.coreProtect(), s.blendDepth(), s.edgeCoverage(),
                s.edgeClusterSize(), v, s.defaultClusterSize()));
    }

    public static void setClusterSize(double cluster) {
        PathStyle s = StyleManager.active();
        double c = Math.max(0.5, cluster);
        List<PathStyle.Entry> core = new ArrayList<>();
        for (PathStyle.Entry e : s.core()) core.add(new PathStyle.Entry(e.id(), e.weight(), e.noiseScale(), c));
        StyleManager.setActive(new PathStyle(core, new ArrayList<>(s.edge()), s.edgeErosion(),
                s.edgeFeatureSize(), s.coreProtect(), s.blendDepth(), s.edgeCoverage(), s.edgeClusterSize(),
                s.edgeReach(), c));
    }
```

Note: `setEdgeErosion` is written the simplest correct way — construct the updated style directly rather than nesting `rebuild`. Use this simpler form to avoid the double-construct above:

```java
    public static void setEdgeErosion(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                v, s.edgeFeatureSize(), s.coreProtect(), s.blendDepth(), s.edgeCoverage(),
                s.edgeClusterSize(), s.edgeReach(), s.defaultClusterSize()));
    }
```

(Use this `setEdgeErosion`; discard the nested-`rebuild` version shown first.)

- [ ] **Step 6: Update the panel sliders**

In `src/main/java/com/lwos/ui/PathStylePanel.java`, replace the Outskirts feather slider and the whole ADVANCED slider block (currently lines 104-117) with:

```java
        // Feather depth (inward blend), in blocks
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Blend depth", s.blendDepth(),
                "blend", 0, 0, 8, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge coverage", s.edgeCoverage(),
                "coverage", 0, 0, 1, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge cluster", s.edgeClusterSize(),
                "edgecluster", 0, 1, 16, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge reach", s.edgeReach(),
                "reach", 0, 0, 6, sliders);

        // Advanced
        y = section(g, font, cx, y, x + PANEL_W - PAD, "ADVANCED");
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge erosion", s.edgeErosion(),
                "erosion", 0, 0, 8, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge feature size", s.edgeFeatureSize(),
                "feature", 0, 1, 16, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Core protect", s.coreProtect(),
                "core", 0, 0, 1, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Core cluster size", s.defaultClusterSize(),
                "cluster", 0, 1, 20, sliders);
```

- [ ] **Step 7: Update the slider dispatch**

In `src/main/java/com/lwos/ui/PathStylePanelInput.java`, replace the `applySlider` switch (lines 165-174) with:

```java
        switch (s.target()) {
            case "coreWeight"  -> PathStyleEdits.setCoreWeight(s.index(), value);
            case "edgeWeight"  -> PathStyleEdits.setEdgeWeight(s.index(), value);
            case "blend"       -> PathStyleEdits.setBlendDepth(value);
            case "coverage"    -> PathStyleEdits.setEdgeCoverage(value);
            case "edgecluster" -> PathStyleEdits.setEdgeClusterSize(value);
            case "reach"       -> PathStyleEdits.setEdgeReach(value);
            case "cluster"     -> PathStyleEdits.setClusterSize(value);
            case "erosion"     -> PathStyleEdits.setEdgeErosion(value);
            case "feature"     -> PathStyleEdits.setEdgeFeatureSize(value);
            case "core"        -> PathStyleEdits.setCoreProtect(value);
            default -> { }
        }
```

- [ ] **Step 8: Run the full test suite**

Run: `./gradlew test`
Expected: PASS. `PathStyleTest`, `EditRequestPacketStyleTest`, `EditPlanBuilderEdgeBandTest`, `StyleManagerTest`, `StyleManagerPresetTest`, `PathStylePanelScrollTest` all green (defaults still place an edge shoulder via the unchanged `EdgeBandEngine`; `neutral` still yields a uniform `dirt_path` footprint because `blendDepth` is 0).

- [ ] **Step 9: Build to confirm the Minecraft-bound UI compiles**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/lwos/config/PathStyle.java src/main/java/com/lwos/plan/EditPlanBuilder.java src/main/java/com/lwos/ui/PathStyleEdits.java src/main/java/com/lwos/ui/PathStylePanel.java src/main/java/com/lwos/ui/PathStylePanelInput.java src/test/java/com/lwos/config/PathStyleTest.java
git commit -m "feat(config): absolute-unit edge knobs (erosion/blend/coverage/cluster/reach) + panel wiring

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Rework `EditPlanBuilder` — uncapped erosion, protected spine, outward scatter

Remove the temporary erosion cap, add the core-protection clamp that lets erosion carve past the band
without ever holing the spine, broaden the column loop to include the outward `edgeReach` band, and
swap `EdgeBandEngine` for `EdgeScatterEngine`. This activates the three dormant knobs and delivers
rugged, melded edges.

**Files:**
- Modify: `src/main/java/com/lwos/plan/EditPlanBuilder.java` (the `build(...)` method + a `protectCore` helper)
- Delete: `src/main/java/com/lwos/organic/EdgeBandEngine.java`
- Delete: `src/test/java/com/lwos/organic/EdgeBandEngineTest.java`
- Delete: `src/test/java/com/lwos/plan/EditPlanBuilderEdgeBandTest.java`
- Test: `src/test/java/com/lwos/plan/EditPlanBuilderEdgeScatterTest.java` (new, replaces the deleted one)

**Interfaces:**
- Consumes: `EdgeScatterEngine(long, double, double, double, double, Palette)` + `scatterBlockAt(PathMask,int,int)` (Task 1); `PathStyle` getters (Task 2); `PathMask.of(Map)`, `PathMask.edgeDistances()`, `PathMask.edgeDistance(int,int)`; `BlendEngine.keepsPathBlock(PathMask,int,int)`; `GradientEngine.blockAt(int,int,int)`.
- Produces: same `EditPlan build(...)` signatures (unchanged public API).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/lwos/plan/EditPlanBuilderEdgeScatterTest.java`:

```java
package com.lwos.plan;

import com.lwos.config.PathStyle;
import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditPlanBuilderEdgeScatterTest {

    /** Flat world at y=64 everywhere. */
    private static WorldView flat() {
        return (x, z) -> 64;
    }

    private static final List<Vec3d> STRAIGHT = List.of(new Vec3d(0, 64, 0), new Vec3d(20, 64, 0));

    /** Builds a style from defaults with individual edge knobs overridden. */
    private static PathStyle style(double erosion, double blend, double coverage,
                                   double cluster, double reach, double coreProtect) {
        PathStyle d = PathStyle.defaults();
        return new PathStyle(d.core(), d.edge(), erosion, d.edgeFeatureSize(), coreProtect,
                blend, coverage, cluster, reach, d.defaultClusterSize());
    }

    private static boolean isEdgeMaterial(String id) {
        return id.equals("minecraft:coarse_dirt") || id.equals("minecraft:moss_block");
    }

    @Test
    void spineIsNeverErodedIntoAHole() {
        // Erosion far larger than the half-width (width 4 -> halfWidth 2); the centre line must survive.
        EditPlan plan = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 4.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(6.0, 2.0, 0.5, 4.0, 2.0, 0.4));
        for (int x = 3; x <= 17; x++) {
            GridPos centre = new GridPos(x, 64, 0);
            assertTrue(plan.changes().containsKey(centre),
                    "protected spine column (" + x + ",0) must always be placed");
        }
    }

    @Test
    void erosionReachesBeyondTheOldBandCap() {
        // Old model capped erosion at (1-coreProtect)*halfWidth = 0.6*2 = 1.2 blocks. With erosion 6 the
        // footprint must extend measurably wider than with no erosion.
        int wideNoErosion = maxAbsZ(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 4.0,
                flat(), TerrainMode.FOLLOW_SURFACE, style(0.0, 0.0, 0.0, 4.0, 0.0, 0.4)));
        int wideEroded = maxAbsZ(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 4.0,
                flat(), TerrainMode.FOLLOW_SURFACE, style(6.0, 0.0, 0.0, 4.0, 0.0, 0.4)));
        assertTrue(wideEroded > wideNoErosion + 1,
                "erosion 6 must bulge past the old ~1-block cap (" + wideEroded + " vs " + wideNoErosion + ")");
    }

    @Test
    void coverageZeroScattersNoEdgeBlocks() {
        EditPlan plan = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(1.5, 2.0, 0.0, 4.0, 2.0, 0.4));
        long edge = plan.changes().values().stream().filter(c -> isEdgeMaterial(c.state().id())).count();
        assertEquals(0, edge, "coverage 0 must place no edge-shoulder blocks");
    }

    @Test
    void coverageIsMonotonic() {
        long low = countEdge(0.3);
        long high = countEdge(1.0);
        assertEquals(0, countEdge(0.0), "coverage 0 -> zero edge blocks");
        assertTrue(high >= low && high > 0,
                "more coverage must never scatter fewer edge blocks (" + high + " >= " + low + ")");
    }

    @Test
    void clusterSizeChangesTheScatterPattern() {
        var fine = edgePositions(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(1.5, 2.0, 0.6, 2.0, 2.0, 0.4)));
        var broad = edgePositions(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(1.5, 2.0, 0.6, 12.0, 2.0, 0.4)));
        assertNotEquals(fine, broad, "edge cluster size must change which columns scatter");
    }

    @Test
    void reachScattersOntoTerrainOutsideTheRim() {
        // No erosion so the only source of outside columns is edgeReach.
        int rimNoReach = maxAbsZ(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(0.0, 2.0, 1.0, 4.0, 0.0, 0.4)));
        int rimWithReach = maxAbsZ(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(0.0, 2.0, 1.0, 4.0, 4.0, 0.4)));
        assertTrue(rimWithReach > rimNoReach,
                "edgeReach must place blocks further out than the bare rim (" + rimWithReach + " vs " + rimNoReach + ")");
    }

    @Test
    void neutralStyleProducesNoEdgeShoulder() {
        EditPlan plan = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, PathStyle.neutral());
        boolean allDirtPath = plan.changes().values().stream()
                .allMatch(c -> c.state().id().equals("minecraft:dirt_path"));
        assertTrue(allDirtPath, "neutral must be a uniform dirt_path footprint (no shoulder)");
    }

    @Test
    void sameStyleAndPointsIsDeterministic() {
        EditPlan a = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(),
                TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());
        EditPlan b = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(),
                TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());
        assertEquals(a.changes(), b.changes(), "preview==apply: identical inputs must yield an identical plan");
    }

    // --- helpers -------------------------------------------------------------------------------

    private long countEdge(double coverage) {
        EditPlan plan = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(1.5, 2.0, coverage, 4.0, 2.0, 0.4));
        return plan.changes().values().stream().filter(c -> isEdgeMaterial(c.state().id())).count();
    }

    private static java.util.Set<GridPos> edgePositions(EditPlan plan) {
        java.util.Set<GridPos> out = new java.util.HashSet<>();
        plan.changes().forEach((pos, c) -> { if (isEdgeMaterial(c.state().id())) out.add(pos); });
        return out;
    }

    private static int maxAbsZ(EditPlan plan) {
        int max = 0;
        for (GridPos pos : plan.changes().keySet()) max = Math.max(max, Math.abs(pos.z()));
        return max;
    }
}
```

Note: the `GridPos` accessor is `z()` and construction is `new GridPos(x, y, z)` — confirm against `src/main/java/com/lwos/plan/GridPos.java` before running; adjust the helper if the record component differs.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.lwos.plan.EditPlanBuilderEdgeScatterTest"`
Expected: FAIL — `spineIsNeverErodedIntoAHole` and/or `erosionReachesBeyondTheOldBandCap` fail because the temporary Task 2 cap still limits erosion and there is no core-protection clamp yet.

- [ ] **Step 3: Rewrite the `build(...)` method and add `protectCore`**

In `src/main/java/com/lwos/plan/EditPlanBuilder.java`:

1. Update the imports: replace `import com.lwos.organic.EdgeBandEngine;` with `import com.lwos.organic.EdgeScatterEngine;` and add `import java.util.HashMap;` (keep the existing `Map`, `ColumnPos`, etc.).

2. Replace the whole body of the full `build(List<Vec3d>, double, double, WorldView, TerrainMode, PathStyle)` method (from `List<PathSample> raw = ...` through `return new EditPlan(changes);`) with:

```java
        List<PathSample> raw = PathSampler.sampleWithWidth(controlPoints, spacing, width);
        // The footprint is derived from the terrain-snapped samples in every mode, so switching modes
        // changes only the Y handling, never the plan's horizontal extent.
        List<PathSample> grounded = TerrainSampler.snapToSurface(raw, view, 0.0);

        // Resolve the style knobs to absolute-block amplitudes. Pure function of (width, style): the
        // client preview and the server apply compute identical values, so preview==apply holds.
        double halfWidth = width / 2.0;
        double coreRadius = style.coreProtect() * halfWidth;          // protected spine radius
        double edgeBandWidth = Math.max(0.0, halfWidth - coreRadius); // signed-distance depth of the core disc
        double erosionAmp = style.edgeErosion();                      // absolute blocks, NOT capped by the band
        double blendDepth = style.blendDepth();                       // inward feather skirt, blocks
        double edgeReach = style.edgeReach();                         // outward scatter band, blocks
        double edgeScale = 1.0 / Math.max(1e-3, style.edgeFeatureSize());

        // Track columns out to the max outward wobble AND the scatter reach, so ragged edges can bulge
        // outward and edge blocks can meld onto surrounding terrain.
        double halo = Math.ceil(erosionAmp + edgeReach) + 1.0;
        PathMask original = PathMask.build(grounded, halo);

        long seed = operationSeed(controlPoints);
        long edgeSeed = seed ^ EDGE_SALT;
        long gradSeed = seed ^ GRAD_SALT;
        long blendSeed = seed ^ BLEND_SALT;
        long bandSeed = seed ^ BAND_SALT;

        // Stage 1 (edge wobble): shape a copy; keep `original` for the protected-core test.
        PathMask wobbled = new EdgeShaper(edgeScale, erosionAmp,
                EDGE_OCTAVES, EDGE_PERSISTENCE, EDGE_LACUNARITY).shape(original, edgeSeed);
        // Core protection: force every column inside the protected core disc to stay solidly inside the
        // shaped field, so erosion deeper than the band can never punch a hole in the spine. Because the
        // guarantee is enforced here (not by capping the amplitude), edgeErosion is free to carve bays
        // far wider than the movable band.
        PathMask shaped = protectCore(wobbled, original, edgeBandWidth);

        // Stage 2 (material gradient): per-column clustered core-block choice.
        GradientEngine gradient = new GradientEngine(gradSeed, style.toCorePalette());
        // Stage 3 (feather blend): near-edge inside columns may be dropped back to terrain.
        BlendEngine blend = blendDepth > 0 ? new BlendEngine(blendSeed, blendDepth) : null;
        // Stage 4 (edge scatter): sparse, clustered edge-palette blocks across the inner feather band and
        // the outward reach. Active only with an edge palette and non-zero coverage.
        EdgeScatterEngine scatter = (style.toEdgePalette().isPresent() && style.edgeCoverage() > 0)
                ? new EdgeScatterEngine(bandSeed, blendDepth, edgeReach, style.edgeCoverage(),
                        style.edgeClusterSize(), style.toEdgePalette().get())
                : null;

        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (ColumnPos c : sortedColumns(shaped.edgeDistances().keySet())) {
            double d = shaped.edgeDistance(c.x(), c.z());

            if (mode == TerrainMode.CUT_AND_FILL) {
                if (d > 0.0) continue; // cut/fill keeps its carved footprint; no outward scatter
                int surfaceY = view.surfaceHeight(c.x(), c.z());
                int pathY = targetPathY(c, raw);
                BlockStateRef pathBlock = gradient.blockAt(c.x(), pathY, c.z());
                emitCutAndFill(changes, c, surfaceY, pathY, pathBlock);
                continue;
            }

            // FOLLOW_SURFACE: columns beyond the outward scatter band are never touched.
            if (d > edgeReach) continue;
            int surfaceY = view.surfaceHeight(c.x(), c.z());
            // Protected core column (by ORIGINAL distance): always a path block. Otherwise an inside
            // column is a path block unless the feather drops it.
            boolean inCore = original.edgeDistance(c.x(), c.z()) <= -edgeBandWidth;
            boolean insidePath = inCore
                    || (d <= 0.0 && (blend == null || blend.keepsPathBlock(shaped, c.x(), c.z())));

            if (insidePath) {
                BlockStateRef block = gradient.blockAt(c.x(), surfaceY, c.z());
                GridPos pos = new GridPos(c.x(), surfaceY, c.z());
                changes.put(pos, new PlannedChange(pos, ChangeKind.TERRAIN, block));
            } else if (scatter != null) {
                // Dropped inside column or an outside column within reach: maybe a scattered edge block.
                var edge = scatter.scatterBlockAt(shaped, c.x(), c.z());
                if (edge.isPresent()) {
                    GridPos pos = new GridPos(c.x(), surfaceY, c.z());
                    changes.put(pos, new PlannedChange(pos, ChangeKind.TERRAIN, edge.get()));
                }
            }
        }
        return new EditPlan(changes);
```

3. Change the `sortedColumns` parameter type if needed — it already takes `Set<ColumnPos>` and `shaped.edgeDistances().keySet()` is a `Set<ColumnPos>`, so no change. Add the helper method after `sortedColumns`:

```java
    /**
     * Returns a copy of {@code shaped}'s distance field with every column inside the original protected
     * core ({@code original.edgeDistance <= -edgeBandWidth}) forced to remain solidly inside
     * ({@code <= -1e-3}). This makes the spine's solidity independent of the erosion amplitude — the
     * core can never be eroded into a hole, however deep {@code edgeErosion} bites elsewhere.
     */
    private static PathMask protectCore(PathMask shaped, PathMask original, double edgeBandWidth) {
        Map<ColumnPos, Double> out = new HashMap<>(shaped.edgeDistances());
        for (Map.Entry<ColumnPos, Double> e : original.edgeDistances().entrySet()) {
            if (e.getValue() <= -edgeBandWidth) {
                ColumnPos c = e.getKey();
                double current = out.getOrDefault(c, Double.POSITIVE_INFINITY);
                out.put(c, Math.min(current, -1.0e-3));
            }
        }
        return PathMask.of(out);
    }
```

- [ ] **Step 4: Delete the superseded engine and its tests**

```bash
git rm src/main/java/com/lwos/organic/EdgeBandEngine.java src/test/java/com/lwos/organic/EdgeBandEngineTest.java src/test/java/com/lwos/plan/EditPlanBuilderEdgeBandTest.java
```

- [ ] **Step 5: Run the new test to verify it passes**

Run: `./gradlew test --tests "com.lwos.plan.EditPlanBuilderEdgeScatterTest"`
Expected: PASS (8 tests).

- [ ] **Step 6: Run the full suite and build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`; no reference to the deleted `EdgeBandEngine` remains (grep `EdgeBandEngine` returns nothing under `src/`).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(organic): uncapped rugged erosion, protected spine, outward edge scatter

Decouple edge erosion from path width with a hard core-protection clamp, broaden
the column loop to the outward edgeReach band, and drive edge blocks from
EdgeScatterEngine (coverage + cluster). Removes EdgeBandEngine.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: In-game verification & push

Confirm the new controls behave in a live client and that preview matches commit, then push.

**Files:** none (manual verification).

- [ ] **Step 1: Launch the client**

Run: `./gradlew runClient`
Expected: the client reaches the main menu; load a creative superflat world.

- [ ] **Step 2: Verify the panel and controls**

- Toggle builder mode, select the Path tool, open the style panel; hold Ctrl to edit.
- Confirm the sliders render: **Blend depth, Edge coverage, Edge cluster, Edge reach** (Outskirts) and **Edge erosion, Edge feature size, Core protect, Core cluster size** (Advanced).
- Place a width-3 path. Set **Edge erosion** high and **Edge feature size** low → the preview edge becomes visibly rugged/bitten while the centre stays solid.
- Sweep **Edge coverage** 0 → 1 → edge blocks go from none to a dense shoulder; **Edge cluster** small → large visibly changes clump size; **Edge reach** > 0 scatters edge blocks onto the surrounding ground.

- [ ] **Step 3: Verify preview ≡ apply**

Commit the path (the commit key). Confirm the placed blocks match the preview exactly — same rugged outline, same scattered edge blocks, no shift.

- [ ] **Step 4: Push**

Per the project's standing instruction (build + tests green, work verified), push the completed work:

```bash
git push origin main
```

---

## Self-Review

**Spec coverage:**
- ① Shape — `edgeErosion` (uncapped, Task 2 field + Task 3 decoupling), `edgeFeatureSize` (kept), `coreProtect` as a true floor (Task 3 `protectCore`). ✔
- ② Scatter — `edgeCoverage` + `edgeClusterSize` (Task 1 engine, Task 2 fields/UI, Task 3 wiring). ✔ Fixes the dead cluster knob (Task 1 `clusterScale`, Task 3 test `clusterSizeChangesTheScatterPattern`). ✔
- ③ Blend — `blendDepth` absolute (Task 2), `edgeReach` outward (Task 1 falloff, Task 3 loop + halo). ✔
- Determinism / preview≡apply / MP — no wire change; new fields on `PathStyle` only; seed still from control points (Task 2 JSON, Task 3 determinism test, Task 4 in-game check). ✔
- Testing list from spec §6 — core-protection invariant, erosion depth, coverage monotonicity, cluster visibility, reach, determinism, neutral — all present in `EditPlanBuilderEdgeScatterTest` + `EdgeScatterEngineTest`. ✔
- Out-of-scope items (variable width, on-top edge blocks, jaggedness dial, decoration, node editing, cut/fill outward scatter) — untouched; `CUT_AND_FILL` explicitly skips outward scatter (Task 3 `if (d > 0.0) continue;`). ✔

**Placeholder scan:** No TBD/TODO. The one "TEMP" is a deliberate, labelled transitional line in Task 2 that Task 3 removes; both the introduction and removal are shown in full. The `setEdgeErosion` first-draft is explicitly superseded by the simpler version in the same step.

**Type consistency:** `PathStyle` constructor arg order `(core, edge, edgeErosion, edgeFeatureSize, coreProtect, blendDepth, edgeCoverage, edgeClusterSize, edgeReach, defaultClusterSize)` is identical in every call site (factories, `PathStyleEdits`, both test helpers). `EdgeScatterEngine(seed, blendDepth, edgeReach, coverage, clusterSize, edgePalette)` and `scatterBlockAt(mask, x, z)` match between Task 1 definition and Task 3 use. Slider `target` strings (`blend`, `coverage`, `edgecluster`, `reach`, `cluster`, `erosion`, `feature`, `core`, `coreWeight`, `edgeWeight`) match between `PathStylePanel` emission and `PathStylePanelInput` dispatch. Getter names (`edgeErosion`, `blendDepth`, `edgeCoverage`, `edgeClusterSize`, `edgeReach`, `coreProtect`, `edgeFeatureSize`, `defaultClusterSize`) are consistent across config, UI, and tests.
