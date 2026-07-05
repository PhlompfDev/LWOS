package com.lwos.ui;

import com.lwos.config.PathStyle;
import com.lwos.config.StyleManager;
import com.lwos.tool.ToolManager;
import com.lwos.ui.components.BlockSlotWidget;
import com.lwos.ui.components.SliderWidget;
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
 * stays interactive behind it. Frosted-glass look: translucent dark fill, hairline dividers, blue
 * accent. Read-only by default; {@link PathStylePanelInput} frees the cursor on Left-Ctrl and drives
 * the widget rectangles this class republishes each frame via {@link #currentLayout()}.
 */
public final class PathStylePanel implements IGuiOverlay {
    public static final PathStylePanel INSTANCE = new PathStylePanel();

    private static final int PANEL_W = 210;
    private static final int PAD = 10;
    private static final int PANEL_BG   = 0xB0121419; // ~69% alpha dark
    private static final int DIVIDER    = 0x1AFFFFFF;
    private static final int ACCENT     = 0xFF7CD3FF;
    private static final int TEXT       = 0xFFE7EAF0;
    private static final int LABEL      = 0xFFAAB2C0;

    /** Widget rectangles for this frame, consumed by the input handler for hit-testing. */
    public record SlotRect(boolean core, int index, int x, int y) { }
    public record SliderRect(String target, int index, int x, int y, int w, double min, double max) { }
    public record ChipRect(String preset, boolean save, int x, int y, int w, int h) { }
    public record Layout(List<SlotRect> slots, List<SliderRect> sliders, List<ChipRect> chips) { }

    private static volatile Layout currentLayout = new Layout(List.of(), List.of(), List.of());

    private PathStylePanel() { }

    public static Layout currentLayout() { return currentLayout; }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !ToolManager.get().isEnabled() || !PathStylePanelState.isOpen()) return;

        Font font = mc.font;
        PathStyle s = StyleManager.active();
        int x = screenWidth - PANEL_W - 8;
        int top = 30;
        int bottom = screenHeight - 30;
        g.fill(x, top, x + PANEL_W, bottom, PANEL_BG);

        List<SlotRect> slots = new ArrayList<>();
        List<SliderRect> sliders = new ArrayList<>();
        List<ChipRect> chips = new ArrayList<>();
        int cx = x + PAD;
        int y = top + PAD;

        // Preset chip bar
        List<String> presets = StyleManager.presetNames();
        int chipX = cx;
        for (String name : presets) {
            int w = font.width(name) + 10;
            if (chipX + w > x + PANEL_W - 40) break; // keep the Save chip room
            g.fill(chipX, y, chipX + w, y + 14, 0x22FFFFFF);
            g.drawString(font, name, chipX + 5, y + 3, TEXT, false);
            chips.add(new ChipRect(name, false, chipX, y, w, 14));
            chipX += w + 4;
        }
        int saveW = font.width("+ Save") + 8;
        int saveX = x + PANEL_W - PAD - saveW;
        g.fill(saveX, y, saveX + saveW, y + 14, 0x2A7CD3FF);
        g.drawString(font, "+ Save", saveX + 4, y + 3, ACCENT, false);
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
        // Blend amount slider
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Blend amount", s.blendSkirtWidth(),
                "blend", 0, 0, 8, sliders);

        // Advanced
        y = section(g, font, cx, y, x + PANEL_W - PAD, "ADVANCED");
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Cluster size", s.defaultClusterSize(),
                "cluster", 0, 1, 20, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge erosion", s.edgeErosionFactor(),
                "erosion", 0, 0, 4, sliders);
        y = labeledSlider(g, font, cx, y, PANEL_W - 2 * PAD, "Edge noise", s.edgeNoiseScale(),
                "noise", 0, 0.01, 0.3, sliders);

        // Footer hint
        g.drawString(font, "Hold Ctrl to edit · Look + P to pick",
                cx, bottom - 12, LABEL, false);

        currentLayout = new Layout(List.copyOf(slots), List.copyOf(sliders), List.copyOf(chips));
    }

    private int section(GuiGraphics g, Font font, int x, int y, int right, String label) {
        g.fill(x, y, right, y + 1, DIVIDER);
        g.drawString(font, label, x, y + 5, LABEL, false);
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
        g.drawString(font, label, x, y, LABEL, false);
        g.drawString(font, String.format("%.2f", value), x + w - 28, y, ACCENT, false);
        int sy = y + 11;
        new SliderWidget(x, sy, w, min, max, value).render(g, 0, 0);
        sliders.add(new SliderRect(target, index, x, sy, w, min, max));
        return sy + 12;
    }

    private void drawAddSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + BlockSlotWidget.SIZE, y + BlockSlotWidget.SIZE, 0x14FFFFFF);
        g.drawString(Minecraft.getInstance().font, "+", x + 12, y + 10, LABEL, false);
    }

    private ItemStack iconFor(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        return block == null ? ItemStack.EMPTY : new ItemStack(block);
    }
}
