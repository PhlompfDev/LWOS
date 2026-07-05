package com.lwos.apply;

import com.lwos.plan.BlockStateRef;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Applies a committed {@link EditPlan} to the server world (spec §6 apply stage): maps each
 * pure {@link GridPos} to a {@link BlockPos} and each {@link BlockStateRef} to a Forge
 * {@link BlockState}, then writes the blocks. The only place that mutates the world.
 */
public final class PlacementEngine {
    private PlacementEngine() { }

    public static void apply(ServerLevel level, EditPlan plan) {
        for (PlannedChange change : plan.changes().values()) {
            BlockState state = resolve(change.state());
            if (state == null) continue; // unknown block id — skip rather than crash
            GridPos p = change.pos();
            level.setBlock(new BlockPos(p.x(), p.y(), p.z()), state, Block.UPDATE_ALL);
        }
    }

    private static BlockState resolve(BlockStateRef ref) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ref.id()));
        return block == null ? null : block.defaultBlockState();
    }
}
