package com.lwos.ui;

import com.lwos.config.PathStyle;
import com.lwos.config.StyleManager;
import com.lwos.tool.ToolManager;
import com.lwos.ui.components.BlockSlotWidget;
import com.lwos.ui.components.SliderWidget;
import com.lwos.ui.theme.JournalTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Docked, right-hand "Path Style" panel drawn as a Forge HUD overlay (not a Screen) so the world
 * stays interactive behind it. Journal look: parchment nine-slice background, ink-colored text,
 * wax-red accent. Read-only by default; {@link PathStylePanelInput} frees the cursor on Left-Ctrl
 * and drives the widget rectangles this class republishes each frame via {@link #currentLayout()}.
 */
public final class PathStylePanel implements IGuiOverlay {
    public static final PathStylePanel INSTANCE = new PathStylePanel();

    private static final int PANEL_W = 210;
    private static final int PAD = 10;

    /** Widget rectangles for this frame, consumed by the input handler for hit-testing. */
    public record SlotRect(boolean core, int index, int x, int y) { }
    public record SliderRect(String target, int index, int x, int y, int w, double min, double max) { }
    public record ChipRect(String preset, boolean save, int x, int y, int w, int h) { }
    public record Layout(List<SlotRect> slots, List<SliderRect> sliders, List<ChipRect> chips) { }

    private static volatile Layout currentLayout = new Layout(List.of(), List.of(), List.of());

    private PathStylePanel() { }

    public static Layout currentLayout() { return currentLayout; }

    /** True when the gui-scaled cursor is horizontally within the docked panel strip (right edge). */
    public static boolean cursorOverPanel() {
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        return mx >= mc.getWindow().getGuiScaledWidth() - PANEL_W - 8;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !ToolManager.get().isEnabled() || !PathStylePanelState.isOpen()) return;

        Font font = mc.font;
        PathStyle s = StyleManager.active();
        int x = screenWidth - PANEL_W - 8;
        int top = 30;
        int bottom = screenHeight - 30;
        JournalTheme.blitNineSlice(g, JournalTheme.PANEL, JournalTheme.PANEL_TEX, JournalTheme.PANEL_TEX,
                0, 0, JournalTheme.PANEL_TEX, JournalTheme.PANEL_TEX, JournalTheme.PANEL_INSET,
                x, top, PANEL_W, bottom - top);

        int scroll = PathStylePanelState.scrollOffset();
        // Reserve a fixed footer strip that never scrolls.
        int bodyBottom = bottom - 16;
        g.enableScissor(x, top, x + PANEL_W, bodyBottom);

        List<SlotRect> slots = new ArrayList<>();
        List<SliderRect> sliders = new ArrayList<>();
        List<ChipRect> chips = new ArrayList<>();
        int cx = x + PAD;
        int contentTop = top + PAD;
        int y = contentTop - scroll;

        // Preset chip bar
        List<String> presets = StyleManager.presetNames();
        int chipX = cx;
        for (String name : presets) {
            int w = font.width(name) + 10;
            if (chipX + w > x + PANEL_W - 40) break; // keep the Save chip room
            JournalTheme.blitWidgetNineSlice(g, JournalTheme.CHIP_U, JournalTheme.CHIP_V,
                    JournalTheme.CHIP_W, JournalTheme.CHIP_H, JournalTheme.CHIP_INSET, chipX, y, w, 14);
            g.drawString(font, name, chipX + 5, y + 3, JournalTheme.INK, false);
            chips.add(new ChipRect(name, false, chipX, y, w, 14));
            chipX += w + 4;
        }
        int saveW = font.width("+ Save") + 8;
        int saveX = x + PANEL_W - PAD - saveW;
        JournalTheme.blitWidgetNineSlice(g, JournalTheme.CHIP_SEL_U, JournalTheme.CHIP_SEL_V,
                JournalTheme.CHIP_W, JournalTheme.CHIP_H, JournalTheme.CHIP_INSET, saveX, y, saveW, 14);
        g.drawString(font, "+ Save", saveX + 4, y + 3, JournalTheme.WAX, false);
        chips.add(new ChipRect(null, true, saveX, y, saveW, 14));
        y += 24;

        // Core Materials
        y = section(g, font, cx, y, x + PANEL_W - PAD, "CORE MATERIALS");
        y = paletteRows(g, font, cx, y, x + PANEL_W - PAD, s.core(), true, slots, sliders, 0.0, 10.0);
        // "add" slot at core end
        slots.add(new SlotRect(true, s.core().size(), cx, y));
        drawAddSlot(g, cx, y); y += BlockSlotWidget.SIZE + 6;

        // Outskirts
        y = section(g, font, cx, y, x + PANEL_W - PAD, "OUTSKIRTS · EDGE BLEND");
        y = paletteRows(g, font, cx, y, x + PANEL_W - PAD, s.edge(), false, slots, sliders, 0.0, 10.0);
        slots.add(new SlotRect(false, s.edge().size(), cx, y));
        drawAddSlot(g, cx, y); y += BlockSlotWidget.SIZE + 6;
        // Feather depth (inward blend), in blocks
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Blend depth", s.blendDepth(),
                "blend", 0, 0, 8, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge coverage", s.edgeCoverage(),
                "coverage", 0, 0, 1, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge cluster", s.edgeClusterSize(),
                "edgecluster", 0, 1, 16, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge reach", s.edgeReach(),
                "reach", 0, 0, 6, sliders);

        // Advanced
        y = section(g, font, cx, y, x + PANEL_W - PAD, "ADVANCED");
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge erosion", s.edgeErosion(),
                "erosion", 0, 0, 8, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge feature size", s.edgeFeatureSize(),
                "feature", 0, 1, 16, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Core protect", s.coreProtect(),
                "core", 0, 0, 1, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Core cluster size", s.defaultClusterSize(),
                "cluster", 0, 1, 20, sliders);

        g.disableScissor();
        // Content height = last y (scrolled) + scroll back to unscrolled space, minus the visible top.
        int contentHeight = (y + scroll) - contentTop;
        int visibleHeight = bodyBottom - contentTop;
        PathStylePanelState.setMaxScroll(Math.max(0, contentHeight - visibleHeight));

        // Leather scrollbar strap along the right edge, only when the body overflows.
        int maxScroll = PathStylePanelState.maxScroll();
        if (maxScroll > 0) {
            int trackTop = contentTop;
            int trackH = bodyBottom - contentTop;
            int thumbH = Math.max(20, trackH * visibleHeight / (visibleHeight + maxScroll));
            int thumbY = trackTop + (trackH - thumbH) * PathStylePanelState.scrollOffset() / maxScroll;
            JournalTheme.blitV3(g, JournalTheme.STRAP_U, JournalTheme.STRAP_V, JournalTheme.STRAP_W,
                    JournalTheme.STRAP_H, JournalTheme.STRAP_CAP, x + PANEL_W - 5, thumbY, 4, thumbH);
        }

        // Footer hint
        g.drawString(font, "Hold Ctrl to edit · Look + P to pick",
                cx, bottom - 12, JournalTheme.INK_FADED, false);

        currentLayout = new Layout(List.copyOf(slots), List.copyOf(sliders), List.copyOf(chips));
    }

    private int section(GuiGraphics g, Font font, int x, int y, int right, String label) {
        JournalTheme.blitTiledH(g, JournalTheme.STITCH_U, JournalTheme.STITCH_V,
                JournalTheme.STITCH_W, JournalTheme.STITCH_H, x, y, right - x);
        g.drawString(font, label, x, y + 5, JournalTheme.INK_FADED, false);
        return y + 18;
    }

    private int paletteRows(GuiGraphics g, Font font, int x, int y, int right, List<PathStyle.Entry> entries,
                            boolean core, List<SlotRect> slots, List<SliderRect> sliders, double wMin, double wMax) {
        for (int i = 0; i < entries.size(); i++) {
            PathStyle.Entry e = entries.get(i);
            new BlockSlotWidget(x, y).render(g, iconFor(e.id()),
                    PathStylePanelState.isEditing() && PathStylePanelState.activeSlot() == i);
            slots.add(new SlotRect(core, i, x, y));
            int sx = x + BlockSlotWidget.SIZE + 6;
            int sw = right - sx;
            new SliderWidget(sx, y + 12, sw, wMin, wMax, e.weight()).render(g, 0, 0);
            sliders.add(new SliderRect(core ? "coreWeight" : "edgeWeight", i, sx, y + 12, sw, wMin, wMax));
            y += BlockSlotWidget.SIZE + 6;
        }
        return y;
    }

    private int labeledSlider(GuiGraphics g, Font font, int x, int y, int w, String label, double value,
                              String target, int index, double min, double max, List<SliderRect> sliders) {
        g.drawString(font, label, x, y, JournalTheme.INK_FADED, false);
        g.drawString(font, String.format("%.2f", value), x + w - 28, y, JournalTheme.WAX, false);
        int sy = y + 11;
        new SliderWidget(x, sy, w, min, max, value).render(g, 0, 0);
        sliders.add(new SliderRect(target, index, x, sy, w, min, max));
        return sy + 12;
    }

    private void drawAddSlot(GuiGraphics g, int x, int y) {
        JournalTheme.blitWidgetNineSlice(g, JournalTheme.SLOT_U, JournalTheme.SLOT_V,
                JournalTheme.SLOT_W, JournalTheme.SLOT_H, JournalTheme.SLOT_INSET,
                x, y, BlockSlotWidget.SIZE, BlockSlotWidget.SIZE);
        g.drawString(Minecraft.getInstance().font, "+", x + 12, y + 10, JournalTheme.INK_FADED, false);
    }

    private ItemStack iconFor(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        return block == null ? ItemStack.EMPTY : new ItemStack(block);
    }
}
