package com.lwos.apply.net;

import com.lwos.apply.LwosServerState;
import com.lwos.apply.PlacementEngine;
import com.lwos.apply.UndoHistory.BlockSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Client → server "redo my last undone commit". No payload; the server pops the sender's redo stack. */
public record RedoRequestPacket() {

    public static void encode(RedoRequestPacket msg, FriendlyByteBuf buf) { }

    public static RedoRequestPacket decode(FriendlyByteBuf buf) { return new RedoRequestPacket(); }

    public static void handle(RedoRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !com.lwos.LwosAccess.isAllowed(sender.getGameProfile().getName())) return;
            ServerLevel level = sender.serverLevel();
            Optional<List<BlockSnapshot>> commit = LwosServerState.UNDO.popRedo(sender.getUUID());
            if (commit.isEmpty()) return;
            List<BlockSnapshot> priors = PlacementEngine.restoreSnapshots(level, commit.get());
            LwosServerState.UNDO.restoreUndo(sender.getUUID(), priors);
        });
        context.setPacketHandled(true);
    }
}
