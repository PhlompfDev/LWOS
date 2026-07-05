package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PathRibbonTest {
    private static final double EPS = 1e-6;

    @Test
    void straightLineAlongXOffsetsInZ() {
        List<PathSample> samples = List.of(
                new PathSample(new Vec3d(0, 0, 0), 4.0),
                new PathSample(new Vec3d(5, 0, 0), 4.0),
                new PathSample(new Vec3d(10, 0, 0), 4.0));
        PathRibbon.Edges edges = PathRibbon.compute(samples);
        assertEquals(3, edges.left().size());
        assertEquals(3, edges.right().size());
        for (int i = 0; i < 3; i++) {
            assertEquals(2.0, edges.left().get(i).z(), EPS);
            assertEquals(-2.0, edges.right().get(i).z(), EPS);
            assertEquals(samples.get(i).position().x(), edges.left().get(i).x(), EPS);
        }
    }

    @Test
    void singleSampleDoesNotCrash() {
        List<PathSample> samples = List.of(new PathSample(new Vec3d(1, 2, 3), 2.0));
        PathRibbon.Edges edges = PathRibbon.compute(samples);
        assertEquals(1, edges.left().size());
        assertEquals(1, edges.right().size());
    }
}
