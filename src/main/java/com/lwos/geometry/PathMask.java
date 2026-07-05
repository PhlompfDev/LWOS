package com.lwos.geometry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Union of per-sample discs (radius = width/2) into a column occupancy field with a
 * signed edge distance (negative = inside), the "soft edge" the organic engine will
 * feather in M5 (spec §4.2, §6). Pure and deterministic.
 */
public final class PathMask {
    // Columns whose disc-union distance is within this halo of the edge are tracked at all
    // (a small buffer just outside 0 so future edge-feathering stages have neighbors to read).
    private static final double EDGE_HALO = 3.0;

    private final Map<ColumnPos, Double> edgeDistance;

    private PathMask(Map<ColumnPos, Double> edgeDistance) {
        this.edgeDistance = edgeDistance;
    }

    public static PathMask build(List<PathSample> samples) {
        Map<ColumnPos, Double> dist = new HashMap<>();
        if (samples.isEmpty()) return new PathMask(dist);

        double maxRadius = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (PathSample s : samples) {
            maxRadius = Math.max(maxRadius, s.width() / 2.0);
            int x = (int) Math.floor(s.position().x());
            int z = (int) Math.floor(s.position().z());
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        int pad = (int) Math.ceil(maxRadius) + 1;
        minX -= pad; maxX += pad; minZ -= pad; maxZ += pad;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double cx = x + 0.5;
                double cz = z + 0.5;
                double best = Double.POSITIVE_INFINITY;

                // Distance to sample points
                for (PathSample s : samples) {
                    double dx = cx - s.position().x();
                    double dz = cz - s.position().z();
                    double d = Math.sqrt(dx * dx + dz * dz) - s.width() / 2.0;
                    if (d < best) best = d;
                }

                // Distance to line segments between consecutive samples
                for (int i = 0; i < samples.size() - 1; i++) {
                    PathSample s0 = samples.get(i);
                    PathSample s1 = samples.get(i + 1);
                    double x0 = s0.position().x(), z0 = s0.position().z();
                    double x1 = s1.position().x(), z1 = s1.position().z();
                    double r = (s0.width() + s1.width()) / 4.0; // average radius

                    // Distance from (cx, cz) to line segment from (x0, z0) to (x1, z1)
                    double dx = x1 - x0, dz = z1 - z0;
                    double lenSq = dx * dx + dz * dz;
                    double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1, ((cx - x0) * dx + (cz - z0) * dz) / lenSq));
                    double px = x0 + t * dx, pz = z0 + t * dz;
                    double dpx = cx - px, dpz = cz - pz;
                    double distToSeg = Math.sqrt(dpx * dpx + dpz * dpz) - r;
                    if (distToSeg < best) best = distToSeg;
                }

                if (best <= EDGE_HALO) dist.put(new ColumnPos(x, z), best);
            }
        }
        return new PathMask(dist);
    }

    public boolean isInside(int x, int z) {
        Double d = edgeDistance.get(new ColumnPos(x, z));
        return d != null && d <= 0.0;
    }

    public double edgeDistance(int x, int z) {
        Double d = edgeDistance.get(new ColumnPos(x, z));
        return d == null ? Double.POSITIVE_INFINITY : d;
    }

    public Set<ColumnPos> insideColumns() {
        Set<ColumnPos> out = new HashSet<>();
        for (Map.Entry<ColumnPos, Double> e : edgeDistance.entrySet()) {
            if (e.getValue() <= 0.0) out.add(e.getKey());
        }
        return out;
    }
}
