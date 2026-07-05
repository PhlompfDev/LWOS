package com.lwos.apply;

import com.lwos.apply.UndoHistory.BlockSnapshot;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies a committed {@link EditPlan} to the server world (spec §6 apply stage): maps each
 * pure {@link GridPos} to a {@link BlockPos} and each {@link BlockStateRef} to a Forge
 * {@link BlockState}, then writes the blocks. The only place that mutates the world.
 */
public final class PlacementEngine {
    private PlacementEngine() { }

    /** Applies the plan and returns the prior states it overwrote (for the per-player undo stack). */
    public static List<BlockSnapshot> apply(ServerLevel level, EditPlan plan) {
        List<BlockSnapshot> priors = new ArrayList<>();
        for (PlannedChange change : plan.changes().values()) {
            BlockState state = resolve(change.state());
            if (state == null) continue; // unknown block id — skip rather than crash
            GridPos p = change.pos();
            BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
            BlockState prior = level.getBlockState(bp);
            priors.add(new BlockSnapshot(p, toRef(prior)));
            level.setBlock(bp, state, Block.UPDATE_ALL);
        }
        return priors;
    }

    /** Serializes a Forge BlockState back to a pure BlockStateRef (id + string properties) for undo. */
    private static BlockStateRef toRef(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        BlockStateRef ref = new BlockStateRef(id == null ? "minecraft:air" : id.toString());
        for (Property<?> property : state.getProperties()) {
            ref = ref.with(property.getName(), namedValue(state, property));
        }
        return ref;
    }

    private static <T extends Comparable<T>> String namedValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
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
