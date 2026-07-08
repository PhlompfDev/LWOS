package com.lwos.geometry;

/**
 * Read-only world query surface for pure geometry code (spec §4.1, §4.3 boundary rule).
 * MUST NOT be implemented by anything importing net.minecraft.* in this package —
 * the Forge-backed implementation lives in com.lwos.client.
 */
public interface WorldView {
    /** Y coordinate of the topmost solid surface block at the given column. */
    int surfaceHeight(int x, int z);

    /**
     * Y of the topmost block that counts as *ground* at the column — skips logs, leaves,
     * saplings, flowers, grass/ferns, mushrooms, vines, and snow layers (the FAWE ground-mask
     * lesson: naive surface detection smears trees into terrain). Defaults to
     * {@link #surfaceHeight}: with no flora present, ground and surface coincide.
     */
    default int groundHeight(int x, int z) {
        return surfaceHeight(x, z);
    }

    /** Block id (e.g. "minecraft:stone") of the topmost solid surface block at the column. */
    String surfaceBlockId(int x, int z);
}
