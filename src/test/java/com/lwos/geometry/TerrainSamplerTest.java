package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TerrainSamplerTest {
    private static final double EPS = 1e-9;

    /** A simple sloped "world": height rises by 1 every 2 blocks along x, flat in z. */
    private static final class SlopedWorldView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) {
            return 64 + Math.floorDiv(x, 2);
        }

        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    @Test
    void snapsYToSurfaceHeightPlusOffset() {
        List<PathSample> in = List.of(
                new PathSample(new Vec3d(0.4, 999, 0.2), 3.0),
                new PathSample(new Vec3d(3.9, -50, 0.9), 3.0));
        List<PathSample> out = TerrainSampler.snapToSurface(in, new SlopedWorldView(), 1.0);

        assertEquals(65.0, out.get(0).position().y(), EPS); // 64 + floor(0/2) + 1
        assertEquals(0.4, out.get(0).position().x(), EPS);
        assertEquals(0.2, out.get(0).position().z(), EPS);
        assertEquals(3.0, out.get(0).width(), EPS);

        assertEquals(66.0, out.get(1).position().y(), EPS); // 64 + floor(3/2) + 1 = 64+1+1
    }

    @Test
    void emptyInEmptyOut() {
        assertTrue(TerrainSampler.snapToSurface(List.of(), new SlopedWorldView(), 0.0).isEmpty());
    }
}
