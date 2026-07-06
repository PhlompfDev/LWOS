package com.lwos.config;

import com.lwos.organic.Palette;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathStyleTest {

    @Test
    void neutralIsATrueIdentity() {
        PathStyle s = PathStyle.neutral();
        assertEquals(0.0, s.edgeErosion(), "neutral does not erode the edge");
        assertEquals(0.0, s.blendDepth(), "neutral does not feather");
        assertEquals(0.0, s.edgeCoverage(), "neutral scatters no edge blocks");
        assertEquals(0.0, s.edgeReach(), "neutral does not reach onto terrain");
        assertEquals(1.0, s.coreProtect(), "neutral protects the whole footprint");
        assertEquals(1, s.core().size());
        assertEquals("minecraft:dirt_path", s.core().get(0).id());
        assertTrue(s.edge().isEmpty(), "neutral has no edge shoulder");
        assertTrue(s.toEdgePalette().isEmpty());
    }

    @Test
    void defaultsHaveCoreAndEdgePalettes() {
        PathStyle s = PathStyle.defaults();
        assertTrue(s.core().size() > 1, "defaults offer clustered core materials");
        assertFalse(s.edge().isEmpty(), "defaults include an edge shoulder");
        assertTrue(s.blendDepth() > 0, "defaults feather the edge");
        assertTrue(s.edgeErosion() > 0, "defaults erode the edge");
        assertTrue(s.edgeCoverage() > 0, "defaults scatter edge blocks");
        assertTrue(s.edgeReach() > 0, "defaults meld onto surrounding terrain");
        assertTrue(s.coreProtect() > 0 && s.coreProtect() < 1, "defaults keep a protected spine but allow a band");
        assertTrue(s.toEdgePalette().isPresent());
    }

    @Test
    void jsonRoundTripPreservesEverything() {
        PathStyle before = PathStyle.defaults();
        PathStyle after = PathStyle.fromJson(before.toJson());
        assertEquals(before, after, "round-trip must be lossless and value-equal");
    }

    @Test
    void fromJsonRejectsNonPositiveWeightEagerly() {
        String bad = """
                { "core": [ {"id":"minecraft:dirt_path","weight":0.0,"noiseScale":0.1,"clusterSize":5.0} ] }
                """;
        assertThrows(IllegalArgumentException.class, () -> PathStyle.fromJson(bad));
    }

    @Test
    void toCorePaletteMapsEveryEntry() {
        PathStyle s = PathStyle.defaults();
        Palette p = s.toCorePalette();
        assertEquals(s.core().size(), p.entries().size());
        assertEquals(s.core().get(0).id(), p.entries().get(0).block().id());
    }
}
