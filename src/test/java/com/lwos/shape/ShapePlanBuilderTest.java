package com.lwos.shape;

import com.lwos.geometry.WorldView;
import com.lwos.plan.BlockStateRef;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShapePlanBuilderTest {
    private static final BlockStateRef OAK = new BlockStateRef("minecraft:oak_planks");
    /** Ground at y<=5 is stone, everything above is air. */
    private static final WorldView FLAT = new WorldView() {
        @Override public int surfaceHeight(int x, int z) { return 5; }
        @Override public String surfaceBlockId(int x, int z) { return "minecraft:stone"; }
    };

    @Test
    void placeFillsAirOnly() {
        // vertical wall from y=4 to y=8: y<=5 is stone (skipped), y>5 planned
        EditPlan plan = ShapePlanBuilder.build(
                List.of(new GridPos(0, 4, 0), new GridPos(0, 8, 3)),
                ShapeMode.WALL, ShapeOptions.DEFAULT, OAK, false, FLAT);
        assertFalse(plan.changes().containsKey(new GridPos(0, 4, 0))); // stone, skipped
        assertFalse(plan.changes().containsKey(new GridPos(0, 5, 2)));
        PlannedChange top = plan.changes().get(new GridPos(0, 8, 1));
        assertEquals(ChangeKind.PLACE, top.kind());
        assertEquals(OAK, top.state());
        assertEquals(4 * 3, plan.size()); // y 6,7,8 x z 0..3
    }

    @Test
    void breakPlansAirOnSolidsOnly() {
        EditPlan plan = ShapePlanBuilder.build(
                List.of(new GridPos(0, 4, 0), new GridPos(0, 8, 3)),
                ShapeMode.WALL, ShapeOptions.DEFAULT, OAK, true, FLAT);
        PlannedChange ground = plan.changes().get(new GridPos(0, 4, 0));
        assertEquals(ChangeKind.REMOVE, ground.kind());
        assertEquals("minecraft:air", ground.state().id());
        assertFalse(plan.changes().containsKey(new GridPos(0, 8, 0))); // already air
        assertEquals(2 * 4, plan.size()); // y 4,5 x z 0..3
    }

    @Test
    void cubeUsesThirdAnchorHeight() {
        EditPlan plan = ShapePlanBuilder.build(
                List.of(new GridPos(0, 6, 0), new GridPos(2, 6, 2), new GridPos(2, 9, 2)),
                ShapeMode.CUBE, ShapeOptions.DEFAULT, OAK, false, FLAT);
        assertEquals(3 * 3 * 4, plan.size()); // 3x3 base, y 6..9, all air
    }

    @Test
    void circleRadiusFromDistance() {
        EditPlan plan = ShapePlanBuilder.build(
                List.of(new GridPos(0, 10, 0), new GridPos(3, 10, 4)), // dist 5
                ShapeMode.CIRCLE, new ShapeOptions(ShapeOptions.Fill.HOLLOW), OAK, false, FLAT);
        assertTrue(plan.changes().containsKey(new GridPos(5, 10, 0)));
        assertTrue(plan.changes().containsKey(new GridPos(0, 10, 5)));
    }

    @Test
    void wrongAnchorCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> ShapePlanBuilder.build(
                List.of(new GridPos(0, 0, 0)), ShapeMode.LINE, ShapeOptions.DEFAULT, OAK, false, FLAT));
    }

    @Test
    void deterministicPlans() {
        List<GridPos> anchors = List.of(new GridPos(-4, 7, 9), new GridPos(1, 7, 12));
        EditPlan p1 = ShapePlanBuilder.build(anchors, ShapeMode.SPHERE, ShapeOptions.DEFAULT, OAK, false, FLAT);
        EditPlan p2 = ShapePlanBuilder.build(anchors, ShapeMode.SPHERE, ShapeOptions.DEFAULT, OAK, false, FLAT);
        assertEquals(p1.changes(), p2.changes());
        assertEquals(List.copyOf(p1.changes().keySet()), List.copyOf(p2.changes().keySet())); // same order
    }
}
