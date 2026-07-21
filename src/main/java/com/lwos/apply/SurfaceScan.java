package com.lwos.apply;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Shared, deterministic ground-finder for both the client ({@code ForgeWorldView}) and server
 * ({@link ServerWorldView}) world views (Global Constraint: determinism — both sides must derive
 * the identical surface height for the same column).
 *
 * <p>The {@code MOTION_BLOCKING} heightmap stops at tree canopies (leaves/logs block motion), so a
 * raw query would build paths across treetops. We start there and scan downward, skipping leaves,
 * logs, and non-motion-blocking blocks (air, plants) until we hit true solid ground.
 */
public final class SurfaceScan {
    private SurfaceScan() { }

    /** Y index of the topmost solid, non-foliage block at the column, scanning down from MOTION_BLOCKING. */
    public static int solidSurfaceHeight(LevelReader level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (y > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && state.blocksMotion()) break;
            y--;
            pos.setY(y);
        }
        return y;
    }

    /**
     * Y of the topmost block that counts as ground (spec §4): scans down from MOTION_BLOCKING,
     * skipping leaves, logs, snow layers, and anything that doesn't block motion (saplings,
     * flowers, grass/ferns, small mushrooms, vines are all non-motion-blocking). Shared by both
     * world views so client preview and server apply answer identically (preview==apply).
     */
    public static int groundHeight(LevelReader level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (y > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                    && !state.is(Blocks.SNOW) && state.blocksMotion()) break;
            y--;
            pos.setY(y);
        }
        return y;
    }

    /** Registry id string of the topmost solid surface block at the column (spec §3). */
    public static String surfaceBlockId(LevelReader level, int x, int z) {
        int y = solidSurfaceHeight(level, x, z);
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id == null ? "minecraft:air" : id.toString();
    }

    /** Registry id of the block at the exact position ("minecraft:air" outside the build height). */
    public static String blockId(LevelReader level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.isOutsideBuildHeight(pos)) return "minecraft:air";
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        return id == null ? "minecraft:air" : id.toString();
    }
}
