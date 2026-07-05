package com.lwos.organic;

import com.lwos.geometry.PathMask;
import com.lwos.organic.noise.PerlinNoise;
import com.lwos.plan.BlockStateRef;

import java.util.Optional;

/**
 * The M6 "outskirts" stage: fills the path's outer band with edge-palette materials
 * (e.g. coarse dirt, moss) that dither out to original terrain at the rim, giving a soft shoulder
 * that melts into the ground. Only columns whose core block was dropped by {@link BlendEngine} in
 * the outer band {@code -skirtWidth < edgeDistance <= 0} are candidates.
 *
 * <p>Fill probability smoothsteps from 1.0 at the inner band edge ({@code d = -skirtWidth}) to 0.0
 * at the rim ({@code d = 0}); a seeded Perlin coin decides keep/drop, and a kept column's block is
 * chosen by a {@link GradientEngine} over the edge palette so the shoulder clusters rather than
 * reading as static. Pure and deterministic.
 */
public final class EdgeBandEngine {

    /** Higher-frequency than the boundary wobble so neighbouring columns can resolve differently. */
    private static final double NOISE_SCALE = 0.35;
    private static final long COIN_SALT = 0x51ED270B7C3D5A11L;

    private final int skirtWidth;
    private final GradientEngine material;
    private final PerlinNoise coin;

    public EdgeBandEngine(long seed, int skirtWidth, Palette edgePalette) {
        if (skirtWidth <= 0) throw new IllegalArgumentException("skirtWidth must be > 0, got " + skirtWidth);
        this.skirtWidth = skirtWidth;
        this.material = new GradientEngine(seed, edgePalette);
        this.coin = new PerlinNoise(seed ^ COIN_SALT);
    }

    public Optional<BlockStateRef> edgeBlockAt(PathMask mask, int x, int z) {
        double d = mask.edgeDistance(x, z);
        if (d > 0.0 || d <= -skirtWidth) return Optional.empty(); // not in the outer band
        double fill = fillProbability(d);
        double sample = (coin.noise(x * NOISE_SCALE, z * NOISE_SCALE) + 1.0) / 2.0; // [0,1)
        if (sample >= fill) return Optional.empty(); // dropped -> leave terrain
        return Optional.of(material.blockAt(x, 0, z));
    }

    /** 1.0 at d = -skirtWidth (inner band edge), smoothstepping to 0.0 at d = 0 (rim). */
    private double fillProbability(double d) {
        double t = -d / skirtWidth; // 0 at rim, 1 at inner edge
        return t * t * (3 - 2 * t);
    }
}
