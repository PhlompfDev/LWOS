package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldViewBlockIdTest {
    /** Flat fake world: solid "minecraft:stone" at y <= 10, air above. */
    private static final WorldView FLAT = new WorldView() {
        @Override public int surfaceHeight(int x, int z) { return 10; }
        @Override public String surfaceBlockId(int x, int z) { return "minecraft:stone"; }
    };

    @Test
    void defaultBlockIdAt_airAboveSurface() {
        assertEquals("minecraft:air", FLAT.blockIdAt(0, 11, 0));
        assertEquals("minecraft:air", FLAT.blockIdAt(5, 200, -3));
    }

    @Test
    void defaultBlockIdAt_surfaceIdAtAndBelowSurface() {
        assertEquals("minecraft:stone", FLAT.blockIdAt(0, 10, 0));
        assertEquals("minecraft:stone", FLAT.blockIdAt(0, -20, 0));
    }
}
