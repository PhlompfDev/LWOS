package com.lwos.client;

import com.lwos.tool.ToolManager;
import com.lwos.tool.ToolType;
import com.mojang.blaze3d.platform.InputConstants;
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

        // Dim the screen slightly behind the wheel.
        g.fill(cx - RADIUS - 40, cy - RADIUS - 20, cx + RADIUS + 40, cy + RADIUS + 20, 0x80000000);

        ToolType[] tools = ToolType.values();
        int n = tools.length;
        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i / n) - Math.PI / 2; // first tool at top
            int x = cx + (int) Math.round(Math.cos(angle) * RADIUS);
            int y = cy + (int) Math.round(Math.sin(angle) * RADIUS);
            boolean sel = tools[i] == tm.selected();
            int color = sel ? 0xFF00FF00 : (0xFF000000 | tools[i].color());
            String label = (sel ? "> " : "") + tools[i].displayName() + (sel ? " <" : "");
            g.drawCenteredString(font, label, x, y - font.lineHeight / 2, color);
        }
        g.drawCenteredString(font, "Scroll to change tool", cx, cy - 4, 0xFFFFFFFF);
    }
}
