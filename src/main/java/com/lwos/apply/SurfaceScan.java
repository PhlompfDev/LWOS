package com.lwos.apply;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

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
}
