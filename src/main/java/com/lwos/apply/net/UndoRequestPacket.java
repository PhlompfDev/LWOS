package com.lwos.apply.net;

import com.lwos.apply.LwosServerState;
import com.lwos.apply.UndoHistory.BlockSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
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
            if (sender == null || !com.lwos.LwosAccess.isAllowed(sender.getGameProfile().getName())) return;
            ServerLevel level = sender.serverLevel();
            Optional<List<BlockSnapshot>> commit = LwosServerState.UNDO.pop(sender.getUUID());
            if (commit.isEmpty()) return;
            List<BlockSnapshot> placed = com.lwos.apply.PlacementEngine.restoreSnapshots(level, commit.get());
            LwosServerState.UNDO.pushRedo(sender.getUUID(), placed);
        });
        context.setPacketHandled(true);
    }
}
