package com.lwos;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(LwosMod.MODID)
public class LwosMod {
    public static final String MODID = "lwos";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LwosMod() {
        LOGGER.info("LWOS Builder Tools loading");
        // Client-side registrations are wired by @Mod.EventBusSubscriber classes
        // added in later tasks (guarded by Dist.CLIENT).
    }
}
