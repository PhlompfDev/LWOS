package com.lwos.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Centripetal Catmull-Rom spline (alpha = 0.5). Pure and deterministic
 * (spec §3.2, §3.6). Endpoints are duplicated so the curve interpolates the
 * first and last control points.
 */
public final class CatmullRomSpline {
    private static final double ALPHA = 0.5;

    private CatmullRomSpline() { }

    public static List<Vec3d> sample(List<Vec3d> controlPoints, int samplesPerSegment) {
        int sps = Math.max(1, samplesPerSegment);
        List<Vec3d> out = new ArrayList<>();
        if (controlPoints == null || controlPoints.isEmpty()) return out;
        if (controlPoints.size() == 1) {
            out.add(controlPoints.get(0));
            return out;
        }
        int n = controlPoints.size();
        out.add(controlPoints.get(0));
        for (int i = 0; i < n - 1; i++) {
            Vec3d p0 = controlPoints.get(Math.max(0, i - 1));
            Vec3d p1 = controlPoints.get(i);
            Vec3d p2 = controlPoints.get(i + 1);
            Vec3d p3 = controlPoints.get(Math.min(n - 1, i + 2));
            appendSegment(out, p0, p1, p2, p3, sps);
        }
        return out;
    }

    // Appends samples for the p1..p2 span (excluding p1, including p2) so joints aren't duplicated.
    private static void appendSegment(List<Vec3d> out, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, int sps) {
        double t0 = 0.0;
        double t1 = t0 + knot(p0, p1);
        double t2 = t1 + knot(p1, p2);
        double t3 = t2 + knot(p2, p3);

        // Degenerate middle span (p1 == p2): emit p2 once, nothing to interpolate.
        if (t2 - t1 <= 1e-12) {
            out.add(p2);
            return;
        }
        for (int s = 1; s <= sps; s++) {
            double t = t1 + (t2 - t1) * ((double) s / sps);
            out.add(point(p0, p1, p2, p3, t0, t1, t2, t3, t));
        }
    }

    private static double knot(Vec3d a, Vec3d b) {
        double d = a.distance(b);
        return Math.pow(d, ALPHA);
    }

    private static Vec3d point(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3,
                               double t0, double t1, double t2, double t3, double t) {
        Vec3d a1 = lerpKnot(p0, p1, t0, t1, t);
        Vec3d a2 = lerpKnot(p1, p2, t1, t2, t);
        Vec3d a3 = lerpKnot(p2, p3, t2, t3, t);
        Vec3d b1 = lerpKnot(a1, a2, t0, t2, t);
        Vec3d b2 = lerpKnot(a2, a3, t1, t3, t);
        return lerpKnot(b1, b2, t1, t2, t);
    }

    // Linear interpolation on a knot interval; falls back to the endpoint if the interval is zero-length.
    private static Vec3d lerpKnot(Vec3d a, Vec3d b, double ta, double tb, double t) {
        double denom = tb - ta;
        if (Math.abs(denom) <= 1e-12) return b;
        double w = (t - ta) / denom;
        return a.scale(1 - w).add(b.scale(w));
    }
}
