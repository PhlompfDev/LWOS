package com.lwos.organic;

import com.lwos.geometry.PathMask;
import com.lwos.organic.noise.PerlinNoise;
import com.lwos.plan.BlockStateRef;

import java.util.Optional;

/**
 * Decides, per column, whether to place an edge-palette block, giving the builder direct control
 * over how many edge blocks appear ({@code coverage}) and how patchy they are ({@code clusterSize}).
 * Supersedes the old fixed-skirt edge-band engine: the scatter band spans the inner feather skirt
 * ({@code -blendDepth < d <= 0}) AND an outward reach onto surrounding terrain
 * ({@code 0 < d <= edgeReach}), so the path melds into the ground instead of ending on a clean line.
 *
 * <p>Placement at column {@code (x,z)} with signed edge distance {@code d}:
 * place iff {@code coverage * falloff(d) > coin(x,z)}, where {@code falloff} peaks at the rim
 * ({@code d ≈ 0}) and smoothsteps to 0 at each band limit, and {@code coin} is a seeded Perlin field
 * sampled at frequency {@code 1/clusterSize} — so cluster size actually drives the patch size
 * (the old engine left this fixed). The kept column's block is chosen by a
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
