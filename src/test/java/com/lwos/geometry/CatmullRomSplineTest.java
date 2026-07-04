package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CatmullRomSplineTest {
    private static final double EPS = 1e-6;

    @Test
    void emptyInEmptyOut() {
        assertTrue(CatmullRomSpline.sample(List.of(), 8).isEmpty());
    }

    @Test
    void singlePointReturnsItself() {
        Vec3d p = new Vec3d(1, 2, 3);
        List<Vec3d> out = CatmullRomSpline.sample(List.of(p), 8);
        assertEquals(1, out.size());
        assertEquals(p, out.get(0));
    }

    @Test
    void passesThroughEndpoints() {
        Vec3d a = new Vec3d(0, 0, 0);
        Vec3d d = new Vec3d(9, 1, 2);
        List<Vec3d> out = CatmullRomSpline.sample(
                List.of(a, new Vec3d(3, 2, 0), new Vec3d(6, -1, 1), d), 16);
        assertEquals(a.x(), out.get(0).x(), EPS);
        assertEquals(a.y(), out.get(0).y(), EPS);
        assertEquals(a.z(), out.get(0).z(), EPS);
        Vec3d last = out.get(out.size() - 1);
        assertEquals(d.x(), last.x(), EPS);
        assertEquals(d.y(), last.y(), EPS);
        assertEquals(d.z(), last.z(), EPS);
    }

    @Test
    void collinearPointsStayCollinear() {
        List<Vec3d> out = CatmullRomSpline.sample(
                List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), new Vec3d(2, 0, 0)), 10);
        double prevX = Double.NEGATIVE_INFINITY;
        for (Vec3d p : out) {
            assertEquals(0.0, p.y(), EPS);
            assertEquals(0.0, p.z(), EPS);
            assertTrue(p.x() >= prevX - EPS, "x should be monotonically non-decreasing");
            prevX = p.x();
        }
    }

    @Test
    void noNaNOnDuplicateConsecutivePoints() {
        List<Vec3d> out = CatmullRomSpline.sample(
                List.of(new Vec3d(0, 0, 0), new Vec3d(0, 0, 0), new Vec3d(1, 0, 0)), 8);
        for (Vec3d p : out) {
            assertFalse(Double.isNaN(p.x()) || Double.isNaN(p.y()) || Double.isNaN(p.z()));
        }
    }
}
