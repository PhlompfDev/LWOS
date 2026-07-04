package com.lwos.tool;

/** The tools shown in the Alt+Scroll wheel. Only PATH has behavior in M1. */
public enum ToolType {
    PATH("Path", 0x4CAF50),
    LINE("Line", 0x2196F3),
    CIRCLE("Circle", 0xFFC107),
    FILL("Fill", 0x9C27B0),
    TERRAIN_BLEND("Terrain Blend", 0x795548);

    private final String displayName;
    private final int color;

    ToolType(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() { return displayName; }
    public int color() { return color; }
}
