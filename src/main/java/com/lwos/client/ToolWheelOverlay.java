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

    private static final int RADIUS = 60;

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

        // Soft dim behind the wheel, then the parchment compass disc (128x128, 1:1).
        g.fill(cx - RADIUS - 40, cy - RADIUS - 20, cx + RADIUS + 40, cy + RADIUS + 20, 0x66000000);
        RenderSystem.enableBlend();
        g.blit(JournalTheme.TOOL_WHEEL, cx - 64, cy - 64, 128, 128, 0.0F, 0.0F, 128, 128, 128, 128);
        RenderSystem.disableBlend();

        ToolType[] tools = ToolType.values();
        int n = tools.length;
        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i / n) - Math.PI / 2; // first tool at top
            int x = cx + (int) Math.round(Math.cos(angle) * RADIUS);
            int y = cy + (int) Math.round(Math.sin(angle) * RADIUS);
            boolean sel = tools[i] == tm.selected();
            int color = sel ? JournalTheme.WAX : JournalTheme.INK;
            String label = (sel ? "> " : "") + tools[i].displayName() + (sel ? " <" : "");
            g.drawString(font, label, x - font.width(label) / 2, y - font.lineHeight / 2, color, false);
        }
        String hint = "Scroll to change tool";
        g.drawString(font, hint, cx - font.width(hint) / 2, cy - 4, JournalTheme.INK_FADED, false);
    }
}
