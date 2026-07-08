package com.lwos.client;

import com.lwos.apply.SurfaceScan;
import com.lwos.geometry.WorldView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Adapts the live client level to the pure geometry.WorldView interface (spec §4.3 boundary rule). */
public final class ForgeWorldView implements WorldView {
    public static final ForgeWorldView INSTANCE = new ForgeWorldView();

    private ForgeWorldView() { }

    @Override
    public int surfaceHeight(int x, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return 64;
        // Shared with ServerWorldView so client preview and server placement agree. MOTION_BLOCKING
        // (not *_NO_LEAVES) is the only tree-aware heightmap maintained on the client; SurfaceScan
        // then walks down through the canopy to real ground.
        return SurfaceScan.solidSurfaceHeight(level, x, z);
    }

    @Override
    public int groundHeight(int x, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return 64;
        return SurfaceScan.groundHeight(level, x, z);
    }

    @Override
    public String surfaceBlockId(int x, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return "minecraft:air";
        return SurfaceScan.surfaceBlockId(level, x, z);
    }
}
