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
    void circleIsSymmetricAndCentered() {
        List<GridPos> c = ShapeGeometry.circle(new GridPos(0, 7, 0), 5, true);
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
        HashSet<GridPos> filled = new HashSet<>(ShapeGeometry.circle(new GridPos(0, 0, 0), 4, false));
        assertTrue(filled.containsAll(ShapeGeometry.circle(new GridPos(0, 0, 0), 4, true)));
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
