package com.lwos.apply.net;

import com.lwos.geometry.Vec3d;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client -> server "commit this path" request (spec §6). Carries only the user's intent —
 * the path control points and global width — not a block payload. The server deterministically
 * rebuilds the {@code EditPlan} via {@link com.lwos.apply.ServerWorldView} before applying it.
 */
public record EditRequestPacket(List<Vec3d> controlPoints, double width) {
    /** Guards the server against a malicious/oversized control-point list. */
    private static final int MAX_CONTROL_POINTS = 4096;

    public static void encode(EditRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.controlPoints().size());
        for (Vec3d p : msg.controlPoints()) {
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
        }
        buf.writeDouble(msg.width());
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
        return new EditRequestPacket(points, width);
    }

    public static void handle(EditRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            // Task 5 wires the server-side rebuild + PlacementEngine.apply here.
        });
        context.setPacketHandled(true);
    }
}
