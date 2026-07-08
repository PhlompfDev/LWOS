package com.lwos.brush;

/**
 * The four terrain-brush kernels (spec §2). Each is a pure function HeightField -> HeightField
 * over the columns within {@code radius} of the brush center, faded by
 * {@code smoothstep(1 - dist/radius)} so every op softens at the rim instead of cutting a
 * cylinder; {@code dist > radius} is never touched. Neighbor reads always come from the
 * original snapshot (single-pass). v1 kernels are noise-free: no RNG, no seed — determinism
 * is structural (same WorldView answers -> byte-identical plan).
 */
public enum BrushOp {
    SMOOTH("Smooth"),
    MELT("Melt"),
    FILL("Fill"),
    LIFT("Lift");

    private final String displayName;

    BrushOp(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    /** Cycle order for the M key: Smooth -> Melt -> Fill -> Lift -> Smooth (spec §1). */
    public BrushOp next() {
        BrushOp[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Applies this kernel around (cx, cz), reading the original field and returning a modified copy. */
    public HeightField apply(HeightField field, int cx, int cz, int radius) {
        int[] out = field.copyHeights();
        for (int z = cz - radius; z <= cz + radius; z++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                long dx = x - cx;
                long dz = z - cz;
                double dist = Math.sqrt((double) (dx * dx + dz * dz));
                if (dist > radius) continue;
                double t = 1.0 - dist / radius;
                double falloff = t * t * (3.0 - 2.0 * t); // smoothstep
                field.set(out, x, z, kernel(field, x, z, falloff));
            }
        }
        return field.with(out);
    }

    private int kernel(HeightField field, int x, int z, double falloff) {
        int old = field.height(x, z);
        switch (this) {
            case SMOOTH -> {
                // 3x3 weighted mean: center 4, edges 2, corners 1, /16; rounded, then lerped
                // from the original height toward the mean by the radial falloff.
                double mean = (4.0 * old
                        + 2.0 * (field.height(x + 1, z) + field.height(x - 1, z)
                               + field.height(x, z + 1) + field.height(x, z - 1))
                        + field.height(x + 1, z + 1) + field.height(x - 1, z + 1)
                        + field.height(x + 1, z - 1) + field.height(x - 1, z - 1)) / 16.0;
                int smoothed = (int) Math.round(mean);
                return old + (int) Math.round((smoothed - old) * falloff);
            }
            case MELT -> {
                // Morphological erosion: a column strictly higher than the max of its
                // 4-neighbors is pulled down toward that max, scaled by falloff.
                int max4 = Math.max(Math.max(field.height(x + 1, z), field.height(x - 1, z)),
                                    Math.max(field.height(x, z + 1), field.height(x, z - 1)));
                return old > max4 ? old - (int) Math.round((old - max4) * falloff) : old;
            }
            case FILL -> {
                // Morphological dilation: a column strictly lower than the min of its
                // 4-neighbors is raised toward that min, scaled by falloff.
                int min4 = Math.min(Math.min(field.height(x + 1, z), field.height(x - 1, z)),
                                    Math.min(field.height(x, z + 1), field.height(x, z - 1)));
                return old < min4 ? old + (int) Math.round((min4 - old) * falloff) : old;
            }
            case LIFT -> {
                // Plateau-ish raise with soft edges: +1 wherever falloff rounds to 1.
                return old + (int) Math.round(falloff);
            }
        }
        throw new AssertionError("unreachable");
    }
}
