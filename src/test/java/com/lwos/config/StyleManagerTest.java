package com.lwos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StyleManagerTest {

    @Test
    void setActiveBumpsVersionAndReplacesStyle(@TempDir Path dir) {
        StyleManager.useBaseDir(dir);
        long v0 = StyleManager.version();
        PathStyle neutral = PathStyle.neutral();
        StyleManager.setActive(neutral);
        assertEquals(neutral, StyleManager.active());
        assertTrue(StyleManager.version() > v0, "setActive must bump the version");
    }

    @Test
    void presetRoundTripsThroughDisk(@TempDir Path dir) {
        StyleManager.useBaseDir(dir);
        PathStyle style = PathStyle.defaults();
        StyleManager.savePreset("muddy_trail", style);
        assertTrue(StyleManager.presetNames().contains("muddy_trail"));
        Optional<PathStyle> loaded = StyleManager.loadPreset("muddy_trail");
        assertTrue(loaded.isPresent());
        assertEquals(style, loaded.get());
    }

    @Test
    void loadMissingPresetReturnsEmpty(@TempDir Path dir) {
        StyleManager.useBaseDir(dir);
        assertTrue(StyleManager.loadPreset("does_not_exist").isEmpty());
    }
}
