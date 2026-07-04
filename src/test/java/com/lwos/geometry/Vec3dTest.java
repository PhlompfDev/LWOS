package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Vec3dTest {
    private static final double EPS = 1e-9;

    @Test
    void addSubScale() {
        Vec3d a = new Vec3d(1, 2, 3);
        Vec3d b = new Vec3d(4, 5, 6);
        assertEquals(new Vec3d(5, 7, 9), a.add(b));
        assertEquals(new Vec3d(-3, -3, -3), a.sub(b));
        assertEquals(new Vec3d(2, 4, 6), a.scale(2));
    }

    @Test
    void lengthAndDistance() {
        assertEquals(5.0, new Vec3d(3, 4, 0).length(), EPS);
        assertEquals(5.0, new Vec3d(0, 0, 0).distance(new Vec3d(0, 3, 4)), EPS);
    }

    @Test
    void pathNodeHoldsPosition() {
        Vec3d p = new Vec3d(7, 8, 9);
        assertEquals(p, new PathNode(p).position());
    }
}
