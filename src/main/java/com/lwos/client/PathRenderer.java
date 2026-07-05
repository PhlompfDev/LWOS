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
    private static final double SAMPLE_SPACING = 0.25; // blocks between curve samples
    private static final double GIZMO = 0.15;          // half-size of a control-point box
    // WorldView.surfaceHeight() returns the topmost SOLID block's index (e.g. 64), so the visible
    // surface (top face) is at index+1. Lift the drawn curve/ribbon just above it so RenderType.lines
    // (depth-tested) isn't occluded by the ground block it would otherwise be buried inside.
    private static final double SURFACE_DRAW_OFFSET = 1.05;

    private PathRenderer() { }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

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

        // EditPlan preview: translucent block mesh of the blocks that will be placed (M3, spec §5).
        com.lwos.plan.EditPlan plan = com.lwos.plan.EditPlanBuilder.build(
                positions, SAMPLE_SPACING, width, ForgeWorldView.INSTANCE);
        PreviewRenderer.render(plan, ps, buffers);

        ps.popPose();
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(RenderType.translucent());
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
