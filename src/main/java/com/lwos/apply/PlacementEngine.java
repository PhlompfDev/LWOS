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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;

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
        if (block == null) return null; // unknown block id — skip rather than crash
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> e : ref.properties().entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(e.getKey());
            if (property == null) continue; // property not defined on this block — ignore, keep default
            state = withProperty(state, property, e.getValue());
        }
        return state;
    }

    /** Parses {@code value} against {@code property} and applies it; leaves the state unchanged if it can't parse. */
    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(v -> state.setValue(property, v)).orElse(state);
    }
}
