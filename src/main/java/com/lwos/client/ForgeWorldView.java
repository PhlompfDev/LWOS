package com.lwos.client;

import com.lwos.geometry.WorldView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Adapts the live client level to the pure geometry.WorldView interface (spec §4.3 boundary rule). */
public final class ForgeWorldView implements WorldView {
    public static final ForgeWorldView INSTANCE = new ForgeWorldView();

    private ForgeWorldView() { }

    @Override
    public int surfaceHeight(int x, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return 64;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }
}
