package com.lwos.organic;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import com.lwos.organic.noise.PerlinNoise;

import java.util.HashMap;
import java.util.Map;

/**
 * Wobbles a {@link PathMask}'s boundary using low-frequency Perlin noise (M5 plan, Task 2), so a
 * geometrically perfect disc-union path reads as an organic, hand-carved one instead.
 *
 * <p>For every column already tracked by the input mask, the signed edge distance is offset by
 * a noise sample: {@code d' = d + amplitude * perlin.fractal(x*scale, z*scale, ...)}. A small
 * {@code scale} (~0.05-0.15 blocks^-1) keeps the noise field low-frequency, so the boundary bends
 * in broad, smooth waves rather than per-block static. Columns are never added or removed — only
 * the distance value changes — so outward bulges are naturally capped by however far the input's
 * tracked halo already reaches (acceptable per spec; produces clearly visible jaggedness without
 * needing to grow the tracked set).
 *
 * <p>Pure and deterministic: the same seed and input always produce byte-identical output. No
 * wall-clock reads, no unseeded {@link java.util.Random}.
 */
public final class EdgeShaper {

    /** Coordinate scale applied before sampling noise; keeps the field low-frequency (broad waves). */
    private static final double DEFAULT_SCALE = 0.08;
    /** Offset amplitude in blocks applied to the signed distance field. */
    private static final double DEFAULT_AMPLITUDE = 3.0;
    private static final int DEFAULT_OCTAVES = 3;
    private static final double DEFAULT_PERSISTENCE = 0.5;
    private static final double DEFAULT_LACUNARITY = 2.0;

    private final double scale;
    private final double amplitude;
    private final int octaves;
    private final double persistence;
    private final double lacunarity;

    /** Creates a shaper using the default low-frequency scale and amplitude. */
    public EdgeShaper() {
        this(DEFAULT_SCALE, DEFAULT_AMPLITUDE, DEFAULT_OCTAVES, DEFAULT_PERSISTENCE, DEFAULT_LACUNARITY);
    }

    /**
     * Creates a shaper with explicit noise parameters.
     *
     * @param scale       coordinate multiplier before sampling noise (smaller = lower frequency)
     * @param amplitude   offset magnitude, in blocks, applied to the signed distance field
     * @param octaves     fractal noise layer count (>= 1)
     * @param persistence per-octave amplitude falloff (0..1)
     * @param lacunarity  per-octave frequency growth
     */
    public EdgeShaper(double scale, double amplitude, int octaves, double persistence, double lacunarity) {
        this.scale = scale;
        this.amplitude = amplitude;
        this.octaves = octaves;
        this.persistence = persistence;
        this.lacunarity = lacunarity;
    }

    /**
     * Shapes {@code input}'s boundary deterministically for the given operation seed. Every
     * tracked column keeps its position in the output; only its signed distance is perturbed.
     *
     * @param input source mask (untouched; a new PathMask is returned)
     * @param seed  operation seed driving the noise field (same seed -> identical output)
     * @return a new PathMask with the same tracked columns and a wobbled distance field
     */
    public PathMask shape(PathMask input, long seed) {
        PerlinNoise noise = new PerlinNoise(seed);
        Map<ColumnPos, Double> source = input.edgeDistances();
        Map<ColumnPos, Double> shaped = new HashMap<>(source.size());

        for (Map.Entry<ColumnPos, Double> entry : source.entrySet()) {
            ColumnPos col = entry.getKey();
            double offset = amplitude * noise.fractal(col.x() * scale, col.z() * scale, octaves, persistence, lacunarity);
            shaped.put(col, entry.getValue() + offset);
        }

        return PathMask.of(shaped);
    }
}
