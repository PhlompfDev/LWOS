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

    /**
     * Registry id of the block at an exact position. Default derives a column-world answer
     * from the surface queries (above surface = air, else the surface block id) so simple
     * fake views keep working; the Forge-backed views override with real lookups.
     */
    default String blockIdAt(int x, int y, int z) {
        return y > surfaceHeight(x, z) ? "minecraft:air" : surfaceBlockId(x, z);
    }

    /**
     * Whether a placement may overwrite the block at the position (air, grass, flowers,
     * snow layers, fluids — Minecraft's replaceable notion). Default treats only air-like
     * ids as replaceable so simple fake views keep working; the Forge-backed views
     * override with the real {@code BlockState.canBeReplaced()} answer.
     */
    default boolean isReplaceableAt(int x, int y, int z) {
        String id = blockIdAt(x, y, z);
        return "minecraft:air".equals(id) || "minecraft:cave_air".equals(id) || "minecraft:void_air".equals(id);
    }
}
