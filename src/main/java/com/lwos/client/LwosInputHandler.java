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

    /** True only when the local player is the authorized builder (see {@link com.lwos.LwosAccess}). */
    private static boolean isModUser() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && com.lwos.LwosAccess.isAllowed(mc.player.getGameProfile().getName());
    }

    private static boolean altHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static boolean ctrlHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!inWorld() || !isModUser()) return;
        ToolManager tm = ToolManager.get();
        while (LwosKeyMappings.TOGGLE_MODE.consumeClick()) tm.toggleEnabled();
        if (!tm.isEnabled()) return;
        while (LwosKeyMappings.TOGGLE_STYLE_PANEL.consumeClick()) com.lwos.ui.PathStylePanelState.toggleOpen();
        while (LwosKeyMappings.UNDO.consumeClick()) {
            LwosMod.CHANNEL.sendToServer(new com.lwos.apply.net.UndoRequestPacket());
            tm.currentBrush().bumpRevision(); // ground may change under the brush preview
            tm.currentShape().bumpRevision(); // ... and under the shape preview
        }
        while (LwosKeyMappings.REDO.consumeClick()) {
            LwosMod.CHANNEL.sendToServer(new com.lwos.apply.net.RedoRequestPacket());
            tm.currentBrush().bumpRevision();
            tm.currentShape().bumpRevision();
        }
        while (LwosKeyMappings.PICK_BLOCK.consumeClick()) pickBlockFromWorld();
        while (LwosKeyMappings.DELETE_POINT.consumeClick()) tm.currentPath().deleteLast();
        while (LwosKeyMappings.REDO_POINT.consumeClick()) tm.currentPath().redoPoint();
        while (LwosKeyMappings.CANCEL_PATH.consumeClick()) {
            if (tm.isShapeToolActive()) tm.currentShape().clear();
            else tm.currentPath().clear();
        }
        while (LwosKeyMappings.WIDTH_UP.consumeClick()) {
            tm.currentPath().setWidth(tm.currentPath().width() + 1.0);
        }
        while (LwosKeyMappings.WIDTH_DOWN.consumeClick()) {
            tm.currentPath().setWidth(tm.currentPath().width() - 1.0);
        }
        while (LwosKeyMappings.TOGGLE_TERRAIN_MODE.consumeClick()) {
            // Same key, per-tool meaning: brush op cycle, shape fill cycle, path terrain-mode cycle.
            if (tm.isTerrainToolActive()) tm.currentBrush().cycleOp();
            else if (tm.isShapeToolActive()) tm.currentShape().cycleFill();
            else tm.currentPath().toggleTerrainMode();
        }
        while (LwosKeyMappings.COMMIT.consumeClick()) commitPath(tm);
    }

    /** Drives the shape gesture: anchor clicks accumulate; the final click commits (spec §2). */
    private static void handleShapeClick(ToolManager tm, boolean asBreak) {
        com.lwos.tool.ShapeTool tool = tm.currentShape();
        com.lwos.shape.ShapeMode mode = tm.activeShapeMode();
        Minecraft mc = Minecraft.getInstance();

        if (tool.state() == com.lwos.tool.ShapeTool.State.IDLE) {
            // First anchor: terrain raycast; place-gestures also need a block in hand.
            Vec3 eye = mc.player.getEyePosition(1.0f);
            Vec3 look = mc.player.getViewVector(1.0f);
            com.lwos.plan.GridPos anchor = ShapeRenderer.terrainAnchor(mc, eye, look, asBreak);
            if (anchor == null) return;
            if (!asBreak) {
                com.lwos.plan.BlockStateRef material = heldBlock(mc);
                if (material == null) {
                    mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("Hold a block to build shapes"), true);
                    return;
                }
                tool.setMaterial(material);
            }
            tool.addAnchor(anchor, asBreak);
            if (mode == com.lwos.shape.ShapeMode.WALL) ShapeRenderer.latchWallNormal(look);
            ShapeRenderer.snapSpringsTo(anchor);
            return;
        }

        // Mid-gesture: a click of the opposite intent cancels (ShapeTool enforces it).
        if (asBreak != tool.breakMode()) {
            tool.addAnchor(new com.lwos.plan.GridPos(0, 0, 0), asBreak); // rejected -> clears
            return;
        }
        if (!ShapeRenderer.hasTarget) return; // aiming at nothing valid: click does nothing
        com.lwos.plan.GridPos aim = ShapeRenderer.aimTarget;

        if (tool.isComplete(mode)) {
            // Final click: commit with the live aim as the last anchor.
            List<com.lwos.plan.GridPos> anchors = new ArrayList<>(tool.anchors());
            anchors.add(aim);
            LwosMod.CHANNEL.sendToServer(new com.lwos.apply.net.ShapeRequestPacket(
                    anchors, mode.ordinal(), tool.options().toJson(),
                    tool.material().id(), tool.breakMode()));
            tool.clear();
            tool.bumpRevision(); // world changed under any lingering preview
        } else {
            tool.addAnchor(aim, asBreak); // cube: base corner locked, extrusion begins
        }
    }

    /** BlockStateRef of the main-hand BlockItem, or null when not holding a placeable block. */
    private static com.lwos.plan.BlockStateRef heldBlock(Minecraft mc) {
        net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return null;
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
        return id == null ? null : new com.lwos.plan.BlockStateRef(id.toString());
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
        if (!inWorld() || !isModUser()) return;
        ToolManager tm = ToolManager.get();
        if (!tm.isEnabled()) return;
        double delta = event.getScrollDelta();
        if (delta == 0) return;
        if (altHeld()) {
            tm.cycle(delta > 0 ? 1 : -1);
            event.setCanceled(true); // don't move the hotbar selection
            return;
        }
        // Ctrl+scroll: brush radius (spec §1). Precedence: with the panel open and the cursor
        // over it while Ctrl is held, the panel's Ctrl-edit interaction wins.
        if (ctrlHeld() && tm.isTerrainToolActive()) {
            if (com.lwos.ui.PathStylePanelState.isEditing() && com.lwos.ui.PathStylePanel.cursorOverPanel()) return;
            tm.currentBrush().adjustRadius(delta > 0 ? 1 : -1);
            event.setCanceled(true);
        }
    }

    /** Left-click with the Terrain tool: commit the previewed dab at the targeted ground column (spec §1). */
    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || !inWorld() || !isModUser()) return;
        ToolManager tm = ToolManager.get();
        if (tm.isShapeToolActive()) {
            event.setSwingHand(false);
            event.setCanceled(true); // the shape tool owns left-click — never swing/break vanilla-style
            handleShapeClick(tm, true);
            return;
        }
        if (!tm.isTerrainToolActive()) return;
        event.setSwingHand(false);
        event.setCanceled(true); // the brush owns left-click while active — never break blocks
        if (!BrushRenderer.hasTarget) return; // looking at sky: no preview, click does nothing
        com.lwos.tool.TerrainBrushTool brush = tm.currentBrush();
        LwosMod.CHANNEL.sendToServer(new com.lwos.apply.net.BrushRequestPacket(
                brush.op().ordinal(), BrushRenderer.targetX, BrushRenderer.targetZ, brush.radius()));
        brush.bumpRevision(); // the ground under the preview just changed; force a rebuild
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !inWorld() || !isModUser()) return;
        ToolManager tm = ToolManager.get();
        if (tm.isShapeToolActive()) {
            handleShapeClick(tm, false);
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
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
        if (!isModUser()) return;
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
