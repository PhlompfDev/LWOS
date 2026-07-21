package com.lwos.tool;

import com.lwos.shape.ShapeMode;

/**
 * The tools shown in the Alt+Scroll wheel. PATH and TERRAIN have bespoke tools; the six
 * shape entries all route to the shared {@link ShapeTool}, parameterized by shapeMode.
 * iconIndex is the widgets.png strip slot (16x16 glyphs; indices 0..4 at v=64, 5..8 at
 * v=80) — decoupled from ordinal() so existing sheet art never moves (FILL's old slot 3
 * is orphaned, its bucket art stays in the sheet unused).
 */
public enum ToolType {
    PATH("Path", 0, null),
    TERRAIN("Terrain", 4, null),
    LINE("Line", 1, ShapeMode.LINE),
    WALL("Wall", 5, ShapeMode.WALL),
    FLOOR("Floor", 6, ShapeMode.FLOOR),
    CUBE("Cube", 7, ShapeMode.CUBE),
    CIRCLE("Circle", 2, ShapeMode.CIRCLE),
    SPHERE("Sphere", 8, ShapeMode.SPHERE);

    private final String displayName;
    private final int iconIndex;
    private final ShapeMode shapeMode;

    ToolType(String displayName, int iconIndex, ShapeMode shapeMode) {
        this.displayName = displayName;
        this.iconIndex = iconIndex;
        this.shapeMode = shapeMode;
    }

    public String displayName() { return displayName; }
    public int iconIndex() { return iconIndex; }
    /** The shape this tool drives, or null for the bespoke path/terrain tools. */
    public ShapeMode shapeMode() { return shapeMode; }
}
