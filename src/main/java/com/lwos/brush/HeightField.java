package com.lwos.brush;

import com.lwos.geometry.WorldView;

/**
 * Immutable local ground-height grid over the brush disc plus a 1-block ring (spec §2 —
 * kernels read 3x3 / 4-neighborhoods, so every column within the radius needs sampled
 * neighbors). Heights come from {@link WorldView#groundHeight}, the flora-skipping ground
 * mask (spec §4), never the raw surface. Ops never mutate a field; they take a working copy
 * via {@link #copyHeights()} and wrap it into a new field via {@link #with(int[])}.
 */
public final class HeightField {
    private final int minX;
    private final int minZ;
    private final int size;      // grid is size x size, row-major: index = (z - minZ) * size + (x - minX)
    private final int[] heights;

    private HeightField(int minX, int minZ, int size, int[] heights) {
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
        this.heights = heights;
    }

    /** Samples the square [c-radius-1, c+radius+1] on both axes from the view's ground mask. */
    public static HeightField sample(WorldView view, int cx, int cz, int radius) {
        int size = 2 * (radius + 1) + 1;
        int minX = cx - radius - 1;
        int minZ = cz - radius - 1;
        int[] h = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                h[z * size + x] = view.groundHeight(minX + x, minZ + z);
            }
        }
        return new HeightField(minX, minZ, size, h);
    }

    /** Ground height at world column (x, z). Must be within the sampled square. */
    public int height(int x, int z) {
        return heights[(z - minZ) * size + (x - minX)];
    }

    /** Working copy of the raw height array, for a kernel to mutate then wrap via {@link #with}. */
    public int[] copyHeights() {
        return heights.clone();
    }

    /** Writes a height into a working array obtained from {@link #copyHeights()} (world coords). */
    public void set(int[] target, int x, int z, int height) {
        target[(z - minZ) * size + (x - minX)] = height;
    }

    /** New field with the same bounds and the given heights (takes ownership of the array). */
    public HeightField with(int[] newHeights) {
        return new HeightField(minX, minZ, size, newHeights);
    }
}
