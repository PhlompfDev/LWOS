package com.lwos.geometry;

import java.util.ArrayList;
import java.util.List;

/** Left/right boundary curves offset by half-width, for visualizing path width. Pure and deterministic (spec §3.2, §3.6). */
public final class PathRibbon {
    private PathRibbon() { }

    public record Edges(List<Vec3d> left, List<Vec3d> right) { }

    public static Edges compute(List<PathSample> samples) {
        int n = samples.size();
        List<Vec3d> left = new ArrayList<>(n);
        List<Vec3d> right = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Vec3d p = samples.get(i).position();
            Vec3d prev = samples.get(Math.max(0, i - 1)).position();
            Vec3d next = samples.get(Math.min(n - 1, i + 1)).position();
            double dx = next.x() - prev.x();
            double dz = next.z() - prev.z();
            double len = Math.sqrt(dx * dx + dz * dz);
            double nx, nz;
            if (len <= 1e-9) { nx = 1; nz = 0; } else { nx = -dz / len; nz = dx / len; }
            double half = samples.get(i).width() / 2.0;
            left.add(new Vec3d(p.x() + nx * half, p.y(), p.z() + nz * half));
            right.add(new Vec3d(p.x() - nx * half, p.y(), p.z() - nz * half));
        }
        return new Edges(left, right);
    }
}
