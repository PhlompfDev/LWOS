package com.lwos.geometry;

/**
 * Read-only world query surface for pure geometry code (spec §4.1, §4.3 boundary rule).
 * MUST NOT be implemented by anything importing net.minecraft.* in this package —
 * the Forge-backed implementation lives in com.lwos.client.
 */
public interface WorldView {
    /** Y coordinate of the topmost solid surface block at the given column. */
    int surfaceHeight(int x, int z);
}
