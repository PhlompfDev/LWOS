package com.lwos.organic;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import com.lwos.plan.BlockStateRef;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EdgeBandEngineTest {

    private static final BlockStateRef COARSE = new BlockStateRef("minecraft:coarse_dirt");
    private static final BlockStateRef MOSS = new BlockStateRef("minecraft:moss_block");

    private static Palette edgePalette() {
        return new Palette(List.of(
                new Palette.Entry(COARSE, 1.0, 0.1, 5.0),
                new Palette.Entry(MOSS, 0.6, 0.1, 5.0)));
    }

    /** A field where column x has signed distance d = -x (so x in 1..2 is the band for skirt=3, roughly). */
    private static PathMask bandMask() {
        Map<ColumnPos, Double> dist = new HashMap<>();
        for (int x = -5; x <= 5; x++) dist.put(new ColumnPos(x, 0), (double) -x);
        return PathMask.of(dist);
    }

    @Test
    void outsideColumnsAreNeverEdgeBlocks() {
        EdgeBandEngine e = new EdgeBandEngine(1L, 3, edgePalette());
        // x = -1 -> d = 1 (outside the path); must be empty.
        assertTrue(e.edgeBlockAt(bandMask(), -1, 0).isEmpty());
    }

    @Test
    void deepInteriorIsNeverEdgeBlock() {
        EdgeBandEngine e = new EdgeBandEngine(1L, 3, edgePalette());
        // x = 5 -> d = -5 <= -skirt(3): core territory, not the shoulder.
        assertTrue(e.edgeBlockAt(bandMask(), 5, 0).isEmpty());
    }

    @Test
    void bandColumnsSometimesGetEdgeBlocksFromThePalette() {
        EdgeBandEngine e = new EdgeBandEngine(7L, 3, edgePalette());
        int placed = 0;
        for (int z = 0; z < 200; z++) {
            Map<ColumnPos, Double> dist = new HashMap<>();
            dist.put(new ColumnPos(2, z), -2.0); // firmly in the band for skirt 3
            Optional<BlockStateRef> b = e.edgeBlockAt(PathMask.of(dist), 2, z);
            if (b.isPresent()) {
                assertTrue(b.get().equals(COARSE) || b.get().equals(MOSS));
                placed++;
            }
        }
        assertTrue(placed > 0, "band columns must sometimes receive an edge block");
    }

    @Test
    void fillProbabilityFadesTowardTheRim() {
        EdgeBandEngine e = new EdgeBandEngine(99L, 4, edgePalette());
        int innerHits = countFill(e, -3.5, 4); // near inner band edge -> denser
        int rimHits = countFill(e, -0.5, 4);   // near the rim -> sparser
        assertTrue(innerHits > rimHits,
                "shoulder must be denser next to the core and fade at the rim; inner=" + innerHits + " rim=" + rimHits);
    }

    @Test
    void identicalSeedProducesIdenticalDecisions() {
        EdgeBandEngine a = new EdgeBandEngine(5L, 3, edgePalette());
        EdgeBandEngine b = new EdgeBandEngine(5L, 3, edgePalette());
        for (int z = 0; z < 50; z++) {
            Map<ColumnPos, Double> dist = new HashMap<>();
            dist.put(new ColumnPos(1, z), -1.5);
            PathMask m = PathMask.of(dist);
            assertEquals(a.edgeBlockAt(m, 1, z), b.edgeBlockAt(m, 1, z));
        }
    }

    private static int countFill(EdgeBandEngine e, double d, int skirt) {
        int hits = 0;
        for (int z = 0; z < 400; z++) {
            Map<ColumnPos, Double> dist = new HashMap<>();
            dist.put(new ColumnPos(0, z), d);
            if (e.edgeBlockAt(PathMask.of(dist), 0, z).isPresent()) hits++;
        }
        return hits;
    }
}
