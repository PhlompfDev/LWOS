package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.client.anim.Spring;
import com.lwos.client.anim.SpringVec3;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeMode;
import com.lwos.shape.ShapePlanBuilder;
import com.lwos.tool.ShapeTool;
import com.lwos.tool.ToolManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Live shape preview (spec §5): terrain-raycasts the hover target before the first
 * anchor, construction-plane-aims after it, builds the plan through the debounced cache,
 * and renders it with the spring presentation layer (spec §3): anchor outline bounces in,
 * the mesh's bounds chase the exact extents with the house underdamped spring.
 *
 * <p>The aim target is published via volatile statics (BrushRenderer pattern) so
 * LwosInputHandler commits exactly what the ghost shows.
 */
@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShapeRenderer {
    private static final double MAX_TARGET_DISTANCE = 96.0;

    // Published each frame for LwosInputHandler.
    public static volatile boolean hasTarget = false;
    public static volatile GridPos aimTarget = null;

    /** First-anchor pick: the cell plus the clicked face's axis (circle orientation). */
    public record AnchorHit(GridPos pos, com.lwos.shape.ShapeOptions.Axis faceAxis) { }

    private static final PreviewPlanCache CACHE = new PreviewPlanCache();

    private record ShapeKey(long revision, GridPos aim, int modeOrdinal) { }

    // Spring presentation state (reset whenever the gesture restarts).
    private static final Spring anchorBounce = new Spring(Spring.ZETA, Spring.HZ);
    private static final SpringVec3 springMin = new SpringVec3();
    private static final SpringVec3 springMax = new SpringVec3();
    private static long lastGestureRevision = -1;
    private static long lastFrameNanos = 0;

    private ShapeRenderer() { }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        hasTarget = false;

        ToolManager tm = ToolManager.get();
        if (!tm.isShapeToolActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        ShapeTool tool = tm.currentShape();
        ShapeMode mode = tm.activeShapeMode();
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);

        GridPos aim = computeAim(mc, tool, mode, eye, look);
        if (aim == null) { renderAnchorsOnly(event, tool); return; }
        aimTarget = aim;
        hasTarget = true;

        float dt = frameDt();

        // Hover/anchor bounce: retrigger the scale spring whenever the gesture (re)starts.
        if (tool.revision() != lastGestureRevision) {
            lastGestureRevision = tool.revision();
            anchorBounce.snapTo(0f);
            anchorBounce.setTarget(1f);
        }
        anchorBounce.update(dt);

        if (tool.state() == ShapeTool.State.IDLE) {
            // No gesture yet: just the bouncing hover outline on the would-be anchor.
            renderOutline(event, aim, anchorBounce.value(), false);
            return;
        }

        // Full anchor list = committed anchors + the live aim as the final anchor, PADDED
        // to the mode's clickCount by repeating the aim: mid-gesture cube previews (1 anchor
        // + aim = 2 of 3) render as the base slab instead of crashing the render thread
        // (2026-07-21 playtest fix — ShapePlanBuilder stays strict for commits).
        List<GridPos> anchors = new ArrayList<>(tool.anchors());
        anchors.add(aim);
        while (anchors.size() < mode.clickCount()) anchors.add(aim);
        ShapeKey key = new ShapeKey(tool.revision(), aim, mode.ordinal());
        long now = System.currentTimeMillis();
        if (CACHE.needsRebuild(key, now)) {
            CACHE.accept(key, ShapePlanBuilder.build(
                    anchors, mode, tool.options(), tool.material(), tool.breakMode(),
                    ForgeWorldView.INSTANCE), now);
        }
        EditPlan plan = CACHE.last();
        if (plan == null || plan.isEmpty()) return;

        // Exact bounds of the plan -> spring targets; the drawn mesh chases them.
        double[] bounds = planBounds(plan);
        springMin.setTarget(bounds[0], bounds[1], bounds[2]);
        springMax.setTarget(bounds[3], bounds[4], bounds[5]);
        springMin.update(dt);
        springMax.update(dt);

        PreviewRenderer.Transform t = boundsTransform(bounds);
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PreviewRenderer.render(plan, ps, cam, buffers, t);
        // Outline box at the spring bounds (green place / red break).
        drawBoundsBox(event, tool.breakMode());
        buffers.endBatch(RenderType.lines());
    }

    /** Maps exact plan bounds onto the current spring bounds, per axis. */
    private static PreviewRenderer.Transform boundsTransform(double[] b) {
        double ex = Math.max(b[3] - b[0], 1e-6), ey = Math.max(b[4] - b[1], 1e-6), ez = Math.max(b[5] - b[2], 1e-6);
        double sx = (springMax.x() - springMin.x()) / ex;
        double sy = (springMax.y() - springMin.y()) / ey;
        double sz = (springMax.z() - springMin.z()) / ez;
        return new PreviewRenderer.Transform(sx, sy, sz,
                springMin.x() - b[0] * sx, springMin.y() - b[1] * sy, springMin.z() - b[2] * sz);
    }

    private static double[] planBounds(EditPlan plan) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (GridPos p : plan.changes().keySet()) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x() + 1);
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y() + 1);
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z() + 1);
        }
        return new double[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    private static GridPos computeAim(Minecraft mc, ShapeTool tool, ShapeMode mode, Vec3 eye, Vec3 look) {
        if (tool.state() == ShapeTool.State.IDLE) {
            AnchorHit hit = terrainAnchor(mc, eye, look, tool.breakMode());
            return hit == null ? null : hit.pos();
        }
        GridPos a = tool.anchors().get(0);
        // Cube's third click extrudes along the base plane's fixed axis (a wall base
        // extrudes horizontally); everything else is free 3D aiming.
        if (mode == ShapeMode.CUBE && tool.state() == ShapeTool.State.BASE_DONE) {
            GridPos b = tool.anchors().get(1);
            int axis = com.lwos.shape.ShapeGeometry.rectFixedAxis(a, b);
            return ShapeAim.aimAlongAxis(eye, look, com.lwos.shape.ShapeGeometry.collapseToPlane(a, b), axis);
        }
        return ShapeAim.freeAim(mc, eye, look, a, tool.breakMode());
    }

    /** Terrain raycast for the first anchor: place = face-adjacent pos, break = hit block. */
    public static AnchorHit terrainAnchor(Minecraft mc, Vec3 eye, Vec3 look, boolean forBreak) {
        Vec3 end = eye.add(look.x * MAX_TARGET_DISTANCE, look.y * MAX_TARGET_DISTANCE, look.z * MAX_TARGET_DISTANCE);
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = forBreak ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        com.lwos.shape.ShapeOptions.Axis axis = switch (hit.getDirection().getAxis()) {
            case X -> com.lwos.shape.ShapeOptions.Axis.X;
            case Z -> com.lwos.shape.ShapeOptions.Axis.Z;
            default -> com.lwos.shape.ShapeOptions.Axis.Y;
        };
        return new AnchorHit(new GridPos(pos.getX(), pos.getY(), pos.getZ()), axis);
    }

    /** Bounce-scaled wireframe outline around one block cell. */
    private static void renderOutline(RenderLevelStageEvent event, GridPos p, float scale, boolean red) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double cx = p.x() + 0.5 - cam.x, cy = p.y() + 0.5 - cam.y, cz = p.z() + 0.5 - cam.z;
        double h = 0.5 * Math.max(scale, 0.01) + 0.01;
        AABB box = new AABB(cx - h, cy - h, cz - h, cx + h, cy + h, cz + h);
        LevelRenderer.renderLineBox(ps, buffers.getBuffer(RenderType.lines()), box,
                red ? 1.0f : 0.3f, red ? 0.2f : 1.0f, 0.3f, 1.0f);
        buffers.endBatch(RenderType.lines());
    }

    /** Committed-anchor gizmos while the aim is off-plane (e.g. looking at the sky). */
    private static void renderAnchorsOnly(RenderLevelStageEvent event, ShapeTool tool) {
        for (GridPos p : tool.anchors()) renderOutline(event, p, 1f, tool.breakMode());
    }

    private static void drawBoundsBox(RenderLevelStageEvent event, boolean breakMode) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        AABB box = new AABB(springMin.x() - cam.x, springMin.y() - cam.y, springMin.z() - cam.z,
                            springMax.x() - cam.x, springMax.y() - cam.y, springMax.z() - cam.z);
        LevelRenderer.renderLineBox(ps, buffers.getBuffer(RenderType.lines()), box,
                breakMode ? 1.0f : 0.3f, breakMode ? 0.2f : 1.0f, 0.3f, 1.0f);
    }

    private static float frameDt() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 1f / 60f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return dt;
    }

    /** Snaps the extent springs to the current exact bounds (called when a gesture starts). */
    public static void snapSpringsTo(GridPos p) {
        springMin.snapTo(p.x(), p.y(), p.z());
        springMax.snapTo(p.x() + 1.0, p.y() + 1.0, p.z() + 1.0);
    }
}
