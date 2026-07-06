package com.lwos.plan;

import com.lwos.config.PathStyle;
import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EditPlanBuilderTest {

    /** The geometric-pipeline assertions below deliberately disable the organic stages. */
    private static final PathStyle NEUTRAL = PathStyle.neutral();

    private static final class FlatWorldView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 70; }
    }

    /** Surface rises gently with x (1 block every 4) — non-flat but too shallow to trigger stairs. */
    private static final class SlopedWorldView implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return 70 + Math.floorDiv(x, 4); }
    }

    /** A flat surface at a fixed height, for isolating cut vs. fill behavior against a level path. */
    private record LevelWorldView(int height) implements WorldView {
        @Override
        public int surfaceHeight(int x, int z) { return height; }
    }

    @Test
    void producesNonEmptyTerrainPlanAlongTheLine() {
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(10, 70, 0));
        EditPlan plan = EditPlanBuilder.build(controls, 1.0, 4.0, new FlatWorldView(),
                TerrainMode.FOLLOW_SURFACE, NEUTRAL);

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
        EditPlan plan = EditPlanBuilder.build(controls, 1.0, 3.0, new SlopedWorldView(),
                TerrainMode.FOLLOW_SURFACE, NEUTRAL);

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
    void cutAndFillCarvesTerrainAboveThePathToAir() {
        // Surface at 80, path forced down to 70 -> everything from 71..80 must be removed (set to air).
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(10, 70, 0));
        EditPlan plan = EditPlanBuilder.build(controls, 1.0, 3.0, new LevelWorldView(80),
                TerrainMode.CUT_AND_FILL, NEUTRAL);

        boolean sawCarve = false;
        boolean sawFill = false;
        for (PlannedChange change : plan.changes().values()) {
            if (change.kind() == ChangeKind.REMOVE) {
                sawCarve = true;
                assertEquals("minecraft:air", change.state().id(), "carve targets must be air");
                assertTrue(change.pos().y() > 70 && change.pos().y() <= 80,
                        "carved blocks sit above the path and up to the surface");
            }
            if (change.kind() == ChangeKind.PLACE) sawFill = true;
        }
        assertTrue(sawCarve, "a path cut 10 blocks below the surface must produce REMOVE changes");
        assertFalse(sawFill, "cutting through a hill must not place fill");
        // The walkable path floor sits at the interpolated Y.
        assertTrue(plan.changes().containsKey(new GridPos(5, 70, 0)));
        assertEquals(ChangeKind.TERRAIN, plan.changes().get(new GridPos(5, 70, 0)).kind());
    }

    @Test
    void cutAndFillBuildsAFoundationOverADip() {
        // Surface at 60, path held up at 70 -> dirt fill from 61..69, path block at 70, nothing removed.
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(10, 70, 0));
        EditPlan plan = EditPlanBuilder.build(controls, 1.0, 3.0, new LevelWorldView(60),
                TerrainMode.CUT_AND_FILL, NEUTRAL);

        boolean sawFill = false;
        for (PlannedChange change : plan.changes().values()) {
            assertNotEquals(ChangeKind.REMOVE, change.kind(), "bridging a dip must not carve anything");
            if (change.kind() == ChangeKind.PLACE) {
                sawFill = true;
                assertEquals("minecraft:dirt", change.state().id(), "fill material is dirt");
                assertTrue(change.pos().y() > 60 && change.pos().y() < 70,
                        "fill sits between the old surface and the path");
            }
        }
        assertTrue(sawFill, "a path held 10 blocks above the surface must produce PLACE fill");
        assertTrue(plan.changes().containsKey(new GridPos(5, 70, 0)));
    }

    @Test
    void singleControlPointStillProducesAPlan() {
        EditPlan plan = EditPlanBuilder.build(List.of(new Vec3d(0, 70, 0)), 1.0, 2.0, new FlatWorldView(),
                TerrainMode.FOLLOW_SURFACE, NEUTRAL);
        assertFalse(plan.isEmpty());
    }

    @Test
    void sameInputsProduceIdenticalPlan_determinism() {
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(4, 70, 2), new Vec3d(9, 70, -3));
        WorldView view = new FlatWorldView();

        EditPlan first = EditPlanBuilder.build(controls, 0.5, 5.0, view, TerrainMode.FOLLOW_SURFACE, NEUTRAL);
        EditPlan second = EditPlanBuilder.build(controls, 0.5, 5.0, view, TerrainMode.FOLLOW_SURFACE, NEUTRAL);

        assertEquals(first.changes(), second.changes());
    }

    @Test
    void organicDefaultsAreDeterministic() {
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(20, 70, 8), new Vec3d(40, 70, -6));
        WorldView view = new FlatWorldView();

        EditPlan first = EditPlanBuilder.build(controls, 0.25, 8.0, view,
                TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());
        EditPlan second = EditPlanBuilder.build(controls, 0.25, 8.0, view,
                TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());

        assertEquals(first.changes(), second.changes(),
                "same control points + tunables must yield a byte-identical organic plan (preview==apply)");
    }

    @Test
    void organicDefaultsKeepNarrowDefaultWidthPathVisible() {
        // Regression (vanished-preview bug): at the in-game default width 3 the absolute organic
        // amplitudes (erosion 1.5, feather skirt 2) both meet/exceed the path's half-width (1.5) and
        // used to erode+feather the whole footprint away (28 columns -> 1), making the preview invisible.
        // A default-width path must keep a solid, visible core.
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(6, 70, 0));
        WorldView view = new FlatWorldView();
        double spacing = EditPlanBuilder.DEFAULT_SPACING;

        int neutral = EditPlanBuilder.build(controls, spacing, 3.0, view,
                TerrainMode.FOLLOW_SURFACE, NEUTRAL).changes().size();
        int organic = EditPlanBuilder.build(controls, spacing, 3.0, view,
                TerrainMode.FOLLOW_SURFACE, PathStyle.defaults()).changes().size();

        assertTrue(organic >= neutral / 2,
                "a default width-3 path must keep a visible core; kept " + organic + " of " + neutral);
    }

    @Test
    void organicDefaultsClusterMaterialsFeatherAndReshapeVsNeutral() {
        // A wide, long path gives the noise fields room to express clusters and a feathered rim.
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(30, 70, 0), new Vec3d(60, 70, 0));
        WorldView view = new FlatWorldView();

        EditPlan organic = EditPlanBuilder.build(controls, 0.25, 8.0, view,
                TerrainMode.FOLLOW_SURFACE, PathStyle.defaults());
        EditPlan neutral = EditPlanBuilder.build(controls, 0.25, 8.0, view,
                TerrainMode.FOLLOW_SURFACE, NEUTRAL);

        // (a) Clustered materials: more than one distinct block id appears (not uniform dirt_path).
        Set<String> organicBlocks = new HashSet<>();
        for (PlannedChange change : organic.changes().values()) {
            organicBlocks.add(change.state().id());
        }
        assertTrue(organicBlocks.size() > 1,
                "organic default palette must place more than one distinct block id, got " + organicBlocks);

        // (b) Feathering: fewer inside columns are placed than the neutral (un-feathered) build.
        assertTrue(organic.changes().size() < neutral.changes().size(),
                "feathering must drop some near-edge inside columns vs the neutral build");

        // (c) Edge shaping: the placed footprint differs from the neutral (un-shaped) footprint.
        Set<GridPos> organicCols = organic.changes().keySet();
        Set<GridPos> neutralCols = neutral.changes().keySet();
        assertNotEquals(neutralCols, organicCols,
                "edge shaping + feathering must change which columns are inside the path");
    }

    /** Builds a style with explicit width-relative knobs (palette falls back to the default core). */
    private static PathStyle style(double roughness, double featureSize, double feather, double core) {
        String json = String.format(java.util.Locale.ROOT,
                "{\"edgeRoughness\":%s,\"edgeFeatureSize\":%s,\"featherDepth\":%s,\"coreProtect\":%s}",
                roughness, featureSize, feather, core);
        return PathStyle.fromJson(json);
    }

    /** Counts placed columns whose center lies beyond {@code halfWidth} of the z=0 centerline. */
    private static int outwardColumnCount(List<Vec3d> controls, double width, double halfWidth,
                                          WorldView view, PathStyle s) {
        Set<GridPos> cols = EditPlanBuilder.build(controls, 0.25, width, view,
                TerrainMode.FOLLOW_SURFACE, s).changes().keySet();
        int count = 0;
        for (GridPos p : cols) {
            if (Math.abs(p.z() + 0.5) > halfWidth) count++;
        }
        return count;
    }

    /** The farthest a placed column's center reaches from the z=0 centerline (the outer boundary). */
    private static double maxOutwardExcursion(List<Vec3d> controls, double width,
                                              WorldView view, PathStyle s) {
        Set<GridPos> cols = EditPlanBuilder.build(controls, 0.25, width, view,
                TerrainMode.FOLLOW_SURFACE, s).changes().keySet();
        double max = 0.0;
        for (GridPos p : cols) {
            max = Math.max(max, Math.abs(p.z() + 0.5));
        }
        return max;
    }

    @Test
    void featherEngagesAtDefaultWidthThree() {
        // The old floor() clamp forced skirt=0 below width 8, so feathering never ran on a width-3
        // path. With width-relative feather it must now drop near-edge columns vs a no-feather build.
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(20, 70, 0));
        WorldView view = new FlatWorldView();

        int hard = EditPlanBuilder.build(controls, 0.25, 3.0, view,
                TerrainMode.FOLLOW_SURFACE, style(0.0, 5.0, 0.0, 0.4)).changes().size();
        int soft = EditPlanBuilder.build(controls, 0.25, 3.0, view,
                TerrainMode.FOLLOW_SURFACE, style(0.0, 5.0, 0.8, 0.4)).changes().size();

        assertTrue(soft < hard,
                "feather must drop edge columns even at width 3 (impossible under the old clamp); "
                        + "hard=" + hard + " soft=" + soft);
    }

    @Test
    void higherRoughnessWidensOutwardBoundaryExcursions() {
        // Same control points => same underlying noise field; a larger roughness scales the same
        // excursions further, so strictly more columns cross beyond the half-width.
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(30, 70, 0));
        WorldView view = new FlatWorldView();

        int low = outwardColumnCount(controls, 8.0, 4.0, view, style(0.2, 5.0, 0.0, 0.2));
        int high = outwardColumnCount(controls, 8.0, 4.0, view, style(0.9, 5.0, 0.0, 0.2));

        assertTrue(high > low,
                "raising edgeRoughness must widen outward excursions; low=" + low + " high=" + high);
    }

    @Test
    void protectedCoreSurvivesMaxErosionAndFeather() {
        // width 8, coreProtect 0.4 -> coreRadius 1.6, so the z=0 row (radial distance 0.5) is deep in
        // the protected core and must survive even at max roughness AND max feather.
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(20, 70, 0));
        WorldView view = new FlatWorldView();

        Set<GridPos> cols = EditPlanBuilder.build(controls, 0.25, 8.0, view,
                TerrainMode.FOLLOW_SURFACE, style(1.0, 5.0, 1.0, 0.4)).changes().keySet();

        for (int x = 2; x <= 18; x++) {
            assertTrue(cols.contains(new GridPos(x, 70, 0)),
                    "protected-core column x=" + x + " (z=0) must survive max erosion + feather");
        }
    }

    @Test
    void outwardBaysReachBeyondTheCleanFootprintAndHalfWidth() {
        // The old fixed 0.5 halo capped outward growth. With the proportional halo, high roughness
        // must push the outer boundary PAST both the clean (un-wobbled) footprint and the geometric
        // half-width. Comparative so it proves erosion caused the excursion, not endpoint rounding.
        // (3-octave Perlin rarely nears its +/-1 bound, so assert "further than clean", not a fixed
        // absolute reach — the realized outward push is real but modest.)
        List<Vec3d> controls = List.of(new Vec3d(0, 70, 0), new Vec3d(30, 70, 0));
        WorldView view = new FlatWorldView();
        double width = 8.0;

        double cleanMax = maxOutwardExcursion(controls, width, view, style(0.0, 5.0, 0.0, 0.2));
        double roughMax = maxOutwardExcursion(controls, width, view, style(0.9, 5.0, 0.0, 0.2));

        assertTrue(roughMax > cleanMax,
                "high roughness must push the boundary beyond the clean footprint; "
                        + "clean=" + cleanMax + " rough=" + roughMax);
        assertTrue(roughMax > width / 2.0,
                "outward bays must extend beyond the geometric half-width; rough=" + roughMax);
    }
}
