package com.lwos.geometry;

import java.util.ArrayList;
import java.util.List;

/** Resamples a curve to even arc-length spacing. Pure and deterministic (spec §3.2, §3.6). */
public final class PathSampler {
    private static final int SAMPLES_PER_SEGMENT = 24;

    private PathSampler() { }

    public static List<Vec3d> sample(List<Vec3d> controlPoints, double spacing) {
        return resample(CatmullRomSpline.sample(controlPoints, SAMPLES_PER_SEGMENT), spacing);
    }

    public static List<Vec3d> resample(List<Vec3d> polyline, double spacing) {
        List<Vec3d> out = new ArrayList<>();
        if (polyline == null || polyline.isEmpty()) return out;
        if (polyline.size() == 1) {
            out.add(polyline.get(0));
            return out;
        }
        double step = Math.max(1e-6, spacing);
        out.add(polyline.get(0));
        double carried = 0.0; // distance accumulated since the last emitted point
        for (int i = 0; i < polyline.size() - 1; i++) {
            Vec3d a = polyline.get(i);
            Vec3d b = polyline.get(i + 1);
            double segLen = a.distance(b);
            if (segLen <= 1e-12) continue;
            double distIntoSeg = step - carried; // where the next emission falls within this segment
            while (distIntoSeg <= segLen + 1e-9) {
                double w = distIntoSeg / segLen;
                out.add(a.scale(1 - w).add(b.scale(w)));
                distIntoSeg += step;
            }
            carried = segLen - (distIntoSeg - step);
        }
        Vec3d last = polyline.get(polyline.size() - 1);
        if (out.get(out.size() - 1).distance(last) > 1e-6) out.add(last);
        return out;
    }
}
