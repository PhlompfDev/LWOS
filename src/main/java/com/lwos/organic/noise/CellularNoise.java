package com.lwos.organic.noise;

/**
 * Self-contained, deterministic Cellular (Worley) noise (M5 plan, Task 1).
 *
 * <p>Worley noise scatters one feature point per integer cell and answers questions about the
 * nearest feature. The organic engine uses it for <em>clumped patches</em>: {@link #cellValue}
 * is constant across an entire cell, so thresholding it paints contiguous runs of one material
 * rather than per-block static (M5 goal: "clustered patches, not uniform random").
 *
 * <p>Like {@link PerlinNoise} it is seeded from a {@code long} and reads no clock or shared RNG,
 * so identical (x, y, seed) triples always produce identical results.
 */
public final class CellularNoise {

    private final long seed;

    public CellularNoise(long seed) {
        this.seed = seed;
    }

    /**
     * F1 distance: Euclidean distance from (x, y) to the nearest feature point, searching the
     * 3x3 block of cells around the sample. Always {@code >= 0}; in practice below ~1.5. Small
     * near a feature, larger in the gaps between them — useful for eroding edges irregularly.
     */
    public double f1(double x, double y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        double best = Double.POSITIVE_INFINITY;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                int gx = cx + ox;
                int gy = cy + oy;
                double fx = gx + hash01(gx, gy, seed);
                double fy = gy + hash01(gx, gy, seed ^ 0x1234567890ABCDEFL);
                double dx = fx - x;
                double dy = fy - y;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < best) best = d;
            }
        }
        return best;
    }

    /**
     * A deterministic {@code [0.0, 1.0)} value tied to the <em>cell that owns the nearest feature
     * point</em>. It is constant everywhere inside that cell's Worley region, so callers get large
     * contiguous patches sharing one value — the basis for clustered material runs.
     */
    public double cellValue(double x, double y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        double best = Double.POSITIVE_INFINITY;
        int bestX = cx, bestY = cy;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                int gx = cx + ox;
                int gy = cy + oy;
                double fx = gx + hash01(gx, gy, seed);
                double fy = gy + hash01(gx, gy, seed ^ 0x1234567890ABCDEFL);
                double dx = fx - x;
                double dy = fy - y;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < best) {
                    best = d;
                    bestX = gx;
                    bestY = gy;
                }
            }
        }
        return hash01(bestX, bestY, seed ^ 0x27D4EB2F165667C5L);
    }

    /** Deterministic hash of an integer cell + seed into {@code [0.0, 1.0)}. */
    private static double hash01(int x, int y, long seed) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= y * 0xC2B2AE3D27D4EB4FL;
        h = mix64(h);
        return (h >>> 11) * 0x1.0p-53; // top 53 bits -> [0,1)
    }

    /** splitmix64 finaliser — maps a 64-bit state to a well-distributed 64-bit hash. */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
