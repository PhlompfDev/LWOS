package com.lwos.organic;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import com.lwos.organic.noise.PerlinNoise;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Softens a {@link PathMask}'s boundary into the surrounding terrain (M5 plan, Task 4): in an
 * outer skirt of {@code N} blocks, it randomly (but deterministically) decides to leave the
 * original terrain block intact instead of placing a path block, so the path feathers out rather
 * than ending on a hard line.
 *
 * <p>The decision is driven by a distance-derived keep-probability compared against a seeded
 * {@link PerlinNoise} sample at that column:
 * <ul>
 *   <li>A column with {@code edgeDistance <= -N} (deep interior) is always kept
 *       (probability 1.0).</li>
 *   <li>As {@code edgeDistance} rises from {@code -N} toward {@code 0} (approaching the edge),
 *       the keep-probability falls smoothly toward 0.</li>
 *   <li>Columns outside the path ({@code edgeDistance > 0}, including the untracked halo) are
 *       never kept -- BlendEngine only feathers the inside of the path.</li>
 * </ul>
 * Comparing a per-column noise sample against that probability (rather than, say, a fixed
 * threshold on distance alone) is what makes the feather edge irregular instead of a clean
 * geometric ring: two columns at the same distance from the edge can resolve differently.
 *
 * <p>Pure and deterministic: the same seed, skirt width, and input mask always produce a
 * byte-identical (here, set-identical) keep/drop decision for every column. No wall-clock reads,
 * no unseeded {@link java.util.Random}.
 */
public final class BlendEngine {

    // Coordinate scale applied before sampling noise for the keep/drop coin. Deliberately higher
    // frequency than EdgeShaper's boundary wobble -- the feather needs per-column irregularity,
    // not broad waves, so nearby columns can resolve differently (see class doc).
    private static final double NOISE_SCALE = 0.35;

    private final int skirtWidth;
    private final PerlinNoise noise;

    /**
     * @param seed       operation seed driving the keep/drop noise field (same seed -> identical output)
     * @param skirtWidth width, in blocks, of the outer band over which keep-probability ramps
     *                   from 1.0 (at {@code edgeDistance <= -skirtWidth}) down to ~0 (at
     *                   {@code edgeDistance = 0}); must be > 0
     */
    public BlendEngine(long seed, int skirtWidth) {
        if (skirtWidth <= 0) {
            throw new IllegalArgumentException("skirtWidth must be > 0, got " + skirtWidth);
        }
        this.skirtWidth = skirtWidth;
        this.noise = new PerlinNoise(seed);
    }

    /**
     * Decides whether the path block at {@code (x, z)} should be kept (placed) or dropped (left
     * as original terrain).
     *
     * @return {@code true} to place the path block, {@code false} to leave the original terrain
     *         block intact. Always {@code false} for columns outside the path
     *         ({@code edgeDistance(x, z) > 0}, including untracked columns).
     */
    public boolean keepsPathBlock(PathMask mask, int x, int z) {
        double d = mask.edgeDistance(x, z);
        if (d > 0.0) return false; // outside the path (or untracked) -- never BlendEngine's to keep

        double keepProbability = keepProbability(d);
        if (keepProbability >= 1.0) return true;
        if (keepProbability <= 0.0) return false;

        // Map the noise sample from [-1, 1] to [0, 1) and compare against the keep-probability.
        double sample = (noise.noise(x * NOISE_SCALE, z * NOISE_SCALE) + 1.0) / 2.0;
        return sample < keepProbability;
    }

    /**
     * Convenience batch form: resolves {@link #keepsPathBlock} for every inside column of
     * {@code mask} and returns the surviving (kept) ones. What Task 5's pipeline consumes to know
     * which columns still get a path block placed.
     */
    public Set<ColumnPos> feather(PathMask mask) {
        Set<ColumnPos> kept = new HashSet<>();
        for (Map.Entry<ColumnPos, Double> entry : mask.edgeDistances().entrySet()) {
            if (entry.getValue() > 0.0) continue; // only inside columns are candidates
            ColumnPos col = entry.getKey();
            if (keepsPathBlock(mask, col.x(), col.z())) {
                kept.add(col);
            }
        }
        return kept;
    }

    /**
     * Distance-derived keep-probability: 1.0 for {@code d <= -skirtWidth} (deep interior),
     * smoothly falling to 0.0 at {@code d = 0} (the edge). Uses a smoothstep-style curve (rather
     * than a linear ramp) so the falloff itself reads as a soft gradient, not a crisp cutoff.
     */
    private double keepProbability(double d) {
        if (d <= -skirtWidth) return 1.0;
        if (d >= 0.0) return 0.0;
        // t: 0 at the edge (d=0), 1 at the inner skirt boundary (d=-skirtWidth).
        double t = -d / skirtWidth;
        return t * t * (3 - 2 * t); // smoothstep
    }
}
