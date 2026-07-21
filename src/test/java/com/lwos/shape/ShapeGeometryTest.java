package com.lwos.shape;

import com.lwos.plan.GridPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShapeGeometryTest {
    @Test
    void lineWalksDominantAxis() {
        List<GridPos> l = ShapeGeometry.line(new GridPos(0, 5, 0), new GridPos(4, 5, 1));
        assertEquals(5, l.size()); // dominant axis X: 0..4
        assertEquals(new GridPos(0, 5, 0), l.get(0));
        assertEquals(new GridPos(4, 5, 0), l.get(l.size() - 1)); // z stays at the anchor's z
    }

    @Test
    void lineSingleBlock() {
        assertEquals(List.of(new GridPos(2, 2, 2)),
                ShapeGeometry.line(new GridPos(2, 2, 2), new GridPos(2, 2, 2)));
    }

    @Test
    void rectFloorFilledAndHollow() {
        // corners agree on Y -> XZ floor rectangle
        GridPos a = new GridPos(0, 4, 0), b = new GridPos(3, 4, 2);
        assertEquals(4 * 3, ShapeGeometry.rect(a, b, false).size());
        assertEquals(2 * (4 + 3) - 4, ShapeGeometry.rect(a, b, true).size()); // perimeter
    }

    @Test
    void rectWallSpansHeight() {
        // corners agree on X -> ZY wall
        GridPos a = new GridPos(1, 0, 0), b = new GridPos(1, 4, 3);
        assertEquals(5 * 4, ShapeGeometry.rect(a, b, false).size());
    }

    @Test
    void boxShellVsSolid() {
        GridPos a = new GridPos(0, 0, 0), b = new GridPos(3, 3, 3);
        assertEquals(64, ShapeGeometry.box(a, b, false).size());
        assertEquals(64 - 8, ShapeGeometry.box(a, b, true).size()); // 4^3 minus 2^3 interior
    }

    @Test
    void rectAutoDepthBecomesThickness() {
        // No axis agrees: dy=1 smallest -> horizontal floor, TWO layers thick (y 5 and 6).
        List<GridPos> r = ShapeGeometry.rectAuto(new GridPos(0, 5, 0), new GridPos(3, 6, 4), false);
        assertEquals(4 * 5 * 2, r.size());
        for (GridPos p : r) assertTrue(p.y() == 5 || p.y() == 6);
        assertEquals(1, ShapeGeometry.rectFixedAxis(new GridPos(0, 5, 0), new GridPos(3, 6, 4)));
    }

    @Test
    void rectAutoKeepsVerticalWallWhenXAgrees() {
        // One corner up top, one further down: x agrees -> ZY wall, single layer.
        assertEquals(0, ShapeGeometry.rectFixedAxis(new GridPos(2, 10, 0), new GridPos(2, 4, 5)));
        assertEquals(7 * 6, ShapeGeometry.rectAuto(new GridPos(2, 10, 0), new GridPos(2, 4, 5), false).size());
    }

    @Test
    void rectAutoWallTwoBlocksOut() {
        // The user scenario: top-left of a wall, then bottom-right but 2 out of the plane
        // (dz=1 -> 2 thick): same XY wall, two layers, corner-inclusive.
        GridPos a = new GridPos(0, 10, 0), b = new GridPos(6, 4, 1);
        assertEquals(2, ShapeGeometry.rectFixedAxis(a, b));
        List<GridPos> r = ShapeGeometry.rectAuto(a, b, false);
        assertEquals(7 * 7 * 2, r.size());
        for (GridPos p : r) assertTrue(p.z() == 0 || p.z() == 1);
    }

    @Test
    void rectAutoTallDiagonalBecomesThickWall() {
        // dy dominates (10) with dx=2 smallest -> vertical ZY wall, 3 blocks thick (x 0..2).
        GridPos a = new GridPos(0, 0, 0), b = new GridPos(2, 10, 6);
        assertEquals(0, ShapeGeometry.rectFixedAxis(a, b));
        List<GridPos> r = ShapeGeometry.rectAuto(a, b, false);
        assertEquals(7 * 11 * 3, r.size());
        for (GridPos p : r) assertTrue(p.x() >= 0 && p.x() <= 2);
    }

    @Test
    void verticalCircleOnWallAxis() {
        // Normal X: circle spans Z and Y.
        List<GridPos> c = ShapeGeometry.circle(new GridPos(3, 10, 0), 4, true, 0);
        for (GridPos p : c) assertEquals(3, p.x());
        assertTrue(c.contains(new GridPos(3, 14, 0)));
        assertTrue(c.contains(new GridPos(3, 10, 4)));
    }

    @Test
    void circleIsSymmetricAndCentered() {
        List<GridPos> c = ShapeGeometry.circle(new GridPos(0, 7, 0), 5, true, 1);
        HashSet<GridPos> set = new HashSet<>(c);
        assertEquals(set.size(), c.size()); // no duplicates
        for (GridPos p : c) {
            assertEquals(7, p.y());
            assertTrue(set.contains(new GridPos(-p.x(), 7, p.z()))); // x-mirror symmetry
            assertTrue(set.contains(new GridPos(p.x(), 7, -p.z()))); // z-mirror symmetry
        }
    }

    @Test
    void filledCircleContainsOutline() {
        HashSet<GridPos> filled = new HashSet<>(ShapeGeometry.circle(new GridPos(0, 0, 0), 4, false, 1));
        assertTrue(filled.containsAll(ShapeGeometry.circle(new GridPos(0, 0, 0), 4, true, 1)));
        assertTrue(filled.contains(new GridPos(0, 0, 0)));
    }

    @Test
    void sphereShellHasNoInterior() {
        HashSet<GridPos> shell = new HashSet<>(ShapeGeometry.sphere(new GridPos(0, 0, 0), 4, true));
        assertFalse(shell.contains(new GridPos(0, 0, 0)));
        assertTrue(shell.contains(new GridPos(4, 0, 0)));
        assertTrue(shell.contains(new GridPos(0, -4, 0)));
    }

    @Test
    void extentClampCapsRunawayShapes() {
        List<GridPos> l = ShapeGeometry.line(new GridPos(0, 0, 0), new GridPos(500, 0, 0));
        assertEquals(ShapeGeometry.MAX_EXTENT + 1, l.size()); // anchor + 64
        List<GridPos> s = ShapeGeometry.sphere(new GridPos(0, 0, 0), 500, true);
        for (GridPos p : s) assertTrue(Math.abs(p.x()) <= ShapeGeometry.MAX_EXTENT);
    }

    @Test
    void deterministicOrder() {
        assertEquals(ShapeGeometry.sphere(new GridPos(3, 9, -2), 6, false),
                     ShapeGeometry.sphere(new GridPos(3, 9, -2), 6, false));
    }
}
