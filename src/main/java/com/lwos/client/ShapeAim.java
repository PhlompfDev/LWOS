package com.lwos.client;

import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeGeometry;
import net.minecraft.world.phys.Vec3;

/**
 * Construction-plane targeting (spec §2): after the first anchor, aim stops raycasting
 * terrain and intersects the camera ray with the shape's construction plane/axis so
 * shapes stretch into open air. All doubles until the final snap; results clamped to
 * the shape extent cap around the anchor.
 */
public final class ShapeAim {
    private static final double EPS = 1e-5;
    /** Cap on the ray parameter so near-parallel aims can't produce kilometer hits. */
    private static final double MAX_T = 256.0;

    private ShapeAim() { }

    /** Endpoint on the axis line (through anchor) the ray passes closest to. */
    public static GridPos aimLine(Vec3 eye, Vec3 look, GridPos anchor) {
        GridPos best = null;
        double bestDist = Double.MAX_VALUE;
        double[][] axes = { {1, 0, 0}, {0, 1, 0}, {0, 0, 1} };
        for (double[] ax : axes) {
            double[] r = closestOnLine(eye, look, anchor, ax[0], ax[1], ax[2]);
            if (r == null) continue;
            if (r[1] < bestDist) {
                bestDist = r[1];
                int d = (int) Math.round(r[0]);
                best = clamp(anchor, new GridPos(
                        anchor.x() + (int) (ax[0] * d),
                        anchor.y() + (int) (ax[1] * d),
                        anchor.z() + (int) (ax[2] * d)));
            }
        }
        return best;
    }

    /** Ray ∩ horizontal plane y = anchor.y (block-center plane), snapped and clamped. */
    public static GridPos aimPlaneY(Vec3 eye, Vec3 look, GridPos anchor) {
        if (Math.abs(look.y) < EPS) return null;
        double t = (anchor.y() + 0.5 - eye.y) / look.y;
        if (t <= 0 || t > MAX_T) return null;
        return clamp(anchor, new GridPos(
                (int) Math.floor(eye.x + look.x * t),
                anchor.y(),
                (int) Math.floor(eye.z + look.z * t)));
    }

    /** Ray ∩ vertical plane through the anchor (normal X or Z), snapped and clamped. */
    public static GridPos aimWall(Vec3 eye, Vec3 look, GridPos anchor, boolean normalIsX) {
        double denom = normalIsX ? look.x : look.z;
        if (Math.abs(denom) < EPS) return null;
        double planeCoord = (normalIsX ? anchor.x() : anchor.z()) + 0.5;
        double t = (planeCoord - (normalIsX ? eye.x : eye.z)) / denom;
        if (t <= 0 || t > MAX_T) return null;
        return clamp(anchor, normalIsX
                ? new GridPos(anchor.x(), (int) Math.floor(eye.y + look.y * t), (int) Math.floor(eye.z + look.z * t))
                : new GridPos((int) Math.floor(eye.x + look.x * t), (int) Math.floor(eye.y + look.y * t), anchor.z()));
    }

    /** Closest-approach Y on the vertical line through corner — cube extrusion height. */
    public static Integer aimHeight(Vec3 eye, Vec3 look, GridPos corner) {
        double[] r = closestOnLine(eye, look, corner, 0, 1, 0);
        if (r == null) return null;
        int dy = (int) Math.round(r[0]);
        dy = Math.max(-ShapeGeometry.MAX_EXTENT, Math.min(ShapeGeometry.MAX_EXTENT, dy));
        return corner.y() + dy;
    }

    /**
     * Closest points between the camera ray and an axis line through base.
     * Returns { line parameter s, distance between closest points }, or null when the
     * closest approach is behind the camera or the ray is parallel to the axis.
     */
    private static double[] closestOnLine(Vec3 eye, Vec3 look, GridPos base, double ax, double ay, double az) {
        double bx = base.x() + 0.5, by = base.y() + 0.5, bz = base.z() + 0.5;
        double wx = eye.x - bx, wy = eye.y - by, wz = eye.z - bz;
        double a = look.lengthSqr();
        double b = look.x * ax + look.y * ay + look.z * az;
        double c = 1.0; // axis is unit
        double d = look.x * wx + look.y * wy + look.z * wz;
        double e = ax * wx + ay * wy + az * wz;
        double denom = a * c - b * b;
        if (Math.abs(denom) < EPS) return null; // ray parallel to the axis
        double t = (b * e - c * d) / denom;     // along the ray
        double s = (a * e - b * d) / denom;     // along the axis line
        if (t <= 0 || t > MAX_T) return null;
        double px = eye.x + look.x * t - (bx + ax * s);
        double py = eye.y + look.y * t - (by + ay * s);
        double pz = eye.z + look.z * t - (bz + az * s);
        return new double[] { s, Math.sqrt(px * px + py * py + pz * pz) };
    }

    private static GridPos clamp(GridPos anchor, GridPos p) {
        int dx = Math.max(-ShapeGeometry.MAX_EXTENT, Math.min(ShapeGeometry.MAX_EXTENT, p.x() - anchor.x()));
        int dy = Math.max(-ShapeGeometry.MAX_EXTENT, Math.min(ShapeGeometry.MAX_EXTENT, p.y() - anchor.y()));
        int dz = Math.max(-ShapeGeometry.MAX_EXTENT, Math.min(ShapeGeometry.MAX_EXTENT, p.z() - anchor.z()));
        return new GridPos(anchor.x() + dx, anchor.y() + dy, anchor.z() + dz);
    }
}
