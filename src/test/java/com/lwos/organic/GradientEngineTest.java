package com.lwos.organic;

import com.lwos.plan.BlockStateRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless proof that GradientEngine assigns blocks from a Palette in clustered, contiguous
 * patches driven by low-frequency noise -- not per-block static -- and that every Palette.Entry
 * field (weight, noiseScale, clusterSize) genuinely influences the result (M5 plan, Task 3,
 * Step 3).
 */
class GradientEngineTest {

    private static final BlockStateRef DIRT_PATH = new BlockStateRef("minecraft:dirt_path");
    private static final BlockStateRef COARSE_DIRT = new BlockStateRef("minecraft:coarse_dirt");
    private static final BlockStateRef GRAVEL = new BlockStateRef("minecraft:gravel");

    @Test
    void adjacentColumnsFormClusteredPatchesNotStatic() {
        // Mirrors CellularNoise's clumping proof: over a large area, most neighbouring columns
        // must share the same assigned block. Per-block independent randomness would almost
        // never repeat this often.
        Palette palette = new Palette(List.of(
                new Palette.Entry(DIRT_PATH, 1.0, 0.1, 6.0),
                new Palette.Entry(COARSE_DIRT, 1.0, 0.1, 6.0)
        ));
        GradientEngine engine = new GradientEngine(1234L, palette);

        int total = 0, sameAsNeighbor = 0;
        for (int x = 0; x < 60; x++) {
            for (int z = 0; z < 60; z++) {
                BlockStateRef here = engine.blockAt(x, 0, z);
                BlockStateRef right = engine.blockAt(x + 1, 0, z);
                total++;
                if (here.equals(right)) sameAsNeighbor++;
            }
        }
        double sharedFraction = (double) sameAsNeighbor / total;
        assertTrue(sharedFraction > 0.6,
                "expected clustered patches, but only " + sharedFraction + " of neighbors matched");
    }

    @Test
    void higherWeightBiasesMajorityOfArea() {
        Palette palette = new Palette(List.of(
                new Palette.Entry(DIRT_PATH, 9.0, 0.1, 5.0),
                new Palette.Entry(COARSE_DIRT, 1.0, 0.1, 5.0)
        ));
        GradientEngine engine = new GradientEngine(42L, palette);

        int total = 0, pathCount = 0;
        for (int x = 0; x < 60; x++) {
            for (int z = 0; z < 60; z++) {
                total++;
                if (engine.blockAt(x, 0, z).equals(DIRT_PATH)) pathCount++;
            }
        }
        double pathFraction = (double) pathCount / total;
        assertTrue(pathFraction > 0.5,
                "expected the heavily-weighted block to cover a clear majority, got " + pathFraction);
    }

    @Test
    void sameSeedAndPaletteProducesIdenticalGrid() {
        Palette palette = new Palette(List.of(
                new Palette.Entry(DIRT_PATH, 1.0, 0.1, 5.0),
                new Palette.Entry(COARSE_DIRT, 1.0, 0.15, 8.0),
                new Palette.Entry(GRAVEL, 0.5, 0.2, 3.0)
        ));
        GradientEngine a = new GradientEngine(777L, palette);
        GradientEngine b = new GradientEngine(777L, palette);

        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 30; z++) {
                assertEquals(a.blockAt(x, 0, z), b.blockAt(x, 0, z),
                        "identical seed + palette must yield identical assignment at (" + x + "," + z + ")");
            }
        }
    }

    @Test
    void differentSeedsDivergeSomewhere() {
        Palette palette = new Palette(List.of(
                new Palette.Entry(DIRT_PATH, 1.0, 0.1, 5.0),
                new Palette.Entry(COARSE_DIRT, 1.0, 0.15, 8.0),
                new Palette.Entry(GRAVEL, 0.5, 0.2, 3.0)
        ));
        GradientEngine a = new GradientEngine(1L, palette);
        GradientEngine b = new GradientEngine(2L, palette);

        int differences = 0;
        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 30; z++) {
                if (!a.blockAt(x, 0, z).equals(b.blockAt(x, 0, z))) differences++;
            }
        }
        assertTrue(differences > 0, "different seeds must produce a different assignment somewhere");
    }

    @Test
    void clusterSizeChangesPatchGranularity() {
        // A tiny clusterSize should produce much smaller, more numerous patches (more transitions
        // between neighbours) than a huge clusterSize over the same area -- proving clusterSize
        // genuinely drives patch scale rather than being a dead field.
        Palette fineGrained = new Palette(List.of(
                new Palette.Entry(DIRT_PATH, 1.0, 0.1, 1.0),
                new Palette.Entry(COARSE_DIRT, 1.0, 0.1, 1.0)
        ));
        Palette coarseGrained = new Palette(List.of(
                new Palette.Entry(DIRT_PATH, 1.0, 0.1, 40.0),
                new Palette.Entry(COARSE_DIRT, 1.0, 0.1, 40.0)
        ));

        int fineTransitions = countTransitions(new GradientEngine(55L, fineGrained));
        int coarseTransitions = countTransitions(new GradientEngine(55L, coarseGrained));

        assertTrue(fineTransitions > coarseTransitions,
                "smaller clusterSize must yield more, smaller patches (more transitions) than a huge clusterSize; "
                        + "fine=" + fineTransitions + " coarse=" + coarseTransitions);
    }

    @Test
    void noiseScaleChangesBoundaryJitter() {
        // Two palettes differing only in noiseScale must (over a large area) disagree at some
        // columns -- proving noiseScale genuinely perturbs the boundary rather than being unused.
        Palette lowJitter = new Palette(List.of(
                new Palette.Entry(DIRT_PATH, 1.0, 0.01, 6.0),
                new Palette.Entry(COARSE_DIRT, 1.0, 0.01, 6.0)
        ));
        Palette highJitter = new Palette(List.of(
                new Palette.Entry(DIRT_PATH, 1.0, 0.9, 6.0),
                new Palette.Entry(COARSE_DIRT, 1.0, 0.9, 6.0)
        ));

        GradientEngine a = new GradientEngine(9001L, lowJitter);
        GradientEngine b = new GradientEngine(9001L, highJitter);

        int differences = 0;
        for (int x = 0; x < 60; x++) {
            for (int z = 0; z < 60; z++) {
                if (!a.blockAt(x, 0, z).equals(b.blockAt(x, 0, z))) differences++;
            }
        }
        assertTrue(differences > 0, "changing noiseScale must measurably change the assignment somewhere");
    }

    private static int countTransitions(GradientEngine engine) {
        int transitions = 0;
        for (int x = 0; x < 60; x++) {
            for (int z = 0; z < 60; z++) {
                BlockStateRef here = engine.blockAt(x, 0, z);
                BlockStateRef right = engine.blockAt(x + 1, 0, z);
                if (!here.equals(right)) transitions++;
            }
        }
        return transitions;
    }
}
