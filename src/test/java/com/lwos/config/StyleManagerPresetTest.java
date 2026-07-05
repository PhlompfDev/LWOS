package com.lwos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StyleManagerPresetTest {

    @Test
    void deletePresetRemovesItFromTheListing(@TempDir Path dir) {
        StyleManager.useBaseDir(dir);
        StyleManager.savePreset("alpha", PathStyle.defaults());
        StyleManager.savePreset("beta", PathStyle.defaults());
        assertEquals(List.of("alpha", "beta"), StyleManager.presetNames());

        assertTrue(StyleManager.deletePreset("alpha"), "deleting an existing preset returns true");
        assertEquals(List.of("beta"), StyleManager.presetNames());
    }

    @Test
    void deleteMissingPresetReturnsFalse(@TempDir Path dir) {
        StyleManager.useBaseDir(dir);
        assertFalse(StyleManager.deletePreset("ghost"), "deleting a missing preset returns false");
    }

    @Test
    void renamePresetMovesTheFile(@TempDir Path dir) {
        StyleManager.useBaseDir(dir);
        StyleManager.savePreset("old", PathStyle.defaults());

        assertTrue(StyleManager.renamePreset("old", "new"), "rename of an existing preset returns true");
        assertEquals(List.of("new"), StyleManager.presetNames());
        assertTrue(StyleManager.loadPreset("new").isPresent(), "renamed preset is still loadable");
    }

    @Test
    void renameOntoExistingNameFails(@TempDir Path dir) {
        StyleManager.useBaseDir(dir);
        StyleManager.savePreset("a", PathStyle.defaults());
        StyleManager.savePreset("b", PathStyle.defaults());
        assertFalse(StyleManager.renamePreset("a", "b"), "rename must not clobber an existing preset");
        assertEquals(List.of("a", "b"), StyleManager.presetNames());
    }
}
