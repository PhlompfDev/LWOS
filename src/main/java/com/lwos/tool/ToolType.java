package com.lwos.tool;

import com.lwos.shape.ShapeMode;

/**
 * The tools shown in the Alt+Scroll wheel. PATH and TERRAIN have bespoke tools; the six
 * shape entries all route to the shared {@link ShapeTool}, parameterized by shapeMode.
 * iconIndex is the widgets.png icon-set-v2 slot (16x16 glyphs in one row at v=96,
 * u = index*16) — kept explicit rather than ordinal() so sheet layout and enum order
 * can evolve independently (rows 1-2 hold the retired v1 glyphs, unused).
 */
public enum ToolType {
    PATH("Path", 0, null),
    TERRAIN("Terrain", 1, null),
    LINE("Line", 2, ShapeMode.LINE),
    RECT("Rect", 4, ShapeMode.RECT),   // floor-diamond glyph reads "plane"; wall glyph (3) retired
    CUBE("Cube", 5, ShapeMode.CUBE),
    CIRCLE("Circle", 6, ShapeMode.CIRCLE),
    SPHERE("Sphere", 7, ShapeMode.SPHERE);

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
