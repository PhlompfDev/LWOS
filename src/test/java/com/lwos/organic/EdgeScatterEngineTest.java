package com.lwos.organic;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import com.lwos.plan.BlockStateRef;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EdgeScatterEngineTest {

    private static final long SEED = 0xC0FFEEL;

    /** A straight path along the x-axis centred on z=0 with the given half-width; signed distance is
     *  |z_center| - halfWidth. Tracks columns from -pad..+pad blocks beyond the rim on each side. */
    private static PathMask straightBand(double halfWidth, int pad) {
        Map<ColumnPos, Double> d = new HashMap<>();
        int zLimit = (int) Math.ceil(halfWidth) + pad + 1;
        for (int x = 0; x <= 20; x++) {
            for (int z = -zLimit; z <= zLimit; z++) {
                d.put(new ColumnPos(x, z), Math.abs(z + 0.5) - halfWidth);
            }
        }
        return PathMask.of(d);
    }

    private static Palette edgePalette() {
        return new Palette(java.util.List.of(
                new Palette.Entry(new BlockStateRef("minecraft:coarse_dirt"), 1.0, 0.1, 4.0)));
    }

    private static Set<ColumnPos> scattered(EdgeScatterEngine e, PathMask mask) {
        Set<ColumnPos> hits = new HashSet<>();
        for (ColumnPos c : mask.edgeDistances().keySet()) {
            if (e.scatterBlockAt(mask, c.x(), c.z()).isPresent()) hits.add(c);
        }
        return hits;
    }

    @Test
    void zeroCoveragePlacesNothing() {
        PathMask mask = straightBand(2.0, 3);
        EdgeScatterEngine e = new EdgeScatterEngine(SEED, 2.0, 3.0, 0.0, 4.0, edgePalette());
        assertTrue(scattered(e, mask).isEmpty(), "coverage 0 must place no edge blocks");
    }

    @Test
    void coverageIsMonotonic() {
        PathMask mask = straightBand(2.0, 3);
        int low = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.3, 4.0, edgePalette()), mask).size();
        int high = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 1.0, 4.0, edgePalette()), mask).size();
        assertTrue(high >= low, "more coverage must never scatter fewer blocks (" + high + " >= " + low + ")");
        assertTrue(high > 0, "full coverage must scatter some blocks");
    }

    @Test
    void scatterIsSparseNotSolid() {
        PathMask mask = straightBand(2.0, 3);
        EdgeScatterEngine e = new EdgeScatterEngine(SEED, 2.0, 3.0, 0.5, 4.0, edgePalette());
        Set<ColumnPos> hits = scattered(e, mask);
        long candidates = mask.edgeDistances().values().stream()
                .filter(d -> d > -2.0 && d <= 3.0).count();
        assertTrue(hits.size() > 0 && hits.size() < candidates,
                "scatter must be sparse: some but not all candidates (" + hits.size() + " of " + candidates + ")");
    }

    @Test
    void clusterSizeChangesThePattern() {
        PathMask mask = straightBand(2.0, 3);
        Set<ColumnPos> fine = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.6, 2.0, edgePalette()), mask);
        Set<ColumnPos> broad = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.6, 12.0, edgePalette()), mask);
        assertNotEquals(fine, broad, "changing cluster size must change which columns scatter");
    }

    @Test
    void reachPlacesBlocksOutsideTheRim() {
        PathMask mask = straightBand(2.0, 4);
        EdgeScatterEngine withReach = new EdgeScatterEngine(SEED, 2.0, 4.0, 1.0, 4.0, edgePalette());
        boolean anyOutside = scattered(withReach, mask).stream()
                .anyMatch(c -> mask.edgeDistance(c.x(), c.z()) > 0.0);
        assertTrue(anyOutside, "edgeReach > 0 must scatter blocks onto terrain outside the rim");

        EdgeScatterEngine noReach = new EdgeScatterEngine(SEED, 2.0, 0.0, 1.0, 4.0, edgePalette());
        boolean noneOutside = scattered(noReach, mask).stream()
                .noneMatch(c -> mask.edgeDistance(c.x(), c.z()) > 0.0);
        assertTrue(noneOutside, "edgeReach 0 must place nothing outside the rim");
    }

    @Test
    void deepInteriorAndBeyondReachAreNeverCandidates() {
        Map<ColumnPos, Double> d = new HashMap<>();
        d.put(new ColumnPos(0, 0), -5.0);  // deep inside, past blendDepth
        d.put(new ColumnPos(1, 0), 9.0);   // far outside, past edgeReach
        PathMask mask = PathMask.of(d);
        EdgeScatterEngine e = new EdgeScatterEngine(SEED, 2.0, 3.0, 1.0, 4.0, edgePalette());
        assertEquals(Optional.empty(), e.scatterBlockAt(mask, 0, 0));
        assertEquals(Optional.empty(), e.scatterBlockAt(mask, 1, 0));
    }

    @Test
    void isDeterministic() {
        PathMask mask = straightBand(2.0, 3);
        Set<ColumnPos> a = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.5, 4.0, edgePalette()), mask);
        Set<ColumnPos> b = scattered(new EdgeScatterEngine(SEED, 2.0, 3.0, 0.5, 4.0, edgePalette()), mask);
        assertEquals(a, b, "same seed + inputs must scatter identically");
    }
}
