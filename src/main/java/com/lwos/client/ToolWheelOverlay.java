package com.lwos.client;

import com.lwos.tool.ToolManager;
import com.lwos.tool.ToolType;
import com.lwos.ui.theme.JournalTheme;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.lwjgl.glfw.GLFW;

public final class ToolWheelOverlay implements IGuiOverlay {
    public static final ToolWheelOverlay INSTANCE = new ToolWheelOverlay();

    private static final int ICON_RADIUS = 40;

    private ToolWheelOverlay() { }

    private static boolean altHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        ToolManager tm = ToolManager.get();
        if (mc.screen != null || !tm.isEnabled() || !altHeld()) return;

        Font font = mc.font;
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        // Parchment compass disc (128x128, 1:1) — no dim box; the disc carries its own contrast.
        RenderSystem.enableBlend();
        g.blit(JournalTheme.TOOL_WHEEL, cx - 64, cy - 64, 128, 128, 0.0F, 0.0F, 128, 128, 128, 128);
        RenderSystem.disableBlend();

        ToolType[] tools = ToolType.values();
        int n = tools.length;
        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i / n) - Math.PI / 2; // first tool at top
            int x = cx + (int) Math.round(Math.cos(angle) * ICON_RADIUS);
            int y = cy + (int) Math.round(Math.sin(angle) * ICON_RADIUS);
            if (tools[i] == tm.selected()) {
                JournalTheme.blitRegion(g, JournalTheme.SEL_RING_U, JournalTheme.SEL_RING_V,
                        JournalTheme.SEL_RING_SIZE, JournalTheme.SEL_RING_SIZE, x - 12, y - 12);
            }
            JournalTheme.blitToolIcon(g, tools[i].iconIndex(), x - 8, y - 8);
        }
        String name = tm.selected().displayName();
        g.drawString(font, name, cx - font.width(name) / 2, cy + 6, JournalTheme.WAX, false);
    }
}
