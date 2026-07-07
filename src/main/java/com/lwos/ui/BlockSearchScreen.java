package com.lwos.ui;

import com.lwos.ui.theme.JournalTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal block picker for a palette slot: a search box + a scrollable grid of block icons. Selecting
 * a block assigns it to the target slot via {@link PathStyleEdits} and closes. This is the one place
 * the UI is a real Screen (text input + scrolling want full input capture); the docked panel itself
 * is an overlay.
 */
public final class BlockSearchScreen extends Screen {

    private static final int COLS = 8;
    private static final int CELL = 20;

    private final boolean core;
    private final int slotIndex;
    private EditBox search;
    private final List<Block> filtered = new ArrayList<>();
    private int scroll = 0;

    public BlockSearchScreen(boolean core, int slotIndex) {
        super(Component.literal("Pick a block"));
        this.core = core;
        this.slotIndex = slotIndex;
    }

    @Override
    protected void init() {
        int gridX = width / 2 - (COLS * CELL) / 2;
        // Unbordered: the journal search-field frame is drawn behind it in render().
        search = new EditBox(font, gridX + 5, height / 2 - 85, COLS * CELL - 10, 10, Component.literal("search"));
        search.setBordered(false);
        search.setTextColor(0x3B2E1E);
        search.setHint(Component.literal("search blocks..."));
        search.setResponder(s -> refilter());
        addRenderableWidget(search);
        setInitialFocus(search);
        refilter();
    }

    private void refilter() {
        filtered.clear();
        String q = search.getValue().toLowerCase();
        for (Block b : ForgeRegistries.BLOCKS) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
            if (id == null) continue;
            if (q.isEmpty() || id.toString().contains(q)) filtered.add(b);
            if (filtered.size() > 512) break; // cap for a responsive grid
        }
        scroll = 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int gridX = width / 2 - (COLS * CELL) / 2;
        int gridY = height / 2 - 66;
        int rows = 8;
        // Parchment sheet behind everything: search field, grid, hint line.
        JournalTheme.blitNineSlice(g, JournalTheme.PANEL, JournalTheme.PANEL_TEX, JournalTheme.PANEL_TEX,
                0, 0, JournalTheme.PANEL_TEX, JournalTheme.PANEL_TEX, JournalTheme.PANEL_INSET,
                gridX - 12, height / 2 - 102, COLS * CELL + 24, 12 + 36 + rows * CELL + 26);
        // Ink-ruled search field frame (the EditBox itself is unbordered).
        JournalTheme.blitWidgetNineSlice(g, JournalTheme.FIELD_U, JournalTheme.FIELD_V,
                JournalTheme.FIELD_W, JournalTheme.FIELD_H, JournalTheme.FIELD_INSET,
                gridX, height / 2 - 90, COLS * CELL, 18);
        super.render(g, mouseX, mouseY, partial);
        for (int i = 0; i < rows * COLS && (i + scroll * COLS) < filtered.size(); i++) {
            Block b = filtered.get(i + scroll * COLS);
            int cx = gridX + (i % COLS) * CELL;
            int cy = gridY + (i / COLS) * CELL;
            JournalTheme.blitWidgetNineSlice(g, JournalTheme.SLOT_U, JournalTheme.SLOT_V,
                    JournalTheme.SLOT_W, JournalTheme.SLOT_H, JournalTheme.SLOT_INSET,
                    cx, cy, CELL - 2, CELL - 2);
            boolean hover = mouseX >= cx && mouseX <= cx + CELL - 2 && mouseY >= cy && mouseY <= cy + CELL - 2;
            if (hover) {
                g.fill(cx, cy, cx + CELL - 2, cy + 1, JournalTheme.WAX);
                g.fill(cx, cy + CELL - 3, cx + CELL - 2, cy + CELL - 2, JournalTheme.WAX);
                g.fill(cx, cy, cx + 1, cy + CELL - 2, JournalTheme.WAX);
                g.fill(cx + CELL - 3, cy, cx + CELL - 2, cy + CELL - 2, JournalTheme.WAX);
            }
            g.renderItem(new ItemStack(b), cx + 2, cy + 2);
        }
        g.drawString(font, "Scroll to page · click to select · Esc to cancel",
                gridX, gridY + rows * CELL + 4, JournalTheme.INK_FADED, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int gridX = width / 2 - (COLS * CELL) / 2;
        int gridY = height / 2 - 66;
        int rows = 8;
        for (int i = 0; i < rows * COLS && (i + scroll * COLS) < filtered.size(); i++) {
            int cx = gridX + (i % COLS) * CELL;
            int cy = gridY + (i / COLS) * CELL;
            if (mx >= cx && mx <= cx + CELL - 2 && my >= cy && my <= cy + CELL - 2) {
                Block b = filtered.get(i + scroll * COLS);
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
                if (id != null) {
                    if (core) PathStyleEdits.setCoreSlotBlock(slotIndex, id.toString());
                    else PathStyleEdits.setEdgeSlotBlock(slotIndex, id.toString());
                }
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, (filtered.size() + COLS - 1) / COLS - 8);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; } // keep the world (and preview) live behind the picker
}
