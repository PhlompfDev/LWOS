package com.lwos.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Client-side holder of the active working {@link PathStyle} plus a monotonic version counter and
 * named-preset persistence. This is the single home for mutable global state and file IO, so
 * {@link com.lwos.plan.EditPlanBuilder} stays pure. Mirrors the old {@code OrganicTunables.current()}
 * split. The preview cache reads {@link #version()} to know when to rebuild.
 */
public final class StyleManager {

    private static volatile Path baseDir = Path.of("config", "lwos");
    private static volatile PathStyle active = PathStyle.defaults();
    private static volatile long version = 1;

    private StyleManager() { }

    /** Overrides the on-disk base directory (tests point this at a @TempDir). */
    public static synchronized void useBaseDir(Path base) {
        baseDir = base;
        active = PathStyle.defaults();
        version = 1;
    }

    public static PathStyle active() { return active; }

    public static long version() { return version; }

    /** Replaces the working style, bumps the version, and persists it. Never throws on IO failure. */
    public static synchronized void setActive(PathStyle style) {
        active = style;
        version++;
        try {
            Files.createDirectories(baseDir);
            Files.writeString(workingFile(), style.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[LWOS] Failed to persist working style: " + e);
        }
    }

    public static Path stylesDir() { return baseDir.resolve("styles"); }
    public static Path workingFile() { return baseDir.resolve("working-style.json"); }

    public static synchronized void savePreset(String name, PathStyle style) {
        try {
            Files.createDirectories(stylesDir());
            Files.writeString(stylesDir().resolve(name + ".json"), style.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[LWOS] Failed to save preset '" + name + "': " + e);
        }
    }

    public static Optional<PathStyle> loadPreset(String name) {
        Path file = stylesDir().resolve(name + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(PathStyle.fromJson(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            System.err.println("[LWOS] Failed to load preset '" + name + "': " + e);
            return Optional.empty();
        }
    }

    public static List<String> presetNames() {
        if (!Files.exists(stylesDir())) return List.of();
        try (Stream<Path> files = Files.list(stylesDir())) {
            List<String> names = new ArrayList<>();
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                 .forEach(p -> {
                     String f = p.getFileName().toString();
                     names.add(f.substring(0, f.length() - ".json".length()));
                 });
            names.sort(String::compareTo);
            return names;
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Deletes a named preset file. Returns true if a file was removed. Never throws on IO failure. */
    public static synchronized boolean deletePreset(String name) {
        try {
            return Files.deleteIfExists(stylesDir().resolve(name + ".json"));
        } catch (IOException e) {
            System.err.println("[LWOS] Failed to delete preset '" + name + "': " + e);
            return false;
        }
    }

    /**
     * Renames a preset file, refusing to overwrite an existing target. Returns true on success.
     * Never throws on IO failure.
     */
    public static synchronized boolean renamePreset(String oldName, String newName) {
        Path from = stylesDir().resolve(oldName + ".json");
        Path to = stylesDir().resolve(newName + ".json");
        if (!Files.exists(from) || Files.exists(to)) return false;
        try {
            Files.move(from, to);
            return true;
        } catch (IOException e) {
            System.err.println("[LWOS] Failed to rename preset '" + oldName + "' -> '" + newName + "': " + e);
            return false;
        }
    }
}
