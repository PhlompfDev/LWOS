package com.lwos.client;

import com.lwos.LwosMod;
import com.lwos.geometry.Vec3d;
import com.lwos.tool.ToolManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LwosInputHandler {
    private LwosInputHandler() { }

    private static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.screen == null && mc.player != null;
    }

    private static boolean altHeld() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!inWorld()) return;
        ToolManager tm = ToolManager.get();
        while (LwosKeyMappings.TOGGLE_MODE.consumeClick()) tm.toggleEnabled();
        if (!tm.isEnabled()) return;
        while (LwosKeyMappings.DELETE_POINT.consumeClick()) tm.currentPath().deleteLast();
        while (LwosKeyMappings.CANCEL_PATH.consumeClick()) tm.currentPath().clear();
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!inWorld()) return;
        ToolManager tm = ToolManager.get();
        if (!tm.isEnabled() || !altHeld()) return;
        double delta = event.getScrollDelta();
        if (delta != 0) {
            tm.cycle(delta > 0 ? 1 : -1);
            event.setCanceled(true); // don't move the hotbar selection
        }
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !inWorld()) return;
        ToolManager tm = ToolManager.get();
        if (!tm.isPathToolActive()) return;
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            Vec3 loc = hit.getLocation();
            tm.currentPath().addPoint(new Vec3d(loc.x, loc.y, loc.z));
            event.setSwingHand(false);
            event.setCanceled(true); // suppress vanilla use while placing points
        }
    }
}
