package com.lwos.ui;

import com.lwos.LwosMod;
import com.lwos.tool.ToolManager;
import com.lwos.ui.components.BlockSlotWidget;
import com.lwos.ui.components.SliderWidget;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Drives the docked panel while keeping the world live. Holding Left-Ctrl (with the panel open in
 * builder mode) frees the mouse cursor so the user can click the panel's sliders, slots and preset
 * chips; releasing Ctrl re-grabs the mouse and returns to first-person building. Click/drag events
 * are hit-tested against {@link PathStylePanel#currentLayout()}.
 */
@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PathStylePanelInput {

    private static int draggingSlider = -1; // index into the current layout's slider list, or -1
    private static final int SCROLL_STEP = 14; // gui-scaled px per wheel notch

    private PathStylePanelInput() { }

    private static boolean panelActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.level != null && ToolManager.get().isEnabled()
                && PathStylePanelState.isOpen();
    }

    private static boolean ctrlHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_CONTROL);
    }

    /** Reflect Ctrl-hold into editing state, grabbing/releasing the OS cursor on the transition. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean wantEditing = panelActive() && ctrlHeld();
        if (wantEditing && !PathStylePanelState.isEditing()) {
            PathStylePanelState.setEditing(true);
            mc.mouseHandler.releaseMouse();       // show cursor, stop mouse-look
        } else if (!wantEditing && PathStylePanelState.isEditing()) {
            PathStylePanelState.setEditing(false);
            draggingSlider = -1;
            if (mc.screen == null) mc.mouseHandler.grabMouse(); // back to building
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!PathStylePanelState.isEditing()) return;
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        PathStylePanel.Layout layout = PathStylePanel.currentLayout();
        boolean down = event.getAction() == GLFW.GLFW_PRESS;
        int button = event.getButton();

        if (!down) { // release
            draggingSlider = -1;
            return;
        }

        // Right- or middle-click: deletions. Chips take priority over slots (they never overlap, but
        // check chips first for clarity), and the "+ Save" chip / "add" slot are inert here.
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            for (PathStylePanel.ChipRect c : layout.chips()) {
                if (!c.save() && hit(c.x(), c.y(), c.w(), c.h(), mx, my)) {
                    com.lwos.config.StyleManager.deletePreset(c.preset());
                    event.setCanceled(true);
                    return;
                }
            }
            for (PathStylePanel.SlotRect sr : layout.slots()) {
                if (hitSlot(sr, mx, my)) {
                    if (sr.core()) PathStyleEdits.removeCoreSlot(sr.index());
                    else PathStyleEdits.removeEdgeSlot(sr.index());
                    // Keep the highlighted slot index in range after a removal (spec Task 1).
                    PathStylePanelState.setActiveSlot(-1);
                    event.setCanceled(true);
                    return;
                }
            }
            return;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        // Preset chips
        for (PathStylePanel.ChipRect c : layout.chips()) {
            if (hit(c.x(), c.y(), c.w(), c.h(), mx, my)) {
                if (c.save()) savePreset();
                else com.lwos.config.StyleManager.loadPreset(c.preset())
                        .ifPresent(com.lwos.config.StyleManager::setActive);
                event.setCanceled(true);
                return;
            }
        }
        // Slots: select the slot; clicking opens the search modal for that slot
        for (int i = 0; i < layout.slots().size(); i++) {
            PathStylePanel.SlotRect sr = layout.slots().get(i);
            if (hitSlot(sr, mx, my)) {
                PathStylePanelState.setActiveSlot(sr.index());
                mc.setScreen(new BlockSearchScreen(sr.core(), sr.index()));
                event.setCanceled(true);
                return;
            }
        }
        // Sliders: begin drag
        for (int i = 0; i < layout.sliders().size(); i++) {
            PathStylePanel.SliderRect s = layout.sliders().get(i);
            if (mx >= s.x() && mx <= s.x() + s.w() && my >= s.y() - 4 && my <= s.y() + 10) {
                draggingSlider = i;
                applySlider(s, mx);
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Scroll the panel body while the cursor is freed for editing (Ctrl held). */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!PathStylePanelState.isEditing()) return;
        // The terrain brush's Ctrl+scroll owns the wheel while the cursor is off-panel (spec §1
        // precedence rule); path-tool behavior (panel scrolls from anywhere while editing) is kept.
        if (ToolManager.get().isTerrainToolActive() && !PathStylePanel.cursorOverPanel()) return;
        double delta = event.getScrollDelta();
        if (delta == 0) return;
        // Wheel up (delta > 0) reveals content above -> smaller offset.
        PathStylePanelState.addScroll((int) Math.round(-delta * SCROLL_STEP));
        event.setCanceled(true);
    }

    private static boolean hit(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static boolean hitSlot(PathStylePanel.SlotRect sr, double mx, double my) {
        return hit(sr.x(), sr.y(), BlockSlotWidget.SIZE, BlockSlotWidget.SIZE, mx, my);
    }

    @SubscribeEvent
    public static void onClientTickDrag(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || draggingSlider < 0) return;
        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
            draggingSlider = -1;
            return;
        }
        PathStylePanel.Layout layout = PathStylePanel.currentLayout();
        if (draggingSlider >= layout.sliders().size()) { draggingSlider = -1; return; }
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        applySlider(layout.sliders().get(draggingSlider), mx);
    }

    private static void applySlider(PathStylePanel.SliderRect s, double mx) {
        double value = SliderWidget.valueFromPixel((int) Math.round(mx), s.x(), s.w(), s.min(), s.max());
        switch (s.target()) {
            case "coreWeight"  -> PathStyleEdits.setCoreWeight(s.index(), value);
            case "edgeWeight"  -> PathStyleEdits.setEdgeWeight(s.index(), value);
            case "blend"       -> PathStyleEdits.setBlendDepth(value);
            case "coverage"    -> PathStyleEdits.setEdgeCoverage(value);
            case "edgecluster" -> PathStyleEdits.setEdgeClusterSize(value);
            case "reach"       -> PathStyleEdits.setEdgeReach(value);
            case "cluster"     -> PathStyleEdits.setClusterSize(value);
            case "erosion"     -> PathStyleEdits.setEdgeErosion(value);
            case "feature"     -> PathStyleEdits.setEdgeFeatureSize(value);
            case "core"        -> PathStyleEdits.setCoreProtect(value);
            default -> { }
        }
    }

    private static void savePreset() {
        // Name presets by count for now; a rename dialog is future polish. Persists the working style.
        String name = "style_" + (com.lwos.config.StyleManager.presetNames().size() + 1);
        com.lwos.config.StyleManager.savePreset(name, com.lwos.config.StyleManager.active());
    }
}
