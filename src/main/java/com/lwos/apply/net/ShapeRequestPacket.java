package com.lwos.apply.net;

import com.lwos.LwosServerConfig;
import com.lwos.apply.PlacementEngine;
import com.lwos.apply.ServerWorldView;
import com.lwos.plan.BlockStateRef;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeGeometry;
import com.lwos.shape.ShapeMode;
import com.lwos.shape.ShapeOptions;
import com.lwos.shape.ShapePlanBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client -> server "commit this shape" request (spec §6). Intent only: anchors, mode,
 * options, material id, break flag — never a block list. The server re-derives the plan
 * via the same {@link ShapePlanBuilder#build} the preview used (preview == apply), then
 * enforces the survival rules (spec §7) before applying.
 */
public record ShapeRequestPacket(List<GridPos> anchors, int modeOrdinal, String optionsJson,
                                 String materialId, boolean breakMode) {
    /** Max anchor distance from the committing player (matches client reach + shape extent). */
    private static final double MAX_ANCHOR_DISTANCE = 192.0;

    public static void encode(ShapeRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.anchors().size());
        for (GridPos p : msg.anchors()) {
            buf.writeInt(p.x());
            buf.writeInt(p.y());
            buf.writeInt(p.z());
        }
        buf.writeVarInt(msg.modeOrdinal());
        buf.writeUtf(msg.optionsJson(), 256);
        buf.writeUtf(msg.materialId(), 256);
        buf.writeBoolean(msg.breakMode());
    }

    public static ShapeRequestPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 1 || n > 3) throw new IllegalArgumentException("shape anchor count out of range: " + n);
        List<GridPos> anchors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) anchors.add(new GridPos(buf.readInt(), buf.readInt(), buf.readInt()));
        int mode = buf.readVarInt();
        if (mode < 0 || mode >= ShapeMode.values().length) {
            throw new IllegalArgumentException("shape mode ordinal out of range: " + mode);
        }
        return new ShapeRequestPacket(anchors, mode, buf.readUtf(256), buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(ShapeRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            // Authoritative access gate: the client-side check is UX only.
            if (!com.lwos.LwosAccess.isAllowed(sender.getGameProfile().getName())) return;

            ShapeMode mode = ShapeMode.values()[msg.modeOrdinal()];
            if (msg.anchors().size() != mode.clickCount()) return;
            if (!anchorsValid(sender, msg.anchors())) return;

            ServerLevel level = sender.serverLevel();
            ShapeOptions options = ShapeOptions.fromJson(msg.optionsJson());
            BlockStateRef material = new BlockStateRef(msg.materialId());

            EditPlan plan = ShapePlanBuilder.build(msg.anchors(), mode, options, material,
                    msg.breakMode(), new ServerWorldView(level));
            if (plan.isEmpty()) return;

            int cap = LwosServerConfig.MAX_BLOCKS_PER_COMMIT.get();
            if (plan.size() > cap) {
                sender.displayClientMessage(Component.literal(
                        "Shape too large: " + plan.size() + " blocks (cap " + cap + ")"), true);
                return;
            }

            boolean survival = LwosServerConfig.SURVIVAL_MODE.get() && !sender.isCreative();
            if (survival && !msg.breakMode() && !consumeMaterials(sender, material, plan.size())) {
                return; // consumeMaterials reported the shortfall
            }

            List<com.lwos.apply.UndoHistory.BlockSnapshot> priors = PlacementEngine.apply(
                    level, plan, sender.isCreative(), survival && msg.breakMode());
            if (!priors.isEmpty()) com.lwos.apply.LwosServerState.UNDO.push(sender.getUUID(), priors);
            sender.displayClientMessage(Component.literal(
                    (msg.breakMode() ? "Broke " : "Placed ") + priors.size() + " blocks"), true);
        });
        context.setPacketHandled(true);
    }

    private static boolean anchorsValid(ServerPlayer sender, List<GridPos> anchors) {
        GridPos first = anchors.get(0);
        double dx = first.x() - sender.getX(), dy = first.y() - sender.getY(), dz = first.z() - sender.getZ();
        if (dx * dx + dy * dy + dz * dz > MAX_ANCHOR_DISTANCE * MAX_ANCHOR_DISTANCE) return false;
        for (GridPos p : anchors) {
            if (sender.serverLevel().isOutsideBuildHeight(p.y())) return false;
            if (Math.abs(p.x() - first.x()) > ShapeGeometry.MAX_EXTENT
                    || Math.abs(p.y() - first.y()) > ShapeGeometry.MAX_EXTENT
                    || Math.abs(p.z() - first.z()) > ShapeGeometry.MAX_EXTENT) return false;
        }
        return true;
    }

    /**
     * Survival place cost: {@code count} items matching the material's block, taken from the
     * player's inventory. All-or-nothing (spec §7 "no half-shapes"): on shortfall nothing
     * is consumed and the commit is rejected with an actionbar message.
     */
    private static boolean consumeMaterials(ServerPlayer player, BlockStateRef material, int count) {
        var block = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(material.id()));
        if (block == null) return false;
        var item = block.asItem();
        if (item == net.minecraft.world.item.Items.AIR) return false;

        int have = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() == block) have += stack.getCount();
        }
        if (have < count) {
            player.displayClientMessage(Component.literal(
                    "Need " + count + " × " + item.getDescription().getString() + " (have " + have + ")"), true);
            return false;
        }
        int remaining = count;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining == 0) break;
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() == block) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.getInventory().setChanged();
        return true;
    }
}
