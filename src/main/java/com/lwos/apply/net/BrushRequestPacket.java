package com.lwos.apply.net;

import com.lwos.apply.PlacementEngine;
import com.lwos.apply.ServerWorldView;
import com.lwos.brush.BrushOp;
import com.lwos.brush.BrushPlanBuilder;
import com.lwos.plan.EditPlan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server "commit this brush dab" request (spec §5). Carries only the user's intent —
 * op, center column, radius — never blocks, never a style (brushes have none), never a seed
 * (v1 kernels are noise-free). The server re-derives the plan over its own
 * {@link ServerWorldView} via the same {@link BrushPlanBuilder#build} the preview used, so
 * preview==apply holds by construction.
 */
public record BrushRequestPacket(int opOrdinal, int cx, int cz, int radius) {
    /** Server-side radius clamp (mirrors TerrainBrushTool's client clamp) so a crafted packet can't over-edit. */
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 16;

    public static void encode(BrushRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.opOrdinal());
        buf.writeInt(msg.cx());
        buf.writeInt(msg.cz());
        buf.writeVarInt(msg.radius());
    }

    public static BrushRequestPacket decode(FriendlyByteBuf buf) {
        int op = buf.readVarInt();
        if (op < 0 || op >= BrushOp.values().length) {
            throw new IllegalArgumentException("BrushRequestPacket op ordinal out of range: " + op);
        }
        return new BrushRequestPacket(op, buf.readInt(), buf.readInt(), buf.readVarInt());
    }

    public static void handle(BrushRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            // Authoritative access gate: the client-side check is UX only (spec §5).
            if (!com.lwos.LwosAccess.isAllowed(sender.getGameProfile().getName())) return;
            ServerLevel level = sender.serverLevel();
            int radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, msg.radius()));
            BrushOp op = BrushOp.values()[msg.opOrdinal()];

            // Deterministic rebuild from the server's own world view — never trust client blocks.
            EditPlan plan = BrushPlanBuilder.build(op, msg.cx(), msg.cz(), radius, new ServerWorldView(level));
            // Bedrock is only replaceable in creative — survival dabs leave the world floor intact.
            java.util.List<com.lwos.apply.UndoHistory.BlockSnapshot> priors =
                    PlacementEngine.apply(level, plan, sender.isCreative());
            if (!priors.isEmpty()) com.lwos.apply.LwosServerState.UNDO.push(sender.getUUID(), priors);
        });
        context.setPacketHandled(true);
    }
}
