package com.lwos.apply.net;

import com.lwos.apply.PlacementEngine;
import com.lwos.apply.ServerWorldView;
import com.lwos.config.PathStyle;
import com.lwos.geometry.Vec3d;
import com.lwos.plan.EditPlan;
import com.lwos.plan.EditPlanBuilder;
import com.lwos.plan.TerrainMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client -> server "commit this path" request (spec §6). Carries only the user's intent —
 * the path control points and global width — not a block payload. The server deterministically
 * rebuilds the {@code EditPlan} via {@link com.lwos.apply.ServerWorldView} before applying it.
 */
public record EditRequestPacket(List<Vec3d> controlPoints, double width, TerrainMode mode, String styleJson) {
    /** Guards the server against a malicious/oversized control-point list. */
    private static final int MAX_CONTROL_POINTS = 4096;
    /** Guards against an oversized style blob. */
    private static final int MAX_STYLE_JSON = 64 * 1024;
    /** Server-side width clamp (mirrors PathTool's client clamp) so a crafted packet can't over-place. */
    private static final double MIN_WIDTH = 1.0;
    private static final double MAX_WIDTH = 15.0;

    public static void encode(EditRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.controlPoints().size());
        for (Vec3d p : msg.controlPoints()) {
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
        }
        buf.writeDouble(msg.width());
        buf.writeVarInt(msg.mode().ordinal());
        buf.writeUtf(msg.styleJson(), MAX_STYLE_JSON);
    }

    public static EditRequestPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_CONTROL_POINTS) {
            throw new IllegalArgumentException("EditRequestPacket control-point count out of range: " + n);
        }
        List<Vec3d> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            points.add(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        double width = buf.readDouble();
        int modeOrdinal = buf.readVarInt();
        TerrainMode[] modes = TerrainMode.values();
        if (modeOrdinal < 0 || modeOrdinal >= modes.length) {
            throw new IllegalArgumentException("EditRequestPacket terrain mode out of range: " + modeOrdinal);
        }
        String styleJson = buf.readUtf(MAX_STYLE_JSON);
        return new EditRequestPacket(points, width, modes[modeOrdinal], styleJson);
    }

    public static void handle(EditRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || msg.controlPoints().isEmpty()) return;
            ServerLevel level = sender.serverLevel();
            double width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, msg.width()));
            // Deterministic rebuild from the sender's own world view — never trust client blocks (spec §6).
            // The committed style rides the packet so apply matches preview. Parse defensively: a
            // malformed blob falls back to defaults rather than crashing the server thread.
            PathStyle style;
            try { style = PathStyle.fromJson(msg.styleJson()); }
            catch (RuntimeException e) { style = PathStyle.defaults(); }
            EditPlan plan = EditPlanBuilder.build(
                    msg.controlPoints(), EditPlanBuilder.DEFAULT_SPACING, width, new ServerWorldView(level),
                    msg.mode(), style);
            java.util.List<com.lwos.apply.UndoHistory.BlockSnapshot> priors = PlacementEngine.apply(level, plan);
            if (!priors.isEmpty()) com.lwos.apply.LwosServerState.UNDO.push(sender.getUUID(), priors);
        });
        context.setPacketHandled(true);
    }
}
