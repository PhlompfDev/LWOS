package com.lwos.apply;

import com.lwos.geometry.WorldView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Server-side {@link WorldView} over a {@link ServerLevel} (spec §4.3 apply boundary).
 *
 * <p>Uses {@code MOTION_BLOCKING} — the same heightmap type the client's
 * {@code ForgeWorldView} queries — so the server rebuilds a byte-identical
 * {@code EditPlan} from the same control points instead of trusting a client payload.
 */
public final class ServerWorldView implements WorldView {
    private final ServerLevel level;

    public ServerWorldView(ServerLevel level) {
        this.level = level;
    }

    @Override
    public int surfaceHeight(int x, int z) {
        // getHeight() returns the first non-blocking Y above the column; -1 gives the top solid block.
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    }
}
