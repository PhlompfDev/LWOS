package com.lwos.organic;

import com.lwos.organic.noise.CellularNoise;
import com.lwos.organic.noise.PerlinNoise;
import com.lwos.plan.BlockStateRef;

/**
 * Assigns a {@link BlockStateRef} per column from a {@link Palette}, replacing uniform
 * single-block paths with clustered, weighted material runs (M5 plan, Task 3).
 *
 * <p>Each {@link Palette.Entry} gets its own independent noise fields, derived from the base
 * seed plus the entry's index, so different entries don't accidentally share a pattern:
 * <ul>
 *   <li>A {@link CellularNoise} field sampled at {@code (x/clusterSize, z/clusterSize)} gives
 *       a value constant across a whole Worley cell -- the source of contiguous patches.</li>
 *   <li>A {@link PerlinNoise} field sampled at {@code (x*noiseScale, z*noiseScale)} jitters
 *       that base value, breaking up the crisp Voronoi-style cluster boundary into an
 *       irregular one.</li>
 * </ul>
 * The per-entry score is {@code weight * (cellValue + jitterWeight * perlinJitter)}; the entry
 * with the highest score at a column wins ({@code argmax}), so a higher {@code weight} makes an
 * entry win a larger total share of the area.
 *
 * <p>Pure and deterministic: the same seed + palette always produce byte-identical assignments
 * for the same coordinates. No wall-clock reads, no unseeded {@link java.util.Random}.
 */
public final class GradientEngine {

    /** Amplitude of the Perlin jitter relative to the [0,1) cellValue base score. */
    private static final double JITTER_WEIGHT = 0.5;
    /** Distinct salt so an entry's Perlin sub-seed never collides with its Cellular sub-seed. */
    private static final long PERLIN_SALT = 0x9E3779B97F4A7C15L;

    private final Palette palette;
    private final CellularNoise[] cellularFields;
    private final PerlinNoise[] perlinFields;

    public GradientEngine(long seed, Palette palette) {
        this.palette = palette;
        int n = palette.entries().size();
        this.cellularFields = new CellularNoise[n];
        this.perlinFields = new PerlinNoise[n];
        for (int i = 0; i < n; i++) {
            long subSeed = seed + i;
            cellularFields[i] = new CellularNoise(subSeed);
            perlinFields[i] = new PerlinNoise(subSeed ^ PERLIN_SALT);
        }
    }

    /**
     * Returns the block assigned to {@code (x, z)} by argmax of the palette's weighted scores.
     *
     * @param y kept to preserve the natural 3D call signature for surface-following callers
     *          (e.g. terrain replacement walking a heightmap); this palette scores purely on
     *          the (x, z) column, so {@code y} is currently unused. Documented per the brief's
     *          "keep the 3D signature ... document if unused" guidance.
     */
    public BlockStateRef blockAt(int x, int y, int z) {
        var entries = palette.entries();
        double bestScore = Double.NEGATIVE_INFINITY;
        BlockStateRef best = null;

        for (int i = 0; i < entries.size(); i++) {
            Palette.Entry entry = entries.get(i);
            double cellX = x / entry.clusterSize();
            double cellZ = z / entry.clusterSize();
            double base = cellularFields[i].cellValue(cellX, cellZ);

            double jitter = perlinFields[i].noise(x * entry.noiseScale(), z * entry.noiseScale());
            double score = entry.weight() * (base + JITTER_WEIGHT * jitter);

            if (score > bestScore) {
                bestScore = score;
                best = entry.block();
            }
        }
        return best;
    }
}
