package com.lwos.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lwos.organic.Palette;
import com.lwos.plan.BlockStateRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable path style: a core material palette, an optional edge-shoulder palette, and the
 * organic edge/blend parameters. Supersedes the flat OrganicTunables. Minecraft-free and
 * headless-testable (java + Gson only), so the client preview and the server apply path read the
 * same shared data. All mutable global state / file IO lives in {@link StyleManager}, keeping
 * {@link com.lwos.plan.EditPlanBuilder} pure.
 *
 * <p><b>Absolute edge knobs:</b> {@code edgeErosion}, {@code blendDepth} and {@code edgeReach} are
 * absolute distances in blocks, not fractions of the path width — a narrow trail and a wide road
 * both erode/feather/scatter by the same physical depth. {@code coreProtect} is still a fraction of
 * the half-width (0..1): the guaranteed solid spine that erosion and feathering never touch, so a
 * path can't be eaten away to nothing regardless of its erosion/blend settings. {@code edgeCoverage}
 * and {@code edgeClusterSize} drive the edge-scatter density and patch size (also absolute, in
 * blocks). {@code edgeFeatureSize} remains an absolute erosion feature size in blocks.
 */
public final class PathStyle {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One weighted, clustered material choice (mirrors {@link Palette.Entry} as pure config data). */
    public record Entry(String id, double weight, double noiseScale, double clusterSize) { }

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

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

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

    public Palette toCorePalette() { return toPalette(core); }

    /** The edge shoulder palette, or empty when no edge materials are configured. */
    public Optional<Palette> toEdgePalette() {
        return edge.isEmpty() ? Optional.empty() : Optional.of(toPalette(edge));
    }

    private static Palette toPalette(List<Entry> entries) {
        List<Palette.Entry> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            out.add(new Palette.Entry(new BlockStateRef(e.id()), e.weight(), e.noiseScale(), e.clusterSize()));
        }
        return new Palette(out);
    }

    // ---- Factories ---------------------------------------------------------------------------

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

    // ---- JSON --------------------------------------------------------------------------------

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

    private static List<RawEntry> toRaw(List<Entry> entries) {
        List<RawEntry> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            RawEntry r = new RawEntry();
            r.id = e.id(); r.weight = e.weight(); r.noiseScale = e.noiseScale(); r.clusterSize = e.clusterSize();
            out.add(r);
        }
        return out;
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

    private static List<Entry> parseEntries(List<RawEntry> raw, double defaultCluster) {
        List<Entry> out = new ArrayList<>();
        if (raw == null) return out;
        for (RawEntry e : raw) {
            if (e == null || e.id == null) continue;
            double weight = e.weight != null ? e.weight : 1.0;
            double noiseScale = e.noiseScale != null ? e.noiseScale : 0.1;
            double clusterSize = e.clusterSize != null ? e.clusterSize : defaultCluster;
            out.add(new Entry(e.id, weight, noiseScale, clusterSize));
        }
        return out;
    }

    // ---- value equality (for cheap "did anything change" checks) ------------------------------

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

    // Gson serializes the object graph directly; this Dto mirrors the same field names for parsing
    // with boxed types so "absent" (null) is distinguishable from 0.
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
    private static final class RawEntry { String id; Double weight; Double noiseScale; Double clusterSize; }
}
