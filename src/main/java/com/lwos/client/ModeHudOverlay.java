package com.lwos.client;

import com.lwos.plan.TerrainMode;
import com.lwos.tool.ToolManager;
import com.lwos.ui.theme.JournalTheme;
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

    private ModeHudOverlay() { }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        ToolManager tm = ToolManager.get();
        if (mc.screen != null) return;

        String text;
        if (tm.isTerrainToolActive()) {
            // Brush readout (spec §1): e.g. "Smooth · r6".
            com.lwos.tool.TerrainBrushTool brush = tm.currentBrush();
            text = brush.op().displayName() + " · r" + brush.radius();
        } else if (tm.isPathToolActive()) {
            text = "Mode: " + tm.currentPath().terrainMode().displayName();
        } else {
            return;
        }

        Font font = mc.font;
        int x = MARGIN;
        int y = MARGIN;
        int w = font.width(text);
        JournalTheme.blitNineSlice(g, JournalTheme.HUD_PLATE, JournalTheme.HUD_TEX_W, JournalTheme.HUD_TEX_H,
                0, 0, JournalTheme.HUD_TEX_W, JournalTheme.HUD_TEX_H, JournalTheme.HUD_INSET,
                x - PAD, y - PAD, w + 2 * PAD, font.lineHeight + 2 * PAD);
        g.drawString(font, text, x, y, JournalTheme.INK, false);
    }
}
