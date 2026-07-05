package com.lwos.plan;

/**
 * How an {@link EditPlanBuilder} reconciles the path with the existing terrain (M4).
 *
 * <ul>
 *   <li>{@link #FOLLOW_SURFACE} — the default. The path drapes over the terrain, replacing the
 *       topmost solid block of each column so hills are preserved, not levelled.</li>
 *   <li>{@link #CUT_AND_FILL} — the path enforces its own interpolated elevation, carving
 *       ({@link ChangeKind#REMOVE}) through ground that rises above it and filling
 *       ({@link ChangeKind#PLACE}) below it where the ground drops away.</li>
 * </ul>
 */
public enum TerrainMode {
    FOLLOW_SURFACE("Surface"),
    CUT_AND_FILL("Cut & Fill");

    private final String displayName;

    TerrainMode(String displayName) {
        this.displayName = displayName;
    }

    /** Short label for the HUD indicator (e.g. "Surface", "Cut & Fill"). */
    public String displayName() {
        return displayName;
    }

    /** The mode after this one, wrapping around — used by the toggle keybind. */
    public TerrainMode next() {
        TerrainMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
