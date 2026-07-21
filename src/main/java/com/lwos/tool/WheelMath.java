package com.lwos.tool;

/**
 * Pure sector picking for the radial tool wheel (spec §1): cursor offset from the wheel
 * center -> hovered sector index, with a dead zone near the center and a far cutoff.
 * First sector is centered at the top; sectors advance clockwise. No Minecraft imports.
 */
public final class WheelMath {
    /** Cursor closer than this to the center hovers nothing (gui px). */
    public static final double DEAD_ZONE = 12.0;
    /** Cursor farther than this from the center hovers nothing (gui px). */
    public static final double FAR_ZONE = 90.0;

    private WheelMath() { }

    /**
     * Sector index under the cursor, or -1 for none. dx/dy are cursor minus wheel center
     * (gui px, +y down); count is the number of equal sectors.
     */
    public static int sectorAt(double dx, double dy, int count) {
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < DEAD_ZONE || dist > FAR_ZONE) return -1;
        // atan2 with +y down: 0 at +x, increasing clockwise on screen. Shift so 0 = top.
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        double sectorSpan = 2.0 * Math.PI / count;
        angle += sectorSpan / 2.0;           // sector centers (not edges) sit on the spokes
        while (angle < 0) angle += 2.0 * Math.PI;
        while (angle >= 2.0 * Math.PI) angle -= 2.0 * Math.PI;
        return (int) (angle / sectorSpan) % count;
    }

    /** Angle (radians) of a sector's center spoke: first at the top, clockwise, -PI/2 based. */
    public static double sectorAngle(int index, int count) {
        return (2.0 * Math.PI * index / count) - Math.PI / 2.0;
    }

    /** Shortest signed angular delta a -> b in radians (result in [-PI, PI]). */
    public static double shortestArc(double from, double to) {
        double d = (to - from) % (2.0 * Math.PI);
        if (d > Math.PI) d -= 2.0 * Math.PI;
        if (d < -Math.PI) d += 2.0 * Math.PI;
        return d;
    }
}
