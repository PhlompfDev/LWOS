package com.lwos.plan;

import com.lwos.config.PathStyle;
import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditPlanBuilderEdgeScatterTest {

    /** Flat world at y=64 everywhere. */
    private static WorldView flat() {
        return new WorldView() {
            @Override
            public int surfaceHeight(int x, int z) { return 64; }

            @Override
            public String surfaceBlockId(int x, int z) { return "minecraft:grass_block"; }
        };
    }

    private static final List<Vec3d> STRAIGHT = List.of(new Vec3d(0, 64, 0), new Vec3d(20, 64, 0));

    /**
     * Builds a style from defaults with individual edge knobs overridden. Uses a single-material
     * core palette (dirt_path only) rather than {@code defaults().core()} — the default core
     * palette also contains {@code coarse_dirt}, which would make {@link #isEdgeMaterial} match
     * ordinary core-gradient blocks and not just genuine edge-scatter blocks.
     */
    private static PathStyle style(double erosion, double blend, double coverage,
                                   double cluster, double reach, double coreProtect) {
        PathStyle d = PathStyle.defaults();
        List<PathStyle.Entry> core = List.of(new PathStyle.Entry("minecraft:dirt_path", 1.0, 0.1, cluster));
        return new PathStyle(core, d.edge(), erosion, d.edgeFeatureSize(), coreProtect,
                blend, coverage, cluster, reach, d.defaultClusterSize());
    }

    private static boolean isEdgeMaterial(String id) {
        return id.equals("minecraft:coarse_dirt") || id.equals("minecraft:moss_block");
    }

    @Test
    void spineIsNeverErodedIntoAHole() {
        // Erosion far larger than the half-width (width 4 -> halfWidth 2); the centre line must survive.
        EditPlan plan = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 4.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(6.0, 2.0, 0.5, 4.0, 2.0, 0.4));
        for (int x = 3; x <= 17; x++) {
            GridPos centre = new GridPos(x, 64, 0);
            assertTrue(plan.changes().containsKey(centre),
                    "protected spine column (" + x + ",0) must always be placed");
        }
    }

    @Test
    void erosionReachesBeyondTheOldBandCap() {
        // Old model capped erosion at (1-coreProtect)*halfWidth = 0.6*2 = 1.2 blocks. With erosion 6 the
        // footprint must extend measurably wider than with no erosion.
        int wideNoErosion = maxAbsZ(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 4.0,
                flat(), TerrainMode.FOLLOW_SURFACE, style(0.0, 0.0, 0.0, 4.0, 0.0, 0.4)));
        int wideEroded = maxAbsZ(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 4.0,
                flat(), TerrainMode.FOLLOW_SURFACE, style(6.0, 0.0, 0.0, 4.0, 0.0, 0.4)));
        assertTrue(wideEroded > wideNoErosion + 1,
                "erosion 6 must bulge past the old ~1-block cap (" + wideEroded + " vs " + wideNoErosion + ")");
    }

    @Test
    void coverageZeroScattersNoEdgeBlocks() {
        EditPlan plan = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(1.5, 2.0, 0.0, 4.0, 2.0, 0.4));
        long edge = plan.changes().values().stream().filter(c -> isEdgeMaterial(c.state().id())).count();
        assertEquals(0, edge, "coverage 0 must place no edge-shoulder blocks");
    }

    @Test
    void coverageIsMonotonic() {
        long low = countEdge(0.3);
        long high = countEdge(1.0);
        assertEquals(0, countEdge(0.0), "coverage 0 -> zero edge blocks");
        assertTrue(high >= low && high > 0,
                "more coverage must never scatter fewer edge blocks (" + high + " >= " + low + ")");
    }

    @Test
    void clusterSizeChangesTheScatterPattern() {
        var fine = edgePositions(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(1.5, 2.0, 0.6, 2.0, 2.0, 0.4)));
        var broad = edgePositions(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(1.5, 2.0, 0.6, 12.0, 2.0, 0.4)));
        assertNotEquals(fine, broad, "edge cluster size must change which columns scatter");
    }

    @Test
    void reachScattersOntoTerrainOutsideTheRim() {
        // No erosion so the only source of outside columns is edgeReach.
        int rimNoReach = maxAbsZ(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(0.0, 2.0, 1.0, 4.0, 0.0, 0.4)));
        int rimWithReach = maxAbsZ(EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(0.0, 2.0, 1.0, 4.0, 4.0, 0.4)));
        assertTrue(rimWithReach > rimNoReach,
                "edgeReach must place blocks further out than the bare rim (" + rimWithReach + " vs " + rimNoReach + ")");
    }

    @Test
    void cutAndFillSuppressesOutwardScatter() {
        // Cut/fill keeps its carved footprint; columns with d > 0 must never scatter, even with
        // non-zero edgeCoverage and edgeReach that would otherwise place an outward edge shoulder.
        EditPlan plan = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.CUT_AND_FILL, style(1.5, 2.0, 1.0, 4.0, 4.0, 0.4));
        long edge = plan.changes().values().stream().filter(c -> isEdgeMaterial(c.state().id())).count();
        assertEquals(0, edge, "CUT_AND_FILL must never place scattered edge-shoulder blocks");
    }

    @Test
    void neutralStyleProducesNoEdgeShoulder() {
        EditPlan plan = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, PathStyle.neutral());
        boolean allDirtPath = plan.changes().values().stream()
                .allMatch(c -> c.state().id().equals("minecraft:dirt_path"));
        assertTrue(allDirtPath, "neutral must be a uniform dirt_path footprint (no shoulder)");
    }

    @Test
    void sameStyleAndPointsIsDeterministic() {
        EditPlan a = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(),
                TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());
        EditPlan b = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 6.0, flat(),
                TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());
        assertEquals(a.changes(), b.changes(), "preview==apply: identical inputs must yield an identical plan");
    }

    @Test
    void narrowDefaultWidthPathStaysVisible() {
        // Regression (vanished-preview bug): at the in-game default width 3 the absolute erosion (1.5)
        // and feather (blend 2) both meet/exceed the half-width (1.5). The core-protection clamp must
        // keep a solid, visible spine so the footprint is never eroded/feathered away to nothing.
        // (Relocated from EditPlanBuilderTest, which asserted this against the pre-clamp pipeline.)
        List<Vec3d> shortPath = List.of(new Vec3d(0, 64, 0), new Vec3d(6, 64, 0));
        int neutral = EditPlanBuilder.build(shortPath, EditPlanBuilder.DEFAULT_SPACING, 3.0, flat(),
                TerrainMode.FOLLOW_SURFACE, PathStyle.neutral()).changes().size();
        int organic = EditPlanBuilder.build(shortPath, EditPlanBuilder.DEFAULT_SPACING, 3.0, flat(),
                TerrainMode.FOLLOW_SURFACE, PathStyle.defaults()).changes().size();
        assertTrue(organic >= neutral / 2,
                "a default width-3 path must keep a visible core; kept " + organic + " of " + neutral);
    }

    // --- helpers -------------------------------------------------------------------------------

    private long countEdge(double coverage) {
        EditPlan plan = EditPlanBuilder.build(STRAIGHT, EditPlanBuilder.DEFAULT_SPACING, 8.0, flat(),
                TerrainMode.FOLLOW_SURFACE, style(1.5, 2.0, coverage, 4.0, 2.0, 0.4));
        return plan.changes().values().stream().filter(c -> isEdgeMaterial(c.state().id())).count();
    }

    private static java.util.Set<GridPos> edgePositions(EditPlan plan) {
        java.util.Set<GridPos> out = new java.util.HashSet<>();
        plan.changes().forEach((pos, c) -> { if (isEdgeMaterial(c.state().id())) out.add(pos); });
        return out;
    }

    private static int maxAbsZ(EditPlan plan) {
        int max = 0;
        for (GridPos pos : plan.changes().keySet()) max = Math.max(max, Math.abs(pos.z()));
        return max;
    }
}
