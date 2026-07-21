package com.lwos.shape;

/**
 * The click-driven shapes (spec §2; free-placement revision 2026-07-21). Pure — no
 * Minecraft imports. clickCount is the number of anchor clicks that define the shape
 * (the final click both defines the last anchor and commits). RECT replaced the old
 * WALL/FLOOR pair: corners are free 3D points and the rectangle's plane is inferred
 * from them (ShapeGeometry.rectAuto), so one tool covers floors, ceilings, and walls.
 */
public enum ShapeMode {
    LINE("Line", 2, false),
    RECT("Rect", 2, true),
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
