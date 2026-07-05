package com.lwos.organic;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless proof that BlendEngine feathers a solid path into its surroundings: deep-interior
 * columns are always kept, the keep-fraction falls off irregularly (noise-driven, not a clean
 * geometric ring) as edgeDistance approaches 0, and the whole thing is deterministic per seed
 * (M5 plan, Task 4, Step 2).
 */
class BlendEngineTest {

    private static final int WIDTH = 60;   // x range: 0..59
    private static final int DEPTH = 60;   // z range: 0..59
    private static final int RADIUS = 25;  // solid disc radius, in blocks
    private static final int SKIRT = 6;    // BlendEngine skirt width N

    /**
     * Builds a solid circular PathMask of the given radius centered in the grid, with a signed
     * distance field: negative deep inside, rising toward 0 at the rim, and a thin +halo band
     * just outside (mirrors PathMask.build's own halo convention).
     */
    private static PathMask buildSolidDisc(int radius) {
        Map<ColumnPos, Double> dist = new HashMap<>();
        int cx = WIDTH / 2;
        int cz = DEPTH / 2;
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                double dx = (x + 0.5) - cx;
                double dz = (z + 0.5) - cz;
                double d = Math.sqrt(dx * dx + dz * dz) - radius;
                if (d <= 0.5) {
                    dist.put(new ColumnPos(x, z), d);
                }
            }
        }
        return PathMask.of(dist);
    }

    private static double keepFractionInBand(Set<ColumnPos> kept, PathMask mask, double loInclusive, double hiInclusive) {
        int total = 0, keptCount = 0;
        for (Map.Entry<ColumnPos, Double> e : mask.edgeDistances().entrySet()) {
            double d = e.getValue();
            if (d > 0.0) continue; // only inside columns are BlendEngine's concern
            if (d > hiInclusive || d < loInclusive) continue;
            total++;
            if (kept.contains(e.getKey())) keptCount++;
        }
        return total == 0 ? Double.NaN : (double) keptCount / total;
    }

    @Test
    void deepInteriorIsAlwaysKept() {
        PathMask mask = buildSolidDisc(RADIUS);
        BlendEngine engine = new BlendEngine(42L, SKIRT);
        Set<ColumnPos> kept = engine.feather(mask);

        double fraction = keepFractionInBand(kept, mask, Double.NEGATIVE_INFINITY, -SKIRT);
        assertEquals(1.0, fraction, 1e-9,
                "columns with edgeDistance <= -N (deep interior) must ALL be kept");
    }

    @Test
    void keepFractionDecreasesTowardTheEdge() {
        PathMask mask = buildSolidDisc(RADIUS);
        BlendEngine engine = new BlendEngine(42L, SKIRT);
        Set<ColumnPos> kept = engine.feather(mask);

        double interior = keepFractionInBand(kept, mask, Double.NEGATIVE_INFINITY, -SKIRT);
        double midSkirt = keepFractionInBand(kept, mask, -SKIRT, -SKIRT / 2.0);
        double nearEdge = keepFractionInBand(kept, mask, -SKIRT / 2.0, 0.0);

        assertFalse(Double.isNaN(midSkirt), "expected tracked columns in the mid-skirt band");
        assertFalse(Double.isNaN(nearEdge), "expected tracked columns in the near-edge band");

        assertEquals(1.0, interior, 1e-9, "interior keep-fraction should be ~1.0");
        assertTrue(midSkirt < interior,
                "mid-skirt keep-fraction (" + midSkirt + ") must be below the interior (" + interior + ")");
        assertTrue(nearEdge < midSkirt,
                "near-edge keep-fraction (" + nearEdge + ") must be below the mid-skirt (" + midSkirt + ")");
        assertTrue(nearEdge < 1.0 && nearEdge >= 0.0, "near-edge fraction must show real dropout");
    }

    @Test
    void outsideAndUntrackedColumnsAreNeverKept() {
        PathMask mask = buildSolidDisc(RADIUS);
        BlendEngine engine = new BlendEngine(42L, SKIRT);
        Set<ColumnPos> kept = engine.feather(mask);

        // Far outside the disc entirely (untracked column) must never appear in the kept set.
        assertFalse(kept.contains(new ColumnPos(0, 0)));
        assertFalse(engine.keepsPathBlock(mask, 0, 0));

        // A tracked-but-outside halo column (distance in (0, 0.5]) must also never be kept.
        for (Map.Entry<ColumnPos, Double> e : mask.edgeDistances().entrySet()) {
            if (e.getValue() > 0.0) {
                assertFalse(kept.contains(e.getKey()),
                        "column " + e.getKey() + " has positive edgeDistance and must never be kept");
            }
        }
    }

    @Test
    void featheringIsIrregularNotACleanRing() {
        // Proof against a pure geometric fade: somewhere in the skirt, a column strictly closer
        // to the edge (larger/less-negative distance) survives while a column strictly deeper
        // (more interior) right next to it drops. A clean radial fade could never do this --
        // keep/drop would be a strict, monotonic function of distance alone with no crossovers.
        PathMask mask = buildSolidDisc(RADIUS);
        BlendEngine engine = new BlendEngine(7L, SKIRT);
        Set<ColumnPos> kept = engine.feather(mask);

        boolean foundCrossover = false;
        for (Map.Entry<ColumnPos, Double> e : mask.edgeDistances().entrySet()) {
            ColumnPos col = e.getKey();
            double d = e.getValue();
            if (d > 0.0 || d <= -SKIRT) continue; // only the skirt band matters here

            // Compare against 4-neighbors within the skirt band.
            int[][] deltas = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] delta : deltas) {
                ColumnPos nb = new ColumnPos(col.x() + delta[0], col.z() + delta[1]);
                double nd = mask.edgeDistance(nb.x(), nb.z());
                if (nd > 0.0 || nd <= -SKIRT || nd == Double.POSITIVE_INFINITY) continue;

                boolean colIsCloserToEdge = d > nd; // less negative = closer to 0 = closer to edge
                boolean colKept = kept.contains(col);
                boolean nbKept = kept.contains(nb);

                if (colIsCloserToEdge && colKept && !nbKept) {
                    foundCrossover = true;
                    break;
                }
            }
            if (foundCrossover) break;
        }

        assertTrue(foundCrossover,
                "expected at least one noise-driven crossover (an edge-closer column surviving while a " +
                        "more-interior neighbor drops) -- a clean geometric fade could never produce this");
    }

    @Test
    void sameSeedAndMaskProduceIdenticalKeptSet() {
        PathMask mask = buildSolidDisc(RADIUS);
        BlendEngine a = new BlendEngine(123L, SKIRT);
        BlendEngine b = new BlendEngine(123L, SKIRT);

        assertEquals(a.feather(mask), b.feather(mask),
                "same seed + same input must yield a byte-identical (set-identical) kept set");
    }

    @Test
    void differentSeedsDivergeSomewhereInTheSkirt() {
        PathMask mask = buildSolidDisc(RADIUS);
        BlendEngine a = new BlendEngine(1L, SKIRT);
        BlendEngine b = new BlendEngine(2L, SKIRT);

        assertNotEquals(a.feather(mask), b.feather(mask),
                "different seeds must produce a different kept set somewhere in the skirt");
    }

    @Test
    void fractionalSkirtWidthFeathersAndKeepsInterior() {
        // A skirt below 1.0 would have been floored to 0 (feathering off) under the old int API.
        // With a double skirt it must engage: deep interior kept, near-edge shows dropout.
        PathMask mask = buildSolidDisc(RADIUS);
        BlendEngine engine = new BlendEngine(42L, 0.75);
        Set<ColumnPos> kept = engine.feather(mask);

        double interior = keepFractionInBand(kept, mask, Double.NEGATIVE_INFINITY, -1.0);
        assertEquals(1.0, interior, 1e-9, "deep interior must be fully kept with a fractional skirt");

        double nearEdge = keepFractionInBand(kept, mask, -0.75, 0.0);
        assertFalse(Double.isNaN(nearEdge), "expected tracked columns near the edge");
        assertTrue(nearEdge < 1.0, "a fractional skirt must still drop some near-edge columns");
    }
}
