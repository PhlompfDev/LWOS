package com.lwos.geometry;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PathMaskTest {

    @Test
    void singlePointDiscCoversExpectedColumns() {
        // One sample at (0,y,0) with width 2 -> radius 1. Column (0,0) center is (0.5,0.5), distance ~0.707 < 1 -> inside.
        List<PathSample> samples = List.of(new PathSample(new Vec3d(0, 64, 0), 2.0));
        PathMask mask = PathMask.build(samples);
        assertTrue(mask.isInside(0, 0));
        assertTrue(mask.edgeDistance(0, 0) <= 0.0);
    }

    @Test
    void farAwayColumnIsOutside() {
        List<PathSample> samples = List.of(new PathSample(new Vec3d(0, 64, 0), 2.0));
        PathMask mask = PathMask.build(samples);
        assertFalse(mask.isInside(100, 100));
        assertEquals(Double.POSITIVE_INFINITY, mask.edgeDistance(100, 100));
    }

    @Test
    void emptySamplesGiveEmptyMask() {
        PathMask mask = PathMask.build(List.of());
        assertTrue(mask.insideColumns().isEmpty());
        assertFalse(mask.isInside(0, 0));
    }

    @Test
    void wideStraightLineCoversAFullBand() {
        List<PathSample> samples = List.of(
                new PathSample(new Vec3d(0, 64, 0), 6.0),
                new PathSample(new Vec3d(10, 64, 0), 6.0));
        PathMask mask = PathMask.build(samples);
        Set<ColumnPos> inside = mask.insideColumns();
        // Radius 3 around a line from x=0..10 at z=0: column (5, 0) and (5, 2) should both be inside; (5, 10) should not.
        assertTrue(inside.contains(new ColumnPos(5, 0)));
        assertTrue(inside.contains(new ColumnPos(5, 2)));
        assertFalse(inside.contains(new ColumnPos(5, 10)));
    }

    @Test
    void haloParameterTracksColumnsFurtherOutsideTheEdge() {
        // One sample, width 2 -> radius 1. Column (3,0) center (3.5,0.5) is ~2.54 blocks outside the
        // disc edge: tracked only when the halo reaches it, never "inside" either way.
        List<PathSample> samples = List.of(new PathSample(new Vec3d(0, 64, 0), 2.0));

        PathMask narrow = PathMask.build(samples, 0.5);
        PathMask wide = PathMask.build(samples, 3.0);

        assertFalse(narrow.isInside(3, 0));
        assertFalse(wide.isInside(3, 0));
        assertEquals(Double.POSITIVE_INFINITY, narrow.edgeDistance(3, 0),
                "a far column must be untracked under a small halo");
        assertTrue(Double.isFinite(wide.edgeDistance(3, 0)),
                "a wide halo must track columns further outside the edge");
    }
}
