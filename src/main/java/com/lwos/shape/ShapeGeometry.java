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
     * Free-corner rectangle with depth (2026-07-21 revision, depth added same day):
     * infers the plane from two arbitrary 3D corners — an agreeing axis wins, otherwise
     * the smallest |delta| axis (ties prefer Y, then X) is the plane normal — and the
     * corners' span along that normal is the slab's THICKNESS: clicking the far corner
     * two blocks out of the wall plane yields the same wall, that many blocks thick
     * (corner-inclusive, like every other span in the tool). Hollow applies per layer,
     * so a hollow thick wall is its perimeter frame extruded through the depth.
     */
    public static List<GridPos> rectAuto(GridPos a, GridPos b, boolean hollow) {
        b = clampToExtent(a, b);
        int axis = rectFixedAxis(a, b);
        List<GridPos> base = rect(a, collapseToPlane(a, b), hollow);
        int from = axisCoord(a, axis), to = axisCoord(b, axis);
        if (from == to) return base;
        List<GridPos> out = new ArrayList<>();
        int s = Integer.signum(to - from);
        for (int c = from; c != to + s; c += s) {
            for (GridPos p : base) out.add(withAxisCoord(p, axis, c));
        }
        return out;
    }

    private static int axisCoord(GridPos p, int axis) {
        return switch (axis) { case 0 -> p.x(); case 1 -> p.y(); default -> p.z(); };
    }

    private static GridPos withAxisCoord(GridPos p, int axis, int c) {
        return switch (axis) {
            case 0 -> new GridPos(c, p.y(), p.z());
            case 1 -> new GridPos(p.x(), c, p.z());
            default -> new GridPos(p.x(), p.y(), c);
        };
    }

    /**
     * The fixed-plane axis {@link #rectAuto} would use for these corners: 0=X, 1=Y, 2=Z.
     * Shared with cube so its extrusion runs perpendicular to the base plane.
     */
    public static int rectFixedAxis(GridPos a, GridPos b) {
        int dx = Math.abs(b.x() - a.x()), dy = Math.abs(b.y() - a.y()), dz = Math.abs(b.z() - a.z());
        if (dy == 0) return 1; // agreement wins outright, in rect()'s own priority order
        if (dx == 0) return 0;
        if (dz == 0) return 2;
        if (dy <= dx && dy <= dz) return 1; // no agreement: collapse the smallest delta
        if (dx <= dz) return 0;
        return 2;
    }

    /** Collapses b's {@link #rectFixedAxis} coordinate to a's value. */
    public static GridPos collapseToPlane(GridPos a, GridPos b) {
        return switch (rectFixedAxis(a, b)) {
            case 0 -> new GridPos(a.x(), b.y(), b.z());
            case 1 -> new GridPos(b.x(), a.y(), b.z());
            default -> new GridPos(b.x(), b.y(), a.z());
        };
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

    /**
     * Circle on the plane whose normal is {@code axisOrdinal} (0=X, 1=Y, 2=Z): ring
     * (hollow) or disc (filled). Axis Y is the classic horizontal circle; X/Z give
     * vertical circles on walls (free-placement revision).
     */
    public static List<GridPos> circle(GridPos center, int radius, boolean hollow, int axisOrdinal) {
        radius = Math.min(Math.abs(radius), MAX_EXTENT);
        List<GridPos> out = new ArrayList<>();
        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                double dist = Math.sqrt((double) u * u + (double) v * v);
                boolean inside = dist <= radius + 0.5;
                boolean onRing = inside && dist >= radius - 0.5;
                if (hollow ? onRing : inside)
                    out.add(inPlane(center, axisOrdinal, u, v));
            }
        }
        return out;
    }

    /** Maps in-plane offsets (u,v) around center onto world axes for a plane normal. */
    private static GridPos inPlane(GridPos c, int axisOrdinal, int u, int v) {
        return switch (axisOrdinal) {
            case 0 -> new GridPos(c.x(), c.y() + v, c.z() + u);  // normal X: plane spans Z,Y
            case 2 -> new GridPos(c.x() + u, c.y() + v, c.z());  // normal Z: plane spans X,Y
            default -> new GridPos(c.x() + u, c.y(), c.z() + v); // normal Y: plane spans X,Z
        };
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
