package com.lwos.brush;

import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeightFieldTest {

    /** Ground height = x + 100*z so every column is distinguishable; surfaceHeight is a trap. */
    private static final class CoordWorldView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 999; } // must never be consulted
        @Override
        public int groundHeight(int x, int z) { return x + 100 * z; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    @Test
    void samplesDiscPlusOneBlockRing() {
        HeightField f = HeightField.sample(new CoordWorldView(), 10, -5, 3);
        // The sampled square spans [c-radius-1, c+radius+1] on both axes.
        assertEquals((10 - 4) + 100 * (-5 - 4), f.height(10 - 4, -5 - 4));
        assertEquals((10 + 4) + 100 * (-5 + 4), f.height(10 + 4, -5 + 4));
        assertEquals(10 + 100 * -5, f.height(10, -5));
    }

    @Test
    void samplesTheGroundMaskNotTheRawSurface() {
        HeightField f = HeightField.sample(new CoordWorldView(), 0, 0, 2);
        assertEquals(0, f.height(0, 0)); // groundHeight, not the 999 surfaceHeight
    }

    @Test
    void withProducesAnIndependentCopy() {
        HeightField f = HeightField.sample(new CoordWorldView(), 0, 0, 2);
        int[] work = f.copyHeights();
        f.set(work, 1, 1, 42);
        HeightField g = f.with(work);
        assertEquals(42, g.height(1, 1));
        assertEquals(1 + 100, f.height(1, 1)); // original untouched: fields are immutable snapshots
    }
}
