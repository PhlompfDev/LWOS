package com.lwos.client;

import com.lwos.plan.TerrainMode;
import com.lwos.tool.ToolManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Persistent HUD readout of the active {@link TerrainMode} while the Path tool is in use, so the player
 * always knows whether a commit will drape over the terrain or cut/fill through it (M4 UI). Shown only in
 * builder mode with the Path tool active and no screen open.
 */
public final class ModeHudOverlay implements IGuiOverlay {
    public static final ModeHudOverlay INSTANCE = new ModeHudOverlay();

    private static final int MARGIN = 6;
    private static final int PAD = 4;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BG_COLOR = 0x90000000;

    private ModeHudOverlay() { }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        ToolManager tm = ToolManager.get();
        if (mc.screen != null || !tm.isPathToolActive()) return;

        Font font = mc.font;
        TerrainMode mode = tm.currentPath().terrainMode();
        String text = "Mode: " + mode.displayName();

        int x = MARGIN;
        int y = MARGIN;
        int w = font.width(text);
        g.fill(x - PAD, y - PAD, x + w + PAD, y + font.lineHeight + PAD, BG_COLOR);
        g.drawString(font, text, x, y, TEXT_COLOR);
    }
}
