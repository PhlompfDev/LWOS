package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PathSamplerTest {
    private static final double EPS = 1e-6;

    @Test
    void resampleStraightLineIsEvenlySpaced() {
        List<Vec3d> line = List.of(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0));
        List<Vec3d> out = PathSampler.resample(line, 2.0);
        // Endpoints preserved.
        assertEquals(0.0, out.get(0).x(), EPS);
        assertEquals(10.0, out.get(out.size() - 1).x(), EPS);
        // Interior spacing ~2.0 (last step may be shorter).
        for (int i = 0; i < out.size() - 2; i++) {
            assertEquals(2.0, out.get(i).distance(out.get(i + 1)), 1e-3);
        }
    }

    @Test
    void spacingLargerThanLengthGivesEndpointsOnly() {
        List<Vec3d> line = List.of(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0));
        List<Vec3d> out = PathSampler.resample(line, 5.0);
        assertEquals(2, out.size());
        assertEquals(new Vec3d(0, 0, 0), out.get(0));
        assertEquals(new Vec3d(1, 0, 0), out.get(1));
    }

    @Test
    void emptyAndSinglePolyline() {
        assertTrue(PathSampler.resample(List.of(), 1.0).isEmpty());
        Vec3d p = new Vec3d(2, 2, 2);
        assertEquals(List.of(p), PathSampler.resample(List.of(p), 1.0));
    }

    @Test
    void sampleFromControlPointsHitsEndpoints() {
        List<Vec3d> out = PathSampler.sample(
                List.of(new Vec3d(0, 0, 0), new Vec3d(5, 3, 0), new Vec3d(10, 0, 0)), 0.5);
        assertTrue(out.size() > 3);
        assertEquals(0.0, out.get(0).x(), EPS);
        assertEquals(10.0, out.get(out.size() - 1).x(), EPS);
    }
}
