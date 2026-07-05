package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.apply.net.EditRequestPacket;
import com.lwos.geometry.PathNode;
import com.lwos.geometry.Vec3d;
import com.lwos.tool.PathTool;
import com.lwos.tool.ToolManager;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LwosInputHandler {
    /** Look-ray miss distance (blocks) within which a use-click grabs a width handle. */
    private static final double HANDLE_PICK_RADIUS = 0.5;

    /** Max look-ray distance (blocks) for placing a path point on distant terrain. */
    private static final double MAX_PLACE_DISTANCE = 96.0;

    private LwosInputHandler() { }

    private static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.screen == null && mc.player != null;
    }

    private static boolean altHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!inWorld()) return;
        ToolManager tm = ToolManager.get();
        while (LwosKeyMappings.TOGGLE_MODE.consumeClick()) tm.toggleEnabled();
        if (!tm.isEnabled()) return;
        while (LwosKeyMappings.TOGGLE_STYLE_PANEL.consumeClick()) com.lwos.ui.PathStylePanelState.toggleOpen();
        while (LwosKeyMappings.UNDO.consumeClick()) {
            LwosMod.CHANNEL.sendToServer(new com.lwos.apply.net.UndoRequestPacket());
        }
        while (LwosKeyMappings.PICK_BLOCK.consumeClick()) pickBlockFromWorld();
        while (LwosKeyMappings.DELETE_POINT.consumeClick()) tm.currentPath().deleteLast();
        while (LwosKeyMappings.CANCEL_PATH.consumeClick()) tm.currentPath().clear();
        while (LwosKeyMappings.WIDTH_UP.consumeClick()) {
            tm.currentPath().setWidth(tm.currentPath().width() + 1.0);
        }
        while (LwosKeyMappings.WIDTH_DOWN.consumeClick()) {
            tm.currentPath().setWidth(tm.currentPath().width() - 1.0);
        }
        while (LwosKeyMappings.TOGGLE_TERRAIN_MODE.consumeClick()) tm.currentPath().toggleTerrainMode();
        while (LwosKeyMappings.COMMIT.consumeClick()) commitPath(tm);
    }

    /** Assigns the block the player is looking at to the panel's active palette slot. */
    private static void pickBlockFromWorld() {
        if (!com.lwos.ui.PathStylePanelState.isOpen()) return;
        int slot = com.lwos.ui.PathStylePanelState.activeSlot();
        if (slot < 0) return;
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        net.minecraft.core.BlockPos bp = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
        net.minecraft.world.level.block.state.BlockState bs = Minecraft.getInstance().level.getBlockState(bp);
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(bs.getBlock());
        if (id == null) return;
        com.lwos.ui.PathStyleEdits.setCoreSlotBlock(slot, id.toString());
    }

    /** Sends the current path to the server to be placed, then clears the local session. */
    private static void commitPath(ToolManager tm) {
        if (!tm.isPathToolActive()) return;
        PathTool path = tm.currentPath();
        List<PathNode> nodes = path.nodes();
        if (!nodes.isEmpty()) {
            List<Vec3d> points = new ArrayList<>(nodes.size());
            for (PathNode node : nodes) points.add(node.position());
            LwosMod.CHANNEL.sendToServer(new EditRequestPacket(
                    points, path.width(), path.terrainMode(), com.lwos.config.StyleManager.active().toJson()));
        }
        path.clear();
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!inWorld()) return;
        ToolManager tm = ToolManager.get();
        if (!tm.isEnabled() || !altHeld()) return;
        double delta = event.getScrollDelta();
        if (delta != 0) {
            tm.cycle(delta > 0 ? 1 : -1);
            event.setCanceled(true); // don't move the hotbar selection
        }
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !inWorld()) return;
        ToolManager tm = ToolManager.get();
        if (!tm.isPathToolActive()) return;
        PathTool path = tm.currentPath();

        // Already dragging a width handle (use key held): swallow repeats, don't place points.
        if (path.isDraggingWidth()) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        // Aiming at a width handle: grab it instead of placing a point.
        if (aimingAtHandle()) {
            path.beginWidthDrag();
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }

        // Extended-reach placement: cast the look ray up to MAX_PLACE_DISTANCE and place on the first
        // solid block, so points can be dropped on distant terrain (matching how width resizing
        // already reaches).
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 end = eye.add(look.x * MAX_PLACE_DISTANCE, look.y * MAX_PLACE_DISTANCE, look.z * MAX_PLACE_DISTANCE);
        net.minecraft.world.level.ClipContext cc = new net.minecraft.world.level.ClipContext(
                eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player);
        net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(cc);
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 loc = hit.getLocation();
            path.addPoint(new Vec3d(loc.x, loc.y, loc.z));
            event.setSwingHand(false);
            event.setCanceled(true); // suppress vanilla use while placing points
        }
    }

    /** Drives an active width-handle drag: sets width from where the player looks; releases on key-up. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ToolManager tm = ToolManager.get();
        PathTool path = tm.currentPath();
        if (!path.isDraggingWidth()) return;

        Minecraft mc = Minecraft.getInstance();
        boolean stillHeld = mc.options.keyUse.isDown();
        if (!inWorld() || !tm.isPathToolActive() || !PathRenderer.handlesVisible || !stillHeld) {
            path.endWidthDrag();
            return;
        }
        Vec3d center = PathRenderer.handleCenter;
        Vec3d normal = PathRenderer.handleNormal;
        if (center == null || normal == null) { path.endWidthDrag(); return; }

        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        if (Math.abs(look.y) < 1e-4) return;             // looking flat: no plane intersection this tick
        double t = (center.y() - eye.y) / look.y;
        if (t <= 0) return;                               // handle plane is behind the camera
        double dx = (eye.x + look.x * t) - center.x();
        double dz = (eye.z + look.z * t) - center.z();
        double halfWidth = Math.abs(dx * normal.x() + dz * normal.z()); // signed distance along the ribbon normal
        path.setWidth(halfWidth * 2.0);                   // PathTool clamps to [MIN_WIDTH, MAX_WIDTH]
    }

    /** True when the player's look ray passes within {@link #HANDLE_PICK_RADIUS} of either width handle. */
    private static boolean aimingAtHandle() {
        if (!PathRenderer.handlesVisible) return false;
        Vec3d left = PathRenderer.handleLeft;
        Vec3d right = PathRenderer.handleRight;
        if (left == null || right == null) return false;
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        double best = Math.min(rayPointDistance(eye, look, left), rayPointDistance(eye, look, right));
        return best <= HANDLE_PICK_RADIUS;
    }

    /** Shortest distance from a point to a ray (origin {@code eye}, unit direction {@code dir}). */
    private static double rayPointDistance(Vec3 eye, Vec3 dir, Vec3d point) {
        double ex = point.x() - eye.x, ey = point.y() - eye.y, ez = point.z() - eye.z;
        double t = ex * dir.x + ey * dir.y + ez * dir.z; // projection onto the ray
        if (t < 0) t = 0;                                 // clamp to the ray origin (no picking behind camera)
        double cx = eye.x + dir.x * t - point.x();
        double cy = eye.y + dir.y * t - point.y();
        double cz = eye.z + dir.z * t - point.z();
        return Math.sqrt(cx * cx + cy * cy + cz * cz);
    }
}
