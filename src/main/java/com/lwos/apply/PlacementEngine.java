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
import net.minecraft.world.level.block.Blocks;
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

    /**
     * Applies the plan and returns the prior states it overwrote (for the per-player undo stack).
     *
     * @param canReplaceBedrock whether the committing player may overwrite bedrock (true only in
     *                          creative). When false, any change targeting an existing bedrock block
     *                          is skipped so survival edits can't punch through the world floor.
     */
    public static List<BlockSnapshot> apply(ServerLevel level, EditPlan plan, boolean canReplaceBedrock) {
        List<BlockSnapshot> priors = new ArrayList<>();
        for (PlannedChange change : plan.changes().values()) {
            BlockState state = resolve(change.state());
            if (state == null) continue; // unknown block id — skip rather than crash
            GridPos p = change.pos();
            BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
            // Protect bedrock outside creative: leave the existing block untouched (spec: no carving
            // or overwriting the bedrock floor in survival).
            if (!canReplaceBedrock && level.getBlockState(pos).is(Blocks.BEDROCK)) continue;
            priors.add(new BlockSnapshot(p, toRef(level.getBlockState(pos))));
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        return priors;
    }

    /**
     * Writes each snapshot's state into the world, capturing the block it replaced, and returns the
     * inverse snapshot list. Undo and redo both call this: undo passes prior-states and gets the
     * placed-states back (to push onto redo); redo passes those placed-states and gets prior-states
     * back (to push back onto undo).
     */
    public static List<BlockSnapshot> restoreSnapshots(ServerLevel level, List<BlockSnapshot> snapshots) {
        List<BlockSnapshot> inverse = new ArrayList<>();
        for (BlockSnapshot s : snapshots) {
            BlockState target = resolve(s.priorState());
            if (target == null) continue;
            GridPos p = s.pos();
            BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
            BlockState current = level.getBlockState(bp);
            inverse.add(new BlockSnapshot(p, toRef(current)));
            level.setBlock(bp, target, Block.UPDATE_ALL);
        }
        return inverse;
    }

    /** Serializes a Forge BlockState back to a pure BlockStateRef (id + string properties) for undo. */
    public static BlockStateRef toRef(BlockState state) {
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

    public static BlockState resolve(BlockStateRef ref) {
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
