package com.lwos.client;

import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Free-corner targeting (free-placement revision 2026-07-21). Every corner is a real
 * 3D point: the terrain raycast wins wherever you look (floor, ceiling, wall), and
 * aiming at open air falls back to a point along the view ray at the same distance as
 * the anchor — so shapes stretch through air without being locked to any axis. The
 * old per-mode construction planes (horizontal-only floors, latched wall normals) are
 * gone; shape orientation now comes from the corners themselves (ShapeGeometry.rectAuto)
 * or the clicked face (circle axis).
 */
public final class ShapeAim {
    private static final double EPS = 1e-5;
    private static final double MAX_REACH = 96.0;

    private ShapeAim() { }

    /**
     * Stretch-phase target: terrain hit (face-adjacent for place, hit block for break),
     * or the air point at the eye→anchor distance along the ray. Extent-clamped around
     * the anchor. Null only in degenerate cases.
     */
    public static GridPos freeAim(Minecraft mc, Vec3 eye, Vec3 look, GridPos anchor, boolean forBreak) {
        Vec3 end = eye.add(look.scale(MAX_REACH));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = forBreak ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
            return clamp(anchor, new GridPos(pos.getX(), pos.getY(), pos.getZ()));
        }
        // Open air: hold the anchor's distance so the corner rides the view ray predictably.
        double ax = anchor.x() + 0.5 - eye.x, ay = anchor.y() + 0.5 - eye.y, az = anchor.z() + 0.5 - eye.z;
        double t = Math.max(2.0, Math.sqrt(ax * ax + ay * ay + az * az));
        return clamp(anchor, new GridPos(
                (int) Math.floor(eye.x + look.x * t),
                (int) Math.floor(eye.y + look.y * t),
                (int) Math.floor(eye.z + look.z * t)));
    }

    /**
     * Cube-extrusion target: closest-approach coordinate along the world axis
     * ({@code axisOrdinal} 0=X/1=Y/2=Z) through {@code corner}. Null when the ray is
     * parallel to the axis or the approach is behind the camera.
     */
    public static GridPos aimAlongAxis(Vec3 eye, Vec3 look, GridPos corner, int axisOrdinal) {
        double ax = axisOrdinal == 0 ? 1 : 0, ay = axisOrdinal == 1 ? 1 : 0, az = axisOrdinal == 2 ? 1 : 0;
        double[] r = closestOnLine(eye, look, corner, ax, ay, az);
        if (r == null) return null;
        int d = (int) Math.round(r[0]);
        d = Math.max(-ShapeGeometry.MAX_EXTENT, Math.min(ShapeGeometry.MAX_EXTENT, d));
        return new GridPos(
                corner.x() + (int) ax * d,
                corner.y() + (int) ay * d,
                corner.z() + (int) az * d);
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
        if (t <= 0 || t > 256.0) return null;
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
