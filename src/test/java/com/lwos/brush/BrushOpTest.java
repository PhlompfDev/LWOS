package com.lwos.brush;

import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrushOpTest {

    /** Flat ground at 70 everywhere. */
    private static final class FlatView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    /** Flat at 70 with a one-column spike (75) at the origin. */
    private static final class SpikeView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 0 && z == 0 ? 75 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    /** Flat at 70 with a one-column pit (65) at the origin. */
    private static final class PitView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 0 && z == 0 ? 65 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    /** Checkerboard 0/8 — every column has the identical 3x3 neighborhood pattern. */
    private static final class CheckerView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return ((x + z) & 1) == 0 ? 0 : 8; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:stone"; }
    }

    @Test
    void cycleOrderIsSmoothMeltFillLift() {
        assertEquals(BrushOp.MELT, BrushOp.SMOOTH.next());
        assertEquals(BrushOp.FILL, BrushOp.MELT.next());
        assertEquals(BrushOp.LIFT, BrushOp.FILL.next());
        assertEquals(BrushOp.SMOOTH, BrushOp.LIFT.next());
    }

    @Test
    void flatGroundIsANoOpForSmoothMeltFill() {
        HeightField before = HeightField.sample(new FlatView(), 0, 0, 6);
        for (BrushOp op : new BrushOp[] { BrushOp.SMOOTH, BrushOp.MELT, BrushOp.FILL }) {
            HeightField after = op.apply(before, 0, 0, 6);
            for (int z = -7; z <= 7; z++) {
                for (int x = -7; x <= 7; x++) {
                    assertEquals(70, after.height(x, z), op + " must not disturb flat ground");
                }
            }
        }
    }

    @Test
    void liftRaisesThePlateauByExactlyOne() {
        HeightField before = HeightField.sample(new FlatView(), 0, 0, 6);
        HeightField after = BrushOp.LIFT.apply(before, 0, 0, 6);
        assertEquals(71, after.height(0, 0));  // center: falloff 1 -> +1
        assertEquals(71, after.height(3, 0));  // dist 3, t=0.5, falloff 0.5 -> rounds to +1
        assertEquals(70, after.height(4, 0));  // dist 4, t=1/3, falloff ~0.26 -> rounds to 0
        assertEquals(70, after.height(6, 0));  // rim: falloff 0
    }

    @Test
    void meltFlattensASpike() {
        HeightField before = HeightField.sample(new SpikeView(), 0, 0, 4);
        HeightField after = BrushOp.MELT.apply(before, 0, 0, 4);
        assertEquals(70, after.height(0, 0)); // pulled down to the max of its 4-neighbors
        assertEquals(70, after.height(1, 0)); // neighbors untouched
    }

    @Test
    void smoothLowersASpikePartially() {
        HeightField before = HeightField.sample(new SpikeView(), 0, 0, 4);
        HeightField after = BrushOp.SMOOTH.apply(before, 0, 0, 4);
        // 3x3 weighted mean at the spike: (4*75 + 2*(4*70) + 4*70)/16 = 71.25 -> 71.
        assertEquals(71, after.height(0, 0));
    }

    @Test
    void fillLeavesASpikeAlone() {
        HeightField before = HeightField.sample(new SpikeView(), 0, 0, 4);
        HeightField after = BrushOp.FILL.apply(before, 0, 0, 4);
        for (int z = -5; z <= 5; z++) {
            for (int x = -5; x <= 5; x++) {
                assertEquals(before.height(x, z), after.height(x, z));
            }
        }
    }

    @Test
    void fillRaisesAPit() {
        HeightField before = HeightField.sample(new PitView(), 0, 0, 4);
        HeightField after = BrushOp.FILL.apply(before, 0, 0, 4);
        assertEquals(70, after.height(0, 0)); // raised to the min of its 4-neighbors
    }

    @Test
    void smoothRaisesAPitPartially() {
        HeightField before = HeightField.sample(new PitView(), 0, 0, 4);
        HeightField after = BrushOp.SMOOTH.apply(before, 0, 0, 4);
        // (4*65 + 2*(4*70) + 4*70)/16 = 68.75 -> 69.
        assertEquals(69, after.height(0, 0));
    }

    @Test
    void meltLeavesAPitAlone() {
        HeightField before = HeightField.sample(new PitView(), 0, 0, 4);
        HeightField after = BrushOp.MELT.apply(before, 0, 0, 4);
        for (int z = -5; z <= 5; z++) {
            for (int x = -5; x <= 5; x++) {
                assertEquals(before.height(x, z), after.height(x, z));
            }
        }
    }

    @Test
    void smoothEditsAreWeakerAtTheRimThanAtTheCenter() {
        HeightField before = HeightField.sample(new CheckerView(), 0, 0, 6);
        HeightField after = BrushOp.SMOOTH.apply(before, 0, 0, 6);
        int centerDelta = Math.abs(after.height(0, 0) - before.height(0, 0));
        int rimDelta = Math.abs(after.height(5, 0) - before.height(5, 0));
        assertTrue(centerDelta > rimDelta,
                "center delta " + centerDelta + " must exceed near-rim delta " + rimDelta);
        assertEquals(4, centerDelta); // full-strength smooth pulls a checker column to the mean
    }

    @Test
    void columnsBeyondTheRadiusAreNeverTouched() {
        for (BrushOp op : BrushOp.values()) {
            HeightField before = HeightField.sample(new CheckerView(), 0, 0, 3);
            HeightField after = op.apply(before, 0, 0, 3);
            // dist > radius within the square, and the +1 sampling ring.
            assertEquals(before.height(3, 3), after.height(3, 3), op + " touched dist>radius"); // dist ~4.24
            assertEquals(before.height(4, 0), after.height(4, 0), op + " touched the ring");
            assertEquals(before.height(-4, -4), after.height(-4, -4), op + " touched the ring corner");
        }
    }
}
