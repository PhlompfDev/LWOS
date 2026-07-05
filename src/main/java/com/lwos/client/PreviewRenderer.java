package com.lwos.client;

import com.lwos.plan.BlockStateRef;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
     * Draws every {@link PlannedChange} in {@code plan} as a translucent unit block.
     * The caller is responsible for pushing/popping the world-space PoseStack and for
     * flushing the {@link RenderType#translucent()} batch afterwards.
     */
    public static void render(EditPlan plan, PoseStack ps, MultiBufferSource buffers) {
        if (plan.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        VertexConsumer base = buffers.getBuffer(RenderType.translucent());
        VertexConsumer translucent = new ForcedAlpha(base, PREVIEW_ALPHA);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        for (PlannedChange change : plan.changes().values()) {
            // REMOVE cells carry air (nothing to draw as a block); outline them in red so the player
            // sees exactly which blocks CUT_AND_FILL will carve away (spec §5 preview: REMOVE = red).
            if (change.kind() == ChangeKind.REMOVE) {
                renderCarveOutline(change.pos(), ps, lines);
                continue;
            }

            BlockState state = resolve(change.state());
            if (state.isAir()) continue;

            GridPos pos = change.pos();
            ps.pushPose();
            ps.translate(pos.x(), pos.y(), pos.z());
            // Lift the preview clear of the terrain it overlays: +0.125 on Y so a 15/16-height path
            // block's top face emerges above a full block, plus a tiny xz offset + upscale so the
            // side faces don't z-fight with coplanar solid blocks.
            ps.translate(-0.005, 0.125, -0.005);
            ps.scale(1.01f, 1.01f, 1.01f);
            BakedModel model = blockRenderer.getBlockModel(state);
            blockRenderer.getModelRenderer().renderModel(
                    ps.last(), translucent, state, model,
                    1.0f, 1.0f, 1.0f,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, RenderType.translucent());
            ps.popPose();
        }
    }

    /** Draws a red wireframe box at {@code pos} marking a block scheduled for removal. */
    private static void renderCarveOutline(GridPos pos, PoseStack ps, VertexConsumer lines) {
        AABB box = new AABB(
                pos.x() + CARVE_INSET, pos.y() + CARVE_INSET, pos.z() + CARVE_INSET,
                pos.x() + 1 - CARVE_INSET, pos.y() + 1 - CARVE_INSET, pos.z() + 1 - CARVE_INSET);
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
