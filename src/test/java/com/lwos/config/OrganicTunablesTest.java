package com.lwos.config;

import com.lwos.organic.Palette;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrganicTunablesTest {

    @Test
    void neutralIsATrueIdentity() {
        OrganicTunables t = OrganicTunables.neutral();
        assertEquals(0.0, t.edgeErosionFactor(), "neutral must apply no edge erosion (identity)");
        assertEquals(0, t.blendSkirtWidth(), "neutral must not feather any columns");
        assertEquals(1, t.palette().size(), "neutral must have a single palette entry");
        assertEquals("minecraft:dirt_path", t.palette().get(0).id(),
                "neutral must reproduce the pre-M5 dirt_path block");

        // A single-entry palette always yields that block through the GradientEngine.
        Palette p = t.toPalette();
        assertEquals(1, p.entries().size());
        assertEquals("minecraft:dirt_path", p.entries().get(0).block().id());
    }

    @Test
    void defaultsAreTheRealOrganicLook() {
        OrganicTunables t = OrganicTunables.defaults();
        assertTrue(t.edgeErosionFactor() > 0.0, "defaults must wobble the edge");
        assertTrue(t.blendSkirtWidth() > 0, "defaults must feather");
        assertTrue(t.palette().size() > 1, "defaults must offer clustered materials (multi-entry palette)");
    }

    @Test
    void parsesFromJsonString() {
        String json = """
                {
                  "edgeErosionFactor": 2.5,
                  "edgeNoiseScale": 0.12,
                  "blendSkirtWidth": 3,
                  "defaultClusterSize": 6.0,
                  "palette": [
                    {"id": "minecraft:dirt_path", "weight": 3.0, "noiseScale": 0.1, "clusterSize": 5.0},
                    {"id": "minecraft:coarse_dirt", "weight": 1.0, "noiseScale": 0.1, "clusterSize": 5.0}
                  ]
                }
                """;
        OrganicTunables t = OrganicTunables.fromJson(json);
        assertEquals(2.5, t.edgeErosionFactor());
        assertEquals(0.12, t.edgeNoiseScale());
        assertEquals(3, t.blendSkirtWidth());
        assertEquals(6.0, t.defaultClusterSize());
        assertEquals(2, t.palette().size());
        assertEquals("minecraft:coarse_dirt", t.palette().get(1).id());
        assertEquals(3.0, t.palette().get(0).weight());
    }

    @Test
    void toPaletteMapsEveryEntry() {
        OrganicTunables t = OrganicTunables.defaults();
        Palette p = t.toPalette();
        assertEquals(t.palette().size(), p.entries().size());
        List<Palette.Entry> entries = p.entries();
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(t.palette().get(i).id(), entries.get(i).block().id());
            assertEquals(t.palette().get(i).weight(), entries.get(i).weight());
            assertEquals(t.palette().get(i).clusterSize(), entries.get(i).clusterSize());
        }
    }
}
