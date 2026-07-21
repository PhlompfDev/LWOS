package com.lwos.apply;

import com.lwos.geometry.WorldView;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-side {@link WorldView} over a {@link ServerLevel} (spec §4.3 apply boundary).
 *
 * <p>Delegates to {@link SurfaceScan}, the same ground-finder the client's {@code ForgeWorldView}
 * uses, so the server rebuilds a byte-identical {@code EditPlan} from the same control points
 * instead of trusting a client payload.
 */
public final class ServerWorldView implements WorldView {
    private final ServerLevel level;

    public ServerWorldView(ServerLevel level) {
        this.level = level;
    }

    @Override
    public int surfaceHeight(int x, int z) {
        return SurfaceScan.solidSurfaceHeight(level, x, z);
    }

    @Override
    public int groundHeight(int x, int z) {
        return SurfaceScan.groundHeight(level, x, z);
    }

    @Override
    public String surfaceBlockId(int x, int z) {
        return SurfaceScan.surfaceBlockId(level, x, z);
    }

    @Override
    public String blockIdAt(int x, int y, int z) {
        return SurfaceScan.blockId(level, x, y, z);
    }
}
