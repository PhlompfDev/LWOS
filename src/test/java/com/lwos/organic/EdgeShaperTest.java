package com.lwos.organic;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless proof that EdgeShaper turns a geometrically perfect (straight-edged) PathMask into
 * an organic one whose boundary wanders under low-frequency noise (M5 plan, Task 2, Step 2).
 *
 * Uses a golden/matrix-style ASCII grid dump so a human reviewer can visually confirm the edge
 * goes from a razor-straight line to a jagged/wavy one.
 */
class EdgeShaperTest {

    private static final int WIDTH = 40;  // x range: 0..39
    private static final int DEPTH = 24;  // z range: 0..23
    private static final int BAND_HALF_WIDTH = 6; // straight band occupies x in [-6, 6) about centerX

    /**
     * Builds a straight vertical band: inside for x in [centerX - half, centerX + half), a thin
     * tracked halo (+0.5) just outside on both sides, and everything else untracked (+INFINITY).
     * This is the "geometrically perfect" input EdgeShaper must wobble.
     */
    private static PathMask buildStraightBand(int centerX, int half) {
        Map<ColumnPos, Double> dist = new HashMap<>();
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                double signedDistToEdge; // negative inside, positive outside, in "columns" units
                int distFromCenter = Math.abs(x - centerX);
                if (distFromCenter < half) {
                    signedDistToEdge = distFromCenter - half; // negative -> inside
                } else {
                    signedDistToEdge = distFromCenter - half; // >= 0 -> outside/edge
                }
                if (signedDistToEdge <= 0.5) {
                    dist.put(new ColumnPos(x, z), signedDistToEdge);
                }
            }
        }
        return PathMask.of(dist);
    }

    /** Renders a PathMask as a 2D ASCII grid: '#' inside, '.' tracked-but-outside, ' ' untracked. */
    private static String render(PathMask mask) {
        StringBuilder sb = new StringBuilder();
        for (int z = 0; z < DEPTH; z++) {
            for (int x = 0; x < WIDTH; x++) {
                double d = mask.edgeDistance(x, z);
                char c;
                if (mask.isInside(x, z)) c = '#';
                else if (d != Double.POSITIVE_INFINITY) c = '.';
                else c = ' ';
                sb.append(c);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Per-row min inside-x (or -1 if that row has no inside column). Constant for a straight band. */
    private static int[] rowMinInsideX(PathMask mask) {
        int[] mins = new int[DEPTH];
        for (int z = 0; z < DEPTH; z++) {
            int min = -1;
            for (int x = 0; x < WIDTH; x++) {
                if (mask.isInside(x, z)) { min = x; break; }
            }
            mins[z] = min;
        }
        return mins;
    }

    @Test
    void straightBandBecomesJaggedAfterShaping() {
        PathMask input = buildStraightBand(20, BAND_HALF_WIDTH);
        EdgeShaper shaper = new EdgeShaper();
        PathMask shaped = shaper.shape(input, 1234L);

        System.out.println("BEFORE (straight edge):");
        System.out.println(render(input));
        System.out.println("AFTER (shaped, seed=1234):");
        System.out.println(render(shaped));

        // The wobbled inside set must differ from the perfectly straight input.
        assertNotEquals(input.insideColumns(), shaped.insideColumns(),
                "shaping a straight edge with noise must change the inside set");

        // Per-row min inside-x must vary across rows (a straight edge has a constant min).
        int[] mins = rowMinInsideX(shaped);
        boolean varies = false;
        int first = -2;
        for (int m : mins) {
            if (m == -1) continue;
            if (first == -2) first = m;
            else if (m != first) { varies = true; break; }
        }
        assertTrue(varies, "shaped edge must wander row-to-row instead of staying a constant-x straight line");
    }

    @Test
    void shapingIsDeterministicForSameSeed() {
        PathMask input = buildStraightBand(20, BAND_HALF_WIDTH);
        EdgeShaper shaper = new EdgeShaper();

        PathMask a = shaper.shape(input, 999L);
        PathMask b = shaper.shape(input, 999L);

        assertEquals(a.insideColumns(), b.insideColumns(),
                "same seed + same input must yield a byte-identical (here, set-identical) inside region");
        assertEquals(a.edgeDistances(), b.edgeDistances(),
                "same seed + same input must yield an identical distance field");
    }

    @Test
    void shapingDivergesForDifferentSeeds() {
        PathMask input = buildStraightBand(20, BAND_HALF_WIDTH);
        EdgeShaper shaper = new EdgeShaper();

        PathMask a = shaper.shape(input, 1L);
        PathMask b = shaper.shape(input, 2L);

        assertNotEquals(a.insideColumns(), b.insideColumns(),
                "different seeds must produce different boundary wobble");
    }

    @Test
    void shapedMaskPreservesTrackedColumnSet() {
        // EdgeShaper only perturbs distances of already-tracked columns; it must not invent
        // brand-new tracked columns outside the original halo (bulge is bounded by EDGE_HALO).
        PathMask input = buildStraightBand(20, BAND_HALF_WIDTH);
        EdgeShaper shaper = new EdgeShaper();
        PathMask shaped = shaper.shape(input, 42L);

        Set<ColumnPos> inputTracked = input.edgeDistances().keySet();
        Set<ColumnPos> shapedTracked = shaped.edgeDistances().keySet();
        assertEquals(inputTracked, shapedTracked,
                "shaped mask must track exactly the same columns as the input (only distances change)");
    }
}
