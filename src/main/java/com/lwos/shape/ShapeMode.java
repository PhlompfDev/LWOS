package com.lwos.shape;

/**
 * The six EB-style click-driven shapes (spec §2). Pure — no Minecraft imports.
 * clickCount is the number of anchor clicks that define the shape (the final
 * click both defines the last anchor and commits).
 */
public enum ShapeMode {
    LINE("Line", 2, false),
    WALL("Wall", 2, true),
    FLOOR("Floor", 2, true),
    CUBE("Cube", 3, true),
    CIRCLE("Circle", 2, true),
    SPHERE("Sphere", 2, true);

    private final String displayName;
    private final int clickCount;
    private final boolean supportsFill;

    ShapeMode(String displayName, int clickCount, boolean supportsFill) {
        this.displayName = displayName;
        this.clickCount = clickCount;
        this.supportsFill = supportsFill;
    }

    public String displayName() { return displayName; }
    public int clickCount() { return clickCount; }
    public boolean supportsFill() { return supportsFill; }
}
