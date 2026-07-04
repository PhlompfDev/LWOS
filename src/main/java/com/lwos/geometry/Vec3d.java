package com.lwos.geometry;

/** Dependency-free 3D point. MUST NOT import net.minecraft.* / org.joml.* (spec §3.6). */
public record Vec3d(double x, double y, double z) {
    public Vec3d add(Vec3d o) { return new Vec3d(x + o.x, y + o.y, z + o.z); }
    public Vec3d sub(Vec3d o) { return new Vec3d(x - o.x, y - o.y, z - o.z); }
    public Vec3d scale(double s) { return new Vec3d(x * s, y * s, z * s); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public double distance(Vec3d o) { return sub(o).length(); }
}
