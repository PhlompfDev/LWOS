package com.lwos.plan;

import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditPlanBuilderTest {

    private static final class FlatWorldView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 70; }
    }

    /** Surface rises gently with x (1 block every 4) — non-flat but too shallow to trigger stairs. */
    private static final class SlopedWorldView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 70 + Math.floorDiv(x, 4); }
    }

    @Test
    void producesNonEmptyTerrainPlanAlongTheLine() {
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(10, 70, 0));
        EditPlan plan = EditPlanBuilder.build(controls, 1.0, 4.0, new FlatWorldView());

        assertFalse(plan.isEmpty());
        for (PlannedChange change : plan.changes().values()) {
            assertEquals(ChangeKind.TERRAIN, change.kind());
            assertEquals(70, change.pos().y());
            assertEquals("minecraft:dirt_path", change.state().id());
        }
        // A column at the midpoint of the line should be covered.
        assertTrue(plan.changes().containsKey(new GridPos(5, 70, 0)));
    }

    @Test
    void followSurfaceModeDrapesOverTheMockHeightmap() {
        List<Vec3d> controls = List.of(new Vec3d(0, 0, 0), new Vec3d(12, 0, 0));
        EditPlan plan = EditPlanBuilder.build(controls, 1.0, 3.0, new SlopedWorldView(), TerrainMode.FOLLOW_SURFACE);

        assertFalse(plan.isEmpty());
        for (PlannedChange change : plan.changes().values()) {
            // Every placed block sits exactly on the surface reported by the WorldView for its column,
            // regardless of the control points' own Y — the path follows the terrain, not a flat plane.
            int expectedY = 70 + Math.floorDiv(change.pos().x(), 4);
            assertEquals(expectedY, change.pos().y(),
                    "column x=" + change.pos().x() + " should drape onto its surface height");
            assertEquals(ChangeKind.TERRAIN, change.kind());
        }
        // A column partway up the slope lands on its raised surface, not Y=70.
        assertTrue(plan.changes().containsKey(new GridPos(8, 72, 0)));
    }

    @Test
    void singleControlPointStillProducesAPlan() {
        EditPlan plan = EditPlanBuilder.build(List.of(new Vec3d(0, 70, 0)), 1.0, 2.0, new FlatWorldView());
        assertFalse(plan.isEmpty());
    }

    @Test
    void sameInputsProduceIdenticalPlan_determinism() {
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(4, 70, 2), new Vec3d(9, 70, -3));
        WorldView view = new FlatWorldView();

        EditPlan first = EditPlanBuilder.build(controls, 0.5, 5.0, view);
        EditPlan second = EditPlanBuilder.build(controls, 0.5, 5.0, view);

        assertEquals(first.changes(), second.changes());
    }
}
