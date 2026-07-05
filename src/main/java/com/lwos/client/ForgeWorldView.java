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
        // MOTION_BLOCKING (not *_NO_LEAVES): only Usage.CLIENT heightmaps (WORLD_SURFACE, MOTION_BLOCKING)
        // are maintained on the client. Querying a server-only type (e.g. MOTION_BLOCKING_NO_LEAVES)
        // returns the min build height on the client, snapping the whole path far underground.
        // getHeight() returns the first non-blocking Y above the column, so -1 gives the top solid block.
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    }
}
