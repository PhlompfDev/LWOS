package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.client.anim.Spring;
import com.lwos.tool.ToolManager;
import com.lwos.tool.ToolType;
import com.lwos.tool.WheelMath;
import com.lwos.ui.theme.JournalTheme;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Alt-held radial tool wheel (spec: wheel-redesign-ui-tweens). While visible the OS
 * cursor is released (the PathStylePanelInput pattern): hover a sector to preview it,
 * click to select, or release Alt to select the hovered tool. Alt+scroll cycling still
 * works via {@link LwosInputHandler}.
 *
 * <p>Motion (spec §2): the disc scale/fades in with the house spring on open, hovered
 * icons spring-scale up, and the wax selection ring sweeps the shortest arc to the
 * selected sector instead of teleporting.
 */
@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ToolWheelOverlay implements IGuiOverlay {
    public static final ToolWheelOverlay INSTANCE = new ToolWheelOverlay();

    private static final int ICON_RADIUS = 40;
    private static final float HOVER_SCALE = 1.25f;
    private static final float OPEN_START_SCALE = 0.85f;

    // Wheel session state (client thread only; volatile not needed — render + tick + input
    // all run on the client thread).
    private static boolean active = false;
    private static int hovered = -1;

    // Motion state.
    private static final Spring openSpring = new Spring(Spring.ZETA, Spring.HZ);
    private static final Spring ringAngle = new Spring(Spring.ZETA, Spring.HZ);
    private static final Spring[] iconScale = new Spring[ToolType.values().length];
    private static long lastFrameNanos = 0;

    static {
        for (int i = 0; i < iconScale.length; i++) {
            iconScale[i] = new Spring(Spring.ZETA, Spring.HZ);
            iconScale[i].snapTo(1f);
        }
    }

    private ToolWheelOverlay() { }

    private static boolean altHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static boolean shouldShow() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.screen == null && ToolManager.get().isEnabled() && altHeld();
    }

    /** Cursor grab/release + Alt-release selection live on the tick, not the render pass. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean want = shouldShow();
        if (want && !active) {
            active = true;
            hovered = -1;
            openSpring.snapTo(OPEN_START_SCALE);
            openSpring.setTarget(1f);
            ringAngle.snapTo((float) WheelMath.sectorAngle(ToolManager.get().selected().ordinal(),
                    ToolType.values().length));
            mc.mouseHandler.releaseMouse();      // show cursor, stop mouse-look
        } else if (!want && active) {
            active = false;
            if (hovered >= 0) ToolManager.get().select(ToolType.values()[hovered]); // release commits
            hovered = -1;
            if (mc.screen == null) mc.mouseHandler.grabMouse(); // back to building
        }
    }

    /** Click selects the hovered sector (wheel stays open while Alt is held). */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!active) return;
        event.setCanceled(true); // the wheel owns the mouse while open
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) return;
        if (hovered >= 0) ToolManager.get().select(ToolType.values()[hovered]);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        ToolManager tm = ToolManager.get();
        if (!active || mc.screen != null || !tm.isEnabled()) return;

        Font font = mc.font;
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        ToolType[] tools = ToolType.values();
        int n = tools.length;

        // Hover pick from the released cursor (window px -> gui px).
        double mouseX = mc.mouseHandler.xpos() * screenWidth / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * screenHeight / mc.getWindow().getScreenHeight();
        hovered = WheelMath.sectorAt(mouseX - cx, mouseY - cy, n);

        // Advance motion with real frame time.
        float dt = frameDt();
        openSpring.update(dt);
        for (int i = 0; i < n; i++) {
            iconScale[i].setTarget(i == hovered ? HOVER_SCALE : 1f);
            iconScale[i].update(dt);
        }
        float ringTarget = ringAngle.target()
                + (float) WheelMath.shortestArc(ringAngle.target(),
                        WheelMath.sectorAngle(tm.selected().ordinal(), n));
        ringAngle.setTarget(ringTarget); // unwrapped: spring sweeps the short arc
        ringAngle.update(dt);

        float open = openSpring.value();
        float alpha = Math.min(1f, Math.max(0f, (open - OPEN_START_SCALE) / (1f - OPEN_START_SCALE)));

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(open, open, 1f);
        g.pose().translate(-cx, -cy, 0);

        // Parchment compass disc (128x128, 1:1) — no dim box; the disc carries its own contrast.
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(JournalTheme.TOOL_WHEEL, cx - 64, cy - 64, 128, 128, 0.0F, 0.0F, 128, 128, 128, 128);

        // Wax selection ring sweeps to the selected sector.
        double ringA = ringAngle.value();
        int rx = cx + (int) Math.round(Math.cos(ringA) * ICON_RADIUS);
        int ry = cy + (int) Math.round(Math.sin(ringA) * ICON_RADIUS);
        JournalTheme.blitRegion(g, JournalTheme.SEL_RING_U, JournalTheme.SEL_RING_V,
                JournalTheme.SEL_RING_SIZE, JournalTheme.SEL_RING_SIZE, rx - 12, ry - 12);

        // Icons, hovered one spring-scaled around its own center.
        for (int i = 0; i < n; i++) {
            double angle = WheelMath.sectorAngle(i, n);
            int x = cx + (int) Math.round(Math.cos(angle) * ICON_RADIUS);
            int y = cy + (int) Math.round(Math.sin(angle) * ICON_RADIUS);
            float s = iconScale[i].value();
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(s, s, 1f);
            g.pose().translate(-x, -y, 0);
            JournalTheme.blitToolIcon(g, tools[i].iconIndex(), x - 8, y - 8);
            g.pose().popPose();
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        // Center label: hovered name in ink, else the selected name in wax.
        String name = hovered >= 0 ? tools[hovered].displayName() : tm.selected().displayName();
        int color = hovered >= 0 && tools[hovered] != tm.selected() ? JournalTheme.INK : JournalTheme.WAX;
        g.drawString(font, name, cx - font.width(name) / 2, cy + 6, color, false);

        g.pose().popPose();
    }

    private static float frameDt() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 1f / 60f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return dt;
    }
}
