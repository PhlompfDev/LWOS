package com.lwos.plan;

import com.lwos.config.PathStyle;
import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditPlanBuilderEdgeBandTest {

    /** Flat world at y=64 everywhere. */
    private static WorldView flat() {
        return (x, z) -> 64;
    }

    @Test
    void wideFollowSurfacePathPlacesEdgeShoulderBlocks() {
        List<Vec3d> pts = List.of(new Vec3d(0, 64, 0), new Vec3d(20, 64, 0));
        EditPlan plan = EditPlanBuilder.build(
                pts, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(), TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());

        boolean hasCoarseOrMoss = plan.changes().values().stream()
                .anyMatch(c -> c.state().id().equals("minecraft:moss_block")
                        || c.state().id().equals("minecraft:coarse_dirt"));
        assertTrue(hasCoarseOrMoss, "defaults must place edge-shoulder materials on a wide path");
    }

    @Test
    void neutralStyleProducesNoEdgeShoulder() {
        List<Vec3d> pts = List.of(new Vec3d(0, 64, 0), new Vec3d(20, 64, 0));
        EditPlan plan = EditPlanBuilder.build(
                pts, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(), TerrainMode.FOLLOW_SURFACE, PathStyle.neutral());
        boolean allDirtPath = plan.changes().values().stream()
                .allMatch(c -> c.state().id().equals("minecraft:dirt_path"));
        assertTrue(allDirtPath, "neutral must be a uniform dirt_path footprint (no shoulder)");
    }

    @Test
    void sameStyleAndPointsIsDeterministic() {
        List<Vec3d> pts = List.of(new Vec3d(0, 64, 0), new Vec3d(15, 64, 5));
        EditPlan a = EditPlanBuilder.build(pts, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(), TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());
        EditPlan b = EditPlanBuilder.build(pts, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(), TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());
        assertEquals(a.changes(), b.changes(), "preview==apply: identical inputs must yield an identical plan");
    }
}
