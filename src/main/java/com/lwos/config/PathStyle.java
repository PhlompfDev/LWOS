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
 * <p><b>Width-relative knobs:</b> {@code edgeRoughness}, {@code featherDepth} and {@code coreProtect}
 * are normalized fractions (0..1), resolved against the path's half-width in
 * {@link com.lwos.plan.EditPlanBuilder}. This keeps a solid, protected spine at every width — a
 * narrow path can't erode/feather its whole footprint away — and lets one style read well from a
 * width-1 trail to a wide road. {@code edgeFeatureSize} is an absolute size in blocks.
 */
public final class PathStyle {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One weighted, clustered material choice (mirrors {@link Palette.Entry} as pure config data). */
    public record Entry(String id, double weight, double noiseScale, double clusterSize) { }

    private final List<Entry> core;
    private final List<Entry> edge;
    private final double edgeRoughness;
    private final double edgeFeatureSize;
    private final double featherDepth;
    private final double coreProtect;
    private final double defaultClusterSize;

    public PathStyle(List<Entry> core, List<Entry> edge, double edgeRoughness, double edgeFeatureSize,
                     double featherDepth, double coreProtect, double defaultClusterSize) {
        if (core == null || core.isEmpty()) {
            throw new IllegalArgumentException("core palette must have at least one entry");
        }
        this.core = List.copyOf(core);
        this.edge = edge == null ? List.of() : List.copyOf(edge);
        this.edgeRoughness = clamp01(edgeRoughness);
        this.edgeFeatureSize = Math.max(1e-3, edgeFeatureSize);
        this.featherDepth = clamp01(featherDepth);
        this.coreProtect = clamp01(coreProtect);
        this.defaultClusterSize = defaultClusterSize;
        // Eager validation: build the palettes now so a bad entry (weight<=0, clusterSize<=0) is
        // rejected at construction, not later on the render/apply thread.
        toCorePalette();
        toEdgePalette();
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    public List<Entry> core() { return core; }
    public List<Entry> edge() { return edge; }

    /** Ragged-edge amplitude, 0..1, as a fraction of the movable edge band. 0 = clean, 1 = max ragged. */
    public double edgeRoughness() { return edgeRoughness; }

    /** Raggedness feature size, in blocks (larger = broader bays, smaller = fine crenellation). */
    public double edgeFeatureSize() { return edgeFeatureSize; }

    /** Feather skirt depth, 0..1, as a fraction of the movable edge band. 0 = hard edge. */
    public double featherDepth() { return featherDepth; }

    /** Inner fraction of the half-width (0..1) never eroded or feathered — the guaranteed solid spine. */
    public double coreProtect() { return coreProtect; }

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

    /** The real organic look: multi-material core + coarse-dirt/moss edge shoulder, wobbled + feathered. */
    public static PathStyle defaults() {
        double cluster = 5.0;
        List<Entry> core = List.of(
                new Entry("minecraft:dirt_path", 3.0, 0.1, cluster),
                new Entry("minecraft:coarse_dirt", 1.0, 0.1, cluster),
                new Entry("minecraft:gravel", 0.6, 0.1, cluster));
        List<Entry> edge = List.of(
                new Entry("minecraft:coarse_dirt", 1.0, 0.1, cluster),
                new Entry("minecraft:moss_block", 0.6, 0.1, cluster));
        return new PathStyle(core, edge, 0.5, 5.0, 0.5, 0.4, cluster);
    }

    /** True identity: no erosion, no feather, fully protected core, single dirt_path, no edge shoulder. */
    public static PathStyle neutral() {
        return new PathStyle(List.of(new Entry("minecraft:dirt_path", 1.0, 0.1, 5.0)),
                List.of(), 0.0, 5.0, 0.0, 1.0, 5.0);
    }

    // ---- JSON --------------------------------------------------------------------------------

    public String toJson() {
        Dto dto = new Dto();
        dto.core = toRaw(core);
        dto.edge = toRaw(edge);
        dto.edgeRoughness = edgeRoughness;
        dto.edgeFeatureSize = edgeFeatureSize;
        dto.featherDepth = featherDepth;
        dto.coreProtect = coreProtect;
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
     * Parses a style from JSON. Missing scalars fall back to {@link #defaults()}; a missing/empty
     * core palette falls back to the default core. Pure — no IO. Throws {@link IllegalArgumentException}
     * on an invalid entry (weight/clusterSize <= 0) via the constructor's eager validation.
     */
    public static PathStyle fromJson(String json) {
        Dto raw = GSON.fromJson(json, Dto.class);
        if (raw == null) return defaults();
        PathStyle d = defaults();
        double roughness = raw.edgeRoughness != null ? raw.edgeRoughness : d.edgeRoughness;
        double featureSize = raw.edgeFeatureSize != null ? raw.edgeFeatureSize : d.edgeFeatureSize;
        double feather = raw.featherDepth != null ? raw.featherDepth : d.featherDepth;
        double core0 = raw.coreProtect != null ? raw.coreProtect : d.coreProtect;
        double cluster = raw.defaultClusterSize != null ? raw.defaultClusterSize : d.defaultClusterSize;
        List<Entry> core = parseEntries(raw.core, cluster);
        List<Entry> edge = parseEntries(raw.edge, cluster);
        if (core.isEmpty()) core = d.core;
        return new PathStyle(core, edge, roughness, featureSize, feather, core0, cluster);
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
        return Double.compare(p.edgeRoughness, edgeRoughness) == 0
                && Double.compare(p.edgeFeatureSize, edgeFeatureSize) == 0
                && Double.compare(p.featherDepth, featherDepth) == 0
                && Double.compare(p.coreProtect, coreProtect) == 0
                && Double.compare(p.defaultClusterSize, defaultClusterSize) == 0
                && core.equals(p.core) && edge.equals(p.edge);
    }

    @Override public int hashCode() {
        return Objects.hash(core, edge, edgeRoughness, edgeFeatureSize, featherDepth, coreProtect,
                defaultClusterSize);
    }

    // Gson serializes the object graph directly; this Dto mirrors the same field names for parsing
    // with boxed types so "absent" (null) is distinguishable from 0.
    private static final class Dto {
        List<RawEntry> core;
        List<RawEntry> edge;
        Double edgeRoughness;
        Double edgeFeatureSize;
        Double featherDepth;
        Double coreProtect;
        Double defaultClusterSize;
    }
    private static final class RawEntry { String id; Double weight; Double noiseScale; Double clusterSize; }
}
