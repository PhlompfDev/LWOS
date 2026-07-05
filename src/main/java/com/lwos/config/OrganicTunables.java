package com.lwos.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lwos.organic.Palette;
import com.lwos.plan.BlockStateRef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of the M5 organic-generation tunables (edge wobble, material palette,
 * feather skirt) consumed by {@link com.lwos.plan.EditPlanBuilder}. Kept Minecraft-free and
 * headless-testable: it uses only {@code java.nio} + Gson, never a MC type, so both the client
 * preview and the server apply path can read the same shared config in the singleplayer /
 * integrated-server dev workflow.
 *
 * <p><b>Purity boundary:</b> {@link com.lwos.plan.EditPlanBuilder#build} takes a snapshot as a
 * parameter and never reads {@link #current()} or touches the filesystem itself — so the builder
 * stays pure and deterministic. All file IO / mutable global state lives here, in the
 * {@link #current()}/{@link #reload()} holder.
 *
 * <p><b>Multiplayer limitation (out of scope):</b> the tunables holder is a plain JVM-local
 * static. In true multiplayer the client and a remote dedicated server would each read their own
 * {@code config/lwos-organic.json}; there is no sync packet. Determinism (preview==apply) is
 * therefore only guaranteed in singleplayer / an integrated server, which is the M5 Definition of
 * Done (a builder tweaking JSON in-game). Syncing tunables across a network is future work.
 */
public final class OrganicTunables {

    /** Default on-disk location, relative to the game working directory. */
    private static final Path CONFIG_PATH = Path.of("config", "lwos-organic.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final double edgeErosionFactor;
    private final double edgeNoiseScale;
    private final int blendSkirtWidth;
    private final double defaultClusterSize;
    private final List<PaletteEntry> palette;

    private OrganicTunables(double edgeErosionFactor, double edgeNoiseScale, int blendSkirtWidth,
                            double defaultClusterSize, List<PaletteEntry> palette) {
        this.edgeErosionFactor = edgeErosionFactor;
        this.edgeNoiseScale = edgeNoiseScale;
        this.blendSkirtWidth = blendSkirtWidth;
        this.defaultClusterSize = defaultClusterSize;
        this.palette = List.copyOf(palette);
    }

    /** EdgeShaper amplitude, in blocks. 0 => identity (edge unchanged). */
    public double edgeErosionFactor() { return edgeErosionFactor; }

    /** EdgeShaper coordinate scale (smaller = lower frequency, broader waves). */
    public double edgeNoiseScale() { return edgeNoiseScale; }

    /** BlendEngine feather skirt width, in blocks. 0 => keep all inside columns (no feathering). */
    public int blendSkirtWidth() { return blendSkirtWidth; }

    /** Default cluster size applied to palette entries that don't override it. */
    public double defaultClusterSize() { return defaultClusterSize; }

    /** The immutable palette entries, in order. */
    public List<PaletteEntry> palette() { return palette; }

    /** Builds the organic {@link Palette} by mapping each entry's id through {@link BlockStateRef}. */
    public Palette toPalette() {
        List<Palette.Entry> entries = new ArrayList<>(palette.size());
        for (PaletteEntry e : palette) {
            entries.add(new Palette.Entry(new BlockStateRef(e.id()), e.weight(), e.noiseScale(), e.clusterSize()));
        }
        return new Palette(entries);
    }

    // ---- Factories -------------------------------------------------------------------------

    /**
     * The real organic look: a multi-material palette with a wobbled, feathered edge. This is
     * what the two live call sites (preview + apply) use when no override is supplied.
     */
    public static OrganicTunables defaults() {
        double cluster = 5.0;
        List<PaletteEntry> palette = List.of(
                new PaletteEntry("minecraft:dirt_path", 3.0, 0.1, cluster),
                new PaletteEntry("minecraft:coarse_dirt", 1.0, 0.1, cluster),
                new PaletteEntry("minecraft:gravel", 0.6, 0.1, cluster));
        return new OrganicTunables(1.5, 0.08, 2, cluster, palette);
    }

    /**
     * A true identity: no edge erosion, no feathering, and a single dirt_path entry. Reproduces
     * the exact pre-M5 geometric footprint and block choice, so the geometric-pipeline tests can
     * assert on it directly.
     */
    public static OrganicTunables neutral() {
        List<PaletteEntry> palette = List.of(new PaletteEntry("minecraft:dirt_path", 1.0, 0.1, 5.0));
        return new OrganicTunables(0.0, 0.08, 0, 5.0, palette);
    }

    // ---- JSON load + reloadable holder -----------------------------------------------------

    /**
     * Parses a snapshot from a JSON string. Any missing scalar falls back to the {@link #defaults()}
     * value; a missing/empty palette falls back to the default palette. Pure — no IO.
     */
    public static OrganicTunables fromJson(String json) {
        RawConfig raw = GSON.fromJson(json, RawConfig.class);
        if (raw == null) return defaults();
        OrganicTunables d = defaults();

        double erosion = raw.edgeErosionFactor != null ? raw.edgeErosionFactor : d.edgeErosionFactor;
        double noiseScale = raw.edgeNoiseScale != null ? raw.edgeNoiseScale : d.edgeNoiseScale;
        int skirt = raw.blendSkirtWidth != null ? raw.blendSkirtWidth : d.blendSkirtWidth;
        double cluster = raw.defaultClusterSize != null ? raw.defaultClusterSize : d.defaultClusterSize;

        List<PaletteEntry> palette = new ArrayList<>();
        if (raw.palette != null) {
            for (RawEntry e : raw.palette) {
                if (e == null || e.id == null) continue;
                double weight = e.weight != null ? e.weight : 1.0;
                double entryNoise = e.noiseScale != null ? e.noiseScale : 0.1;
                double entryCluster = e.clusterSize != null ? e.clusterSize : cluster;
                palette.add(new PaletteEntry(e.id, weight, entryNoise, entryCluster));
            }
        }
        if (palette.isEmpty()) palette = d.palette;

        OrganicTunables snapshot = new OrganicTunables(erosion, noiseScale, skirt, cluster, palette);
        // Force eager validation (weight > 0, clusterSize > 0 -- see Palette.Entry's constructor) so a
        // bad edit is rejected HERE, at reload time, rather than later on the render/apply thread where
        // toPalette() is otherwise first invoked -- once per frame, by EditPlanBuilder.build.
        snapshot.toPalette();
        return snapshot;
    }

    private static volatile OrganicTunables current = defaults();

    /** The currently active snapshot. Read every frame by the preview; safe to call from anywhere. */
    public static OrganicTunables current() {
        return current;
    }

    /**
     * Re-reads {@code config/lwos-organic.json} into {@link #current()}. If the file is absent it
     * is written with {@link #defaults()} first (so a builder has a template to edit). On any IO or
     * parse error the previous snapshot is retained and the error is reported to stderr — a bad edit
     * never crashes the game. Called from the client input layer (a keybind), never from the pure
     * builder.
     */
    public static synchronized void reload() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                Files.createDirectories(CONFIG_PATH.getParent());
                Files.writeString(CONFIG_PATH, GSON.toJson(defaults().toRaw()), StandardCharsets.UTF_8);
                current = defaults();
                return;
            }
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            current = fromJson(json);
        } catch (IOException | RuntimeException e) {
            System.err.println("[LWOS] Failed to reload organic tunables, keeping previous values: " + e);
        }
    }

    private RawConfig toRaw() {
        RawConfig raw = new RawConfig();
        raw.edgeErosionFactor = edgeErosionFactor;
        raw.edgeNoiseScale = edgeNoiseScale;
        raw.blendSkirtWidth = blendSkirtWidth;
        raw.defaultClusterSize = defaultClusterSize;
        raw.palette = new ArrayList<>();
        for (PaletteEntry e : palette) {
            RawEntry r = new RawEntry();
            r.id = e.id();
            r.weight = e.weight();
            r.noiseScale = e.noiseScale();
            r.clusterSize = e.clusterSize();
            raw.palette.add(r);
        }
        return raw;
    }

    /** One palette entry as plain tunable data (id + weighting/noise/cluster knobs). */
    public record PaletteEntry(String id, double weight, double noiseScale, double clusterSize) { }

    // Gson DTOs: boxed types so "field absent in JSON" is distinguishable (null) from "0".
    private static final class RawConfig {
        Double edgeErosionFactor;
        Double edgeNoiseScale;
        Integer blendSkirtWidth;
        Double defaultClusterSize;
        List<RawEntry> palette;
    }

    private static final class RawEntry {
        String id;
        Double weight;
        Double noiseScale;
        Double clusterSize;
    }
}
