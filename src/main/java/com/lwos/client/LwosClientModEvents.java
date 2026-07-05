package com.lwos.client;

import com.lwos.LwosMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LwosMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LwosClientModEvents {
    private LwosClientModEvents() { }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(LwosKeyMappings.TOGGLE_MODE);
        event.register(LwosKeyMappings.DELETE_POINT);
        event.register(LwosKeyMappings.CANCEL_PATH);
        event.register(LwosKeyMappings.WIDTH_UP);
        event.register(LwosKeyMappings.WIDTH_DOWN);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("tool_wheel", ToolWheelOverlay.INSTANCE);
    }
}
