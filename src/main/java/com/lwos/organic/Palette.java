package com.lwos.organic;

import com.lwos.plan.BlockStateRef;

import java.util.List;

/**
 * An ordered, immutable set of weighted block choices for {@link GradientEngine} (M5 plan,
 * Task 3). Each {@link Entry} is the plan's tuple of {@code (block, weight, noiseScale,
 * clusterSize)} -- together they let a path read as clustered runs of different materials
 * (e.g. coarse dirt next to path block) instead of one uniform block.
 *
 * <p>Pure data: no Minecraft imports, only the pure {@link BlockStateRef} carrier.
 */
public final class Palette {

    private final List<Entry> entries;

    /** Defensive-copies {@code entries} so the Palette is immutable once built. */
    public Palette(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Palette must have at least one entry");
        }
        this.entries = List.copyOf(entries);
    }

    /** The palette's entries, in order. Immutable. */
    public List<Entry> entries() {
        return entries;
    }

    /**
     * One weighted, clustered block choice in a {@link Palette}.
     *
     * <p>Each field plays a distinct role in {@link GradientEngine}'s scoring:
     * <ul>
     *   <li>{@code weight} -- linear bias on the entry's score; a higher weight makes this
     *       entry win a larger total share of area (argmax across entries).</li>
     *   <li>{@code clusterSize} -- patch granularity: sets the cell size of a Cellular
     *       (Worley) noise field sampled as {@code cellValue(x/clusterSize, z/clusterSize)},
     *       which is constant across roughly {@code clusterSize} blocks -- this is what makes
     *       assignments form contiguous patches rather than per-block static.</li>
     *   <li>{@code noiseScale} -- boundary jitter: the frequency of an independent Perlin
     *       field that nudges the score up/down, breaking up otherwise-crisp Voronoi-style
     *       cluster edges into irregular ones.</li>
     * </ul>
     *
     * @param block       the block this entry contributes
     * @param weight      linear bias on this entry's score (higher -> more total area)
     * @param noiseScale  frequency of the Perlin jitter field applied to this entry's score
     * @param clusterSize approximate patch size (in blocks) of this entry's Cellular field
     */
    public record Entry(BlockStateRef block, double weight, double noiseScale, double clusterSize) {

        public Entry {
            if (block == null) throw new IllegalArgumentException("block must not be null");
            if (weight <= 0) throw new IllegalArgumentException("weight must be positive");
            if (clusterSize <= 0) throw new IllegalArgumentException("clusterSize must be positive");
        }
    }
}
