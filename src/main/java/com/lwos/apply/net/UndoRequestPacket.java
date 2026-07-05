package com.lwos.apply.net;

import com.lwos.apply.LwosServerState;
import com.lwos.apply.UndoHistory.BlockSnapshot;
import com.lwos.plan.BlockStateRef;
import com.lwos.plan.GridPos;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Client → server "undo my last commit". No payload; the server pops the sender's undo stack. */
public record UndoRequestPacket() {

    public static void encode(UndoRequestPacket msg, FriendlyByteBuf buf) { }

    public static UndoRequestPacket decode(FriendlyByteBuf buf) { return new UndoRequestPacket(); }

    public static void handle(UndoRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            Optional<List<BlockSnapshot>> commit = LwosServerState.UNDO.pop(sender.getUUID());
            if (commit.isEmpty()) return;
            for (BlockSnapshot s : commit.get()) {
                BlockState state = resolve(s.priorState());
                if (state == null) continue;
                GridPos p = s.pos();
                level.setBlock(new BlockPos(p.x(), p.y(), p.z()), state, Block.UPDATE_ALL);
            }
        });
        context.setPacketHandled(true);
    }

    // Mirror of PlacementEngine.resolve (kept local so the plan package stays MC-free).
    private static BlockState resolve(BlockStateRef ref) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ref.id()));
        if (block == null) return null;
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> e : ref.properties().entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(e.getKey());
            if (property == null) continue;
            state = withProperty(state, property, e.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(v -> state.setValue(property, v)).orElse(state);
    }
}
