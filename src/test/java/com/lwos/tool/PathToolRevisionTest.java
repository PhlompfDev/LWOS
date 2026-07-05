package com.lwos.tool;

import com.lwos.geometry.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathToolRevisionTest {

    @Test
    void mutationsBumpRevision() {
        PathTool t = new PathTool();
        long r0 = t.revision();
        t.addPoint(new Vec3d(0, 64, 0));
        long r1 = t.revision();
        assertTrue(r1 > r0);
        t.setWidth(t.width() + 1.0);
        assertTrue(t.revision() > r1);
    }
}
