package com.lwos.ui;

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
        search = new EditBox(font, gridX, height / 2 - 90, COLS * CELL, 18, Component.literal("search"));
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
        super.render(g, mouseX, mouseY, partial);
        int gridX = width / 2 - (COLS * CELL) / 2;
        int gridY = height / 2 - 66;
        int rows = 8;
        for (int i = 0; i < rows * COLS && (i + scroll * COLS) < filtered.size(); i++) {
            Block b = filtered.get(i + scroll * COLS);
            int cx = gridX + (i % COLS) * CELL;
            int cy = gridY + (i / COLS) * CELL;
            g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, 0x22FFFFFF);
            g.renderItem(new ItemStack(b), cx + 2, cy + 2);
        }
        g.drawString(font, "Scroll to page · click to select · Esc to cancel",
                gridX, gridY + rows * CELL + 4, 0xFFAAB2C0, false);
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
