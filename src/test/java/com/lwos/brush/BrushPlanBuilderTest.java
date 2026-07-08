package com.lwos.brush;

import com.lwos.geometry.WorldView;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrushPlanBuilderTest {

    /** Flat grass ground at 70. */
    private static final class FlatView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    /** Flat at 70 with a one-column stone spike (75) at the origin. */
    private static final class SpikeView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 0 && z == 0 ? 75 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) {
            return x == 0 && z == 0 ? "minecraft:stone" : "minecraft:grass_block";
        }
    }

    /** Flat at 70 with a one-column sand pit (65) at the origin. */
    private static final class PitView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 0 && z == 0 ? 65 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) {
            return x == 0 && z == 0 ? "minecraft:sand" : "minecraft:grass_block";
        }
    }

    /**
     * A "tree": the raw surface at (2,0) is a 90-high canopy, but the ground mask says 70.
     * The pit at the origin is the thing being filled; the tree column must never be edited
     * relative to its canopy height (spec §7 case 6 — the FAWE lesson).
     */
    private static final class TreeView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return x == 2 && z == 0 ? 90 : 70; }
        @Override
        public int groundHeight(int x, int z) { return x == 0 && z == 0 ? 65 : 70; }
        @Override
        public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
    }

    @Test
    void flatGroundYieldsAnEmptyPlanForSmoothMeltFill() {
        for (BrushOp op : new BrushOp[] { BrushOp.SMOOTH, BrushOp.MELT, BrushOp.FILL }) {
            assertTrue(BrushPlanBuilder.build(op, 0, 0, 6, new FlatView()).isEmpty(),
                    op + " on flat ground must be a no-op");
        }
    }

    @Test
    void liftPlacesAOneBlockPlateauOfTheSurfaceBlock() {
        EditPlan plan = BrushPlanBuilder.build(BrushOp.LIFT, 0, 0, 6, new FlatView());
        assertFalse(plan.isEmpty());
        for (PlannedChange change : plan.changes().values()) {
            assertEquals(ChangeKind.PLACE, change.kind());
            assertEquals(71, change.pos().y());
            assertEquals("minecraft:grass_block", change.state().id());
        }
        assertTrue(plan.changes().containsKey(new GridPos(0, 71, 0)));  // center raised
        assertTrue(plan.changes().containsKey(new GridPos(3, 71, 0)));  // plateau edge (falloff 0.5)
        assertFalse(plan.changes().containsKey(new GridPos(4, 71, 0))); // soft edge: no raise
    }

    @Test
    void meltingASpikeEmitsRemoveToAir() {
        EditPlan plan = BrushPlanBuilder.build(BrushOp.MELT, 0, 0, 4, new SpikeView());
        assertEquals(5, plan.size()); // 71..75 carved
        for (int y = 71; y <= 75; y++) {
            PlannedChange change = plan.changes().get(new GridPos(0, y, 0));
            assertNotNull(change, "carve expected at y=" + y);
            assertEquals(ChangeKind.REMOVE, change.kind());
            assertEquals("minecraft:air", change.state().id());
        }
    }

    @Test
    void fillingAPitStacksTheColumnsOwnSurfaceBlock() {
        EditPlan plan = BrushPlanBuilder.build(BrushOp.FILL, 0, 0, 4, new PitView());
        assertEquals(5, plan.size()); // 66..70 filled
        for (int y = 66; y <= 70; y++) {
            PlannedChange change = plan.changes().get(new GridPos(0, y, 0));
            assertNotNull(change, "fill expected at y=" + y);
            assertEquals(ChangeKind.PLACE, change.kind());
            assertEquals("minecraft:sand", change.state().id(), "raise must copy the column's own block");
        }
    }

    @Test
    void groundMaskKeepsTheBrushOffTreeCanopies() {
        EditPlan plan = BrushPlanBuilder.build(BrushOp.FILL, 0, 0, 4, new TreeView());
        assertFalse(plan.isEmpty());
        for (PlannedChange change : plan.changes().values()) {
            assertEquals(0, change.pos().x());
            assertEquals(0, change.pos().z());
            assertTrue(change.pos().y() <= 70, "edits must be relative to ground, not the 90-high canopy");
        }
    }

    @Test
    void identicalInputsProduceByteIdenticalPlans() {
        EditPlan a = BrushPlanBuilder.build(BrushOp.MELT, 0, 0, 4, new SpikeView());
        EditPlan b = BrushPlanBuilder.build(BrushOp.MELT, 0, 0, 4, new SpikeView());
        assertEquals(a.changes(), b.changes());
        // Map iteration order included (spec §7 case 7): LinkedHashMap key order must match.
        assertEquals(List.copyOf(a.changes().keySet()), List.copyOf(b.changes().keySet()));
    }
}
