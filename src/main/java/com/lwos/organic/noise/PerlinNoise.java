package com.lwos.organic.noise;

/**
 * Self-contained, deterministic 2D Perlin (improved) noise (M5 plan, Task 1).
 *
 * <p>The organic engine must never read wall-clock time or an unseeded {@code Random}
 * (Global Constraint: Determinism Guarantee). This implementation builds its permutation
 * table purely from a {@code long} seed via a splitmix64 mixer, so a given seed yields the
 * exact same field on every JVM — no dependence on {@code java.util.Random}'s internals.
 *
 * <p>{@link #noise(double, double)} returns a value in {@code [-1.0, 1.0]}. At integer lattice
 * points the value is exactly {@code 0} (a property of Perlin noise), and the field is smooth
 * (C1-continuous) between them.
 */
public final class PerlinNoise {

    // Eight unit gradient vectors (axis-aligned + diagonals). Perlin noise sums a corner's
    // gradient dotted with the offset to the sample point; a small fixed set keeps it cheap.
    private static final double DIAG = 0.7071067811865476; // 1/sqrt(2)
    private static final double[][] GRAD2 = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {DIAG, DIAG}, {-DIAG, DIAG}, {DIAG, -DIAG}, {-DIAG, -DIAG}
    };
    // Raw improved-Perlin output peaks near +-1/sqrt(2); scaling by sqrt(2) spreads it across the
    // full [-1,1] contract. The final clamp guards the rare corner case where scaling nudges past 1.
    private static final double NORM = 1.4142135623730951;

    private final int[] perm; // 512 entries: p duplicated so index (i+1) never overflows

    public PerlinNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;

        // Seeded Fisher-Yates shuffle driven by splitmix64. Each step advances the state by the
        // golden-ratio constant before mixing, so the sequence never sticks (even for seed 0).
        long state = seed;
        for (int i = 255; i > 0; i--) {
            state += 0x9E3779B97F4A7C15L;
            long r = mix64(state);
            int j = (int) Long.remainderUnsigned(r, i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }

        perm = new int[512];
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    /** Single-octave noise at (x, y). Deterministic; result in {@code [-1.0, 1.0]}. */
    public double noise(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);

        double u = fade(xf);
        double v = fade(yf);

        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];

        double x1 = lerp(u, grad(aa, xf, yf), grad(ba, xf - 1, yf));
        double x2 = lerp(u, grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1));
        double result = lerp(v, x1, x2) * NORM;

        return Math.max(-1.0, Math.min(1.0, result));
    }

    /**
     * Fractal (fBm) noise: sums {@code octaves} layers of {@link #noise} at rising frequency and
     * falling amplitude, normalised back into {@code [-1.0, 1.0]}. Low {@code lacunarity} plus few
     * octaves gives the broad, low-frequency field the organic stages use to cluster placements.
     *
     * @param octaves     number of noise layers (>= 1)
     * @param persistence amplitude multiplier per octave (0..1, typically 0.5)
     * @param lacunarity  frequency multiplier per octave (typically 2.0)
     */
    public double fractal(double x, double y, int octaves, double persistence, double lacunarity) {
        double sum = 0, amplitude = 1, frequency = 1, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amplitude * noise(x * frequency, y * frequency);
            norm += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return norm == 0 ? 0 : sum / norm;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        double[] g = GRAD2[hash & 7];
        return g[0] * x + g[1] * y;
    }

    /** splitmix64 finaliser — maps a 64-bit state to a well-distributed 64-bit hash. */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
