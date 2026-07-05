package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.geometry.PathNode;
import com.lwos.geometry.Vec3d;
import com.lwos.tool.ToolManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PathRenderer {
    // Shared with the server rebuild so client preview and server placement produce the same plan.
    private static final double SAMPLE_SPACING = com.lwos.plan.EditPlanBuilder.DEFAULT_SPACING;
    private static final double GIZMO = 0.15;          // half-size of a control-point box
    private static final double HANDLE = 0.30;         // half-size of a width-handle box
    // WorldView.surfaceHeight() returns the topmost SOLID block's index (e.g. 64), so the visible
    // surface (top face) is at index+1. Lift the drawn curve/ribbon just above it so RenderType.lines
    // (depth-tested) isn't occluded by the ground block it would otherwise be buried inside.
    private static final double SURFACE_DRAW_OFFSET = 1.05;

    // Width-handle state, published each frame for LwosInputHandler to hit-test and drag against.
    // Positions are in world space (the drawn, terrain-lifted ribbon midpoint).
    public static volatile boolean handlesVisible = false;
    public static volatile Vec3d handleCenter;  // ribbon centerline at the path midpoint
    public static volatile Vec3d handleLeft;    // left edge handle box center
    public static volatile Vec3d handleRight;   // right edge handle box center
    public static volatile Vec3d handleNormal;  // unit horizontal normal pointing toward the left edge

    private PathRenderer() { }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        handlesVisible = false; // cleared here; re-published below only when the ribbon is drawn

        ToolManager tm = ToolManager.get();
        if (!tm.isPathToolActive()) return;

        List<PathNode> nodes = tm.currentPath().nodes();
        if (nodes.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f mat = ps.last().pose();
        Matrix3f nor = ps.last().normal();

        // Control-point gizmos (white boxes).
        for (PathNode node : nodes) {
            Vec3d p = node.position();
            AABB box = new AABB(p.x() - GIZMO, p.y() - GIZMO, p.z() - GIZMO,
                                p.x() + GIZMO, p.y() + GIZMO, p.z() + GIZMO);
            LevelRenderer.renderLineBox(ps, lines, box, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        // Terrain-hugging curve + width ribbon (M2: replaces the M1 raw-interpolated curve).
        List<Vec3d> positions = new ArrayList<>(nodes.size());
        for (PathNode node : nodes) positions.add(node.position());

        double width = tm.currentPath().width();
        List<com.lwos.geometry.PathSample> raw =
                com.lwos.geometry.PathSampler.sampleWithWidth(positions, SAMPLE_SPACING, width);
        List<com.lwos.geometry.PathSample> grounded =
                com.lwos.geometry.TerrainSampler.snapToSurface(raw, ForgeWorldView.INSTANCE, SURFACE_DRAW_OFFSET);

        List<Vec3d> centerline = new ArrayList<>(grounded.size());
        for (com.lwos.geometry.PathSample s : grounded) centerline.add(s.position());
        for (int i = 0; i < centerline.size() - 1; i++) {
            addLine(lines, mat, nor, centerline.get(i), centerline.get(i + 1), 0, 255, 0);
        }

        com.lwos.geometry.PathRibbon.Edges edges = com.lwos.geometry.PathRibbon.compute(grounded);
        for (int i = 0; i < edges.left().size() - 1; i++) {
            addLine(lines, mat, nor, edges.left().get(i), edges.left().get(i + 1), 255, 255, 0);
            addLine(lines, mat, nor, edges.right().get(i), edges.right().get(i + 1), 255, 255, 0);
        }

        // Width handles: draggable magenta gizmos on the left/right ribbon edges at the path midpoint.
        int mid = grounded.size() / 2;
        Vec3d center = centerline.get(mid);
        Vec3d leftH = edges.left().get(mid);
        Vec3d rightH = edges.right().get(mid);
        drawHandleBox(ps, lines, leftH);
        drawHandleBox(ps, lines, rightH);

        double hnx = leftH.x() - center.x();
        double hnz = leftH.z() - center.z();
        double hlen = Math.sqrt(hnx * hnx + hnz * hnz);
        handleCenter = center;
        handleLeft = leftH;
        handleRight = rightH;
        handleNormal = hlen > 1e-9 ? new Vec3d(hnx / hlen, 0, hnz / hlen) : new Vec3d(1, 0, 0);
        handlesVisible = true;

        // EditPlan preview: translucent block mesh of the blocks that will be placed (M3, spec §5).
        // Built with the active TerrainMode so the preview matches what the server will place — including
        // the red carve outlines of CUT_AND_FILL (M4).
        com.lwos.plan.EditPlan plan = com.lwos.plan.EditPlanBuilder.build(
                positions, SAMPLE_SPACING, width, ForgeWorldView.INSTANCE, tm.currentPath().terrainMode());
        PreviewRenderer.render(plan, ps, buffers);

        ps.popPose();
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(RenderType.translucent());
    }

    private static void drawHandleBox(PoseStack ps, VertexConsumer lines, Vec3d p) {
        AABB box = new AABB(p.x() - HANDLE, p.y() - HANDLE, p.z() - HANDLE,
                            p.x() + HANDLE, p.y() + HANDLE, p.z() + HANDLE);
        LevelRenderer.renderLineBox(ps, lines, box, 1.0f, 0.0f, 1.0f, 1.0f); // magenta
    }

    private static void addLine(VertexConsumer c, Matrix4f mat, Matrix3f nor, Vec3d a, Vec3d b, int r, int g, int b2) {
        float nx = (float) (b.x() - a.x());
        float ny = (float) (b.y() - a.y());
        float nz = (float) (b.z() - a.z());
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return;
        nx /= len; ny /= len; nz /= len;
        c.vertex(mat, (float) a.x(), (float) a.y(), (float) a.z())
                .color(r, g, b2, 255).normal(nor, nx, ny, nz).endVertex();
        c.vertex(mat, (float) b.x(), (float) b.y(), (float) b.z())
                .color(r, g, b2, 255).normal(nor, nx, ny, nz).endVertex();
    }
}
