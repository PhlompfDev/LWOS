package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.brush.BrushPlanBuilder;
import com.lwos.plan.EditPlan;
import com.lwos.tool.TerrainBrushTool;
import com.lwos.tool.ToolManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Live hover preview for the Terrain brush (spec §5): raycasts the targeted ground column each
 * frame, builds the dab plan over {@link ForgeWorldView} through the debounced cache, and
 * renders it with the same ghost visuals as the path preview. Preview==apply because this and
 * the server handler call the same {@code BrushPlanBuilder.build(...)}.
 *
 * <p>The target column is published via volatile statics each frame (the {@link PathRenderer}
 * width-handle pattern) so {@link LwosInputHandler}'s click-commit acts on exactly the column
 * the ghost is showing. No target (looking at sky) = no preview, and the click does nothing.
 */
@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BrushRenderer {
    /** Max look-ray distance (blocks), matching the path tool's extended placement reach. */
    private static final double MAX_TARGET_DISTANCE = 96.0;

    // Target column, published each frame for LwosInputHandler to commit against.
    public static volatile boolean hasTarget = false;
    public static volatile int targetX;
    public static volatile int targetZ;

    /** Debounced dab-plan cache — same policy as the path preview: no per-frame rebuilds. */
    private static final PreviewPlanCache CACHE = new PreviewPlanCache();

    /** Rebuild key: crosshair column, op, radius, and the tool revision (bumped on commit/undo). */
    private record BrushKey(int opOrdinal, int radius, int cx, int cz, long revision) { }

    private BrushRenderer() { }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        hasTarget = false; // cleared here; re-published below only when a ground column is targeted

        ToolManager tm = ToolManager.get();
        if (!tm.isTerrainToolActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        // Extended-reach ground pick (same clip as path point placement). Sky = no preview.
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 end = eye.add(look.x * MAX_TARGET_DISTANCE, look.y * MAX_TARGET_DISTANCE, look.z * MAX_TARGET_DISTANCE);
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        targetX = pos.getX();
        targetZ = pos.getZ();
        hasTarget = true;

        TerrainBrushTool brush = tm.currentBrush();
        BrushKey key = new BrushKey(brush.op().ordinal(), brush.radius(), targetX, targetZ, brush.revision());
        long now = System.currentTimeMillis();
        if (CACHE.needsRebuild(key, now)) {
            CACHE.accept(key, BrushPlanBuilder.build(
                    brush.op(), targetX, targetZ, brush.radius(), ForgeWorldView.INSTANCE), now);
        }
        EditPlan plan = CACHE.last();
        if (plan == null || plan.isEmpty()) return;

        // Same ghost visuals as the path preview. The pose stack must stay the RAW camera-space
        // stack (PreviewRenderer subtracts the camera in double — the far-from-origin lesson);
        // the REMOVE carve outlines ride the shared lines batch, flushed here because
        // PathRenderer skips entirely while a non-path tool is selected.
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PreviewRenderer.render(plan, ps, cam, buffers);
        buffers.endBatch(RenderType.lines());
    }
}
