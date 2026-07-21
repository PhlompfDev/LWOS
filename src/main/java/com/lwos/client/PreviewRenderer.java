package com.lwos.client;

import com.lwos.plan.BlockStateRef;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Renders an {@link EditPlan} as a batched, translucent block mesh (M3, spec §5 preview).
 * Replaces the M2 wireframe footprint boxes with the actual blocks that will be placed.
 *
 * <p>Pure {@link BlockStateRef} ids are resolved to Forge {@link BlockState}s here — the
 * {@code plan} package never touches Minecraft types.
 */
public final class PreviewRenderer {
    /** Preview alpha (0-255) applied to every block quad so the terrain shows through. */
    private static final int PREVIEW_ALPHA = 150;
    /** Inset of the red carve outline from the block edges, so stacked REMOVE boxes stay individually readable. */
    private static final double CARVE_INSET = 0.02;

    private PreviewRenderer() { }

    /**
     * Draws every {@link PlannedChange} in {@code plan} as a translucent unit block. Block quads
     * are drawn immediately (own Tesselator buffer, own setup/draw/teardown) rather than through
     * {@code buffers}; only the REMOVE carve outlines go through the caller's shared
     * {@link RenderType#lines()} batch. The caller flushes the lines batch afterwards.
     *
     * <p>{@code ps} must be the raw camera-space PoseStack (camera at the origin), NOT pre-translated
     * by {@code -cam}: each block is positioned by translating {@code (pos - cam)} computed in
     * {@code double}. Baking absolute world coordinates into the float model-view matrix (translate
     * by {@code -cam} then by an absolute {@code pos}) loses precision far from the world origin —
     * near ±few-million the ULP approaches half a block, collapsing the unit quads into untextured
     * white slivers (the "preview turns into white silhouettes far from spawn" bug). Subtracting in
     * double keeps the values the matrix sees small, so the texture holds at any distance.
     */
    /** Per-axis affine presentation map (spring layer, spec §3): p' = p * s + o. Identity = exact plan. */
    public record Transform(double sx, double sy, double sz, double ox, double oy, double oz) {
        public static final Transform IDENTITY = new Transform(1, 1, 1, 0, 0, 0);
        public double mapX(double x) { return x * sx + ox; }
        public double mapY(double y) { return y * sy + oy; }
        public double mapZ(double z) { return z * sz + oz; }
    }

    public static void render(EditPlan plan, PoseStack ps, Vec3 cam, MultiBufferSource buffers) {
        render(plan, ps, cam, buffers, Transform.IDENTITY);
    }

    /** As {@link #render(EditPlan, PoseStack, Vec3, MultiBufferSource)} but with a presentation transform. */
    public static void render(EditPlan plan, PoseStack ps, Vec3 cam, MultiBufferSource buffers, Transform t) {
        if (plan.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        // Block quads are drawn through a dedicated Tesselator buffer with an immediate draw call,
        // NOT through the shared MultiBufferSource: by the time this fires (AFTER_PARTICLES), vanilla's
        // own RenderType.translucent() batch for terrain has already been built and flushed earlier in
        // the frame, and re-acquiring/ending that same shared buffer this late left our quads
        // reduced to outlines on screen (root cause of the "wireframe-only preview" bug) despite
        // correct vertex/UV data reaching the buffer. A private buffer + explicit setup/draw/teardown
        // sidesteps whatever shared state the late re-use was corrupting.
        RenderType translucentType = RenderType.translucent();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        ForcedAlpha translucent = new ForcedAlpha(builder, PREVIEW_ALPHA);

        for (PlannedChange change : plan.changes().values()) {
            // REMOVE cells carry air (nothing to draw as a block); outline them in red so the player
            // sees exactly which blocks CUT_AND_FILL will carve away (spec §5 preview: REMOVE = red).
            if (change.kind() == ChangeKind.REMOVE) {
                renderCarveOutline(change.pos(), cam, ps, lines, t);
                continue;
            }

            BlockState state = resolve(change.state());
            if (state.isAir()) continue;

            GridPos pos = change.pos();
            ps.pushPose();
            // Camera-relative offset computed in double (see render()'s javadoc): pos is an int world
            // coordinate, cam a double — the subtraction happens before the float cast in translate().
            ps.translate(t.mapX(pos.x()) - cam.x, t.mapY(pos.y()) - cam.y, t.mapZ(pos.z()) - cam.z);
            // Lift the preview clear of the terrain it overlays: +0.125 on Y so a 15/16-height path
            // block's top face emerges above a full block, plus a tiny xz offset + upscale so the
            // side faces don't z-fight with coplanar solid blocks.
            ps.translate(-0.005, 0.125, -0.005);
            ps.scale(1.01f * (float) t.sx(), 1.01f * (float) t.sy(), 1.01f * (float) t.sz());
            BakedModel model = blockRenderer.getBlockModel(state);
            blockRenderer.getModelRenderer().renderModel(
                    ps.last(), translucent, state, model,
                    1.0f, 1.0f, 1.0f,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, RenderType.translucent());
            ps.popPose();
        }

        translucentType.setupRenderState();
        BufferUploader.drawWithShader(builder.end());
        translucentType.clearRenderState();
    }

    /**
     * Draws a red wireframe box at {@code pos} marking a block scheduled for removal. Built in
     * camera-relative coordinates ({@code pos - cam}) to match the camera-space {@code ps} — same
     * precision reasoning as the block quads in {@link #render}.
     */
    private static void renderCarveOutline(GridPos pos, Vec3 cam, PoseStack ps, VertexConsumer lines, Transform t) {
        double x0 = t.mapX(pos.x()) - cam.x, y0 = t.mapY(pos.y()) - cam.y, z0 = t.mapZ(pos.z()) - cam.z;
        double x1 = t.mapX(pos.x() + 1) - cam.x, y1 = t.mapY(pos.y() + 1) - cam.y, z1 = t.mapZ(pos.z() + 1) - cam.z;
        AABB box = new AABB(
                x0 + CARVE_INSET, y0 + CARVE_INSET, z0 + CARVE_INSET,
                x1 - CARVE_INSET, y1 - CARVE_INSET, z1 - CARVE_INSET);
        LevelRenderer.renderLineBox(ps, lines, box, 1.0f, 0.15f, 0.15f, 0.9f);
    }

    private static BlockState resolve(BlockStateRef ref) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ref.id()));
        return block == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                             : block.defaultBlockState();
    }

    /**
     * Delegating {@link VertexConsumer} that forces a fixed alpha on every vertex color.
     * Block models write full-opacity color through {@code putBulkData}; by overriding the
     * int {@code color} sink (which the default {@code putBulkData}/{@code color(float...)}
     * both route through) we tint the whole mesh translucent without touching model data.
     */
    private static final class ForcedAlpha implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;
        int vertexCount = 0;

        ForcedAlpha(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color(r, g, b, alpha);
            return this;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            vertexCount++;
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            delegate.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
