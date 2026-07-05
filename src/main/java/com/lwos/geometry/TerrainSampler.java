package com.lwos.geometry;

import java.util.ArrayList;
import java.util.List;

/** Snaps sample points onto the world surface so the path hugs terrain (spec §4.2, §6). Pure and deterministic. */
public final class TerrainSampler {
    private TerrainSampler() { }

    public static List<PathSample> snapToSurface(List<PathSample> samples, WorldView view, double verticalOffset) {
        List<PathSample> out = new ArrayList<>(samples.size());
        for (PathSample s : samples) {
            Vec3d p = s.position();
            int x = (int) Math.floor(p.x());
            int z = (int) Math.floor(p.z());
            double y = view.surfaceHeight(x, z) + verticalOffset;
            out.add(new PathSample(new Vec3d(p.x(), y, p.z()), s.width()));
        }
        return out;
    }
}
