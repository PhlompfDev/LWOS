package com.lwos.shape;

import com.lwos.plan.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure deterministic shape rasterizers (spec §4). No RNG anywhere — identical inputs
 * always produce identical, insertion-ordered position lists (iteration order is part
 * of the determinism contract because EditPlan preserves insertion order).
 */
public final class ShapeGeometry {
    /** Max blocks any axis may span from the anchor/center (spec §2 cap). */
    public static final int MAX_EXTENT = 64;

    private ShapeGeometry() { }

    /** Straight axis-aligned run from a toward b along the dominant delta axis. */
    public static List<GridPos> line(GridPos a, GridPos b) {
        b = clampToExtent(a, b);
        int dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        List<GridPos> out = new ArrayList<>();
        if (ax >= ay && ax >= az) {
            int s = Integer.signum(dx) == 0 ? 1 : Integer.signum(dx);
            for (int x = a.x(); x != b.x() + s; x += s) { out.add(new GridPos(x, a.y(), a.z())); if (dx == 0) break; }
        } else if (ay >= az) {
            int s = Integer.signum(dy);
            for (int y = a.y(); y != b.y() + s; y += s) out.add(new GridPos(a.x(), y, a.z()));
        } else {
            int s = Integer.signum(dz);
            for (int z = a.z(); z != b.z() + s; z += s) out.add(new GridPos(a.x(), a.y(), z));
        }
        return out;
    }

    /**
     * Axis-aligned rectangle between two corners. The fixed axis is the one where the
     * corners agree (Y fixed = floor, X or Z fixed = wall); if several agree, the first
     * of Y, X, Z wins (degenerate rectangles collapse to lines/points).
     */
    public static List<GridPos> rect(GridPos a, GridPos b, boolean hollow) {
        b = clampToExtent(a, b);
        List<GridPos> out = new ArrayList<>();
        if (a.y() == b.y()) { // floor: spans X and Z
            int y = a.y();
            int x0 = Math.min(a.x(), b.x()), x1 = Math.max(a.x(), b.x());
            int z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++)
                    if (!hollow || x == x0 || x == x1 || z == z0 || z == z1)
                        out.add(new GridPos(x, y, z));
        } else if (a.x() == b.x()) { // wall in the ZY plane
            int x = a.x();
            int z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
            int y0 = Math.min(a.y(), b.y()), y1 = Math.max(a.y(), b.y());
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++)
                    if (!hollow || z == z0 || z == z1 || y == y0 || y == y1)
                        out.add(new GridPos(x, y, z));
        } else { // wall in the XY plane (z fixed to the anchor's z)
            int z = a.z();
            int x0 = Math.min(a.x(), b.x()), x1 = Math.max(a.x(), b.x());
            int y0 = Math.min(a.y(), b.y()), y1 = Math.max(a.y(), b.y());
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    if (!hollow || x == x0 || x == x1 || y == y0 || y == y1)
                        out.add(new GridPos(x, y, z));
        }
        return out;
    }

    /** Solid box or 1-thick shell between opposite corners. */
    public static List<GridPos> box(GridPos a, GridPos b, boolean hollow) {
        b = clampToExtent(a, b);
        int x0 = Math.min(a.x(), b.x()), x1 = Math.max(a.x(), b.x());
        int y0 = Math.min(a.y(), b.y()), y1 = Math.max(a.y(), b.y());
        int z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
        List<GridPos> out = new ArrayList<>();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    if (!hollow || x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1)
                        out.add(new GridPos(x, y, z));
        return out;
    }

    /** XZ-plane circle at center.y: ring (hollow) or disc (filled). */
    public static List<GridPos> circle(GridPos center, int radius, boolean hollow) {
        radius = Math.min(Math.abs(radius), MAX_EXTENT);
        List<GridPos> out = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt((double) x * x + (double) z * z);
                boolean inside = dist <= radius + 0.5;
                boolean onRing = inside && dist >= radius - 0.5;
                if (hollow ? onRing : inside)
                    out.add(new GridPos(center.x() + x, center.y(), center.z() + z));
            }
        }
        return out;
    }

    /** Sphere shell (hollow) or ball (filled) around center. */
    public static List<GridPos> sphere(GridPos center, int radius, boolean hollow) {
        radius = Math.min(Math.abs(radius), MAX_EXTENT);
        List<GridPos> out = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
                    boolean inside = dist <= radius + 0.5;
                    boolean onShell = inside && Math.round(dist) == radius;
                    if (hollow ? onShell : inside)
                        out.add(new GridPos(center.x() + x, center.y() + y, center.z() + z));
                }
            }
        }
        return out;
    }

    /** Clamps b so no axis is farther than MAX_EXTENT from a. */
    private static GridPos clampToExtent(GridPos a, GridPos b) {
        return new GridPos(
                a.x() + clamp(b.x() - a.x()),
                a.y() + clamp(b.y() - a.y()),
                a.z() + clamp(b.z() - a.z()));
    }

    private static int clamp(int d) {
        return Math.max(-MAX_EXTENT, Math.min(MAX_EXTENT, d));
    }
}
