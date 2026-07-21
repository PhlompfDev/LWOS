package com.lwos.client.anim;

/** Three springs, one per axis — drives spring-following 3D corners (spec §3). */
public final class SpringVec3 {
    private final Spring x = new Spring(Spring.ZETA, Spring.HZ);
    private final Spring y = new Spring(Spring.ZETA, Spring.HZ);
    private final Spring z = new Spring(Spring.ZETA, Spring.HZ);

    public void snapTo(double px, double py, double pz) {
        x.snapTo((float) px);
        y.snapTo((float) py);
        z.snapTo((float) pz);
    }

    public void setTarget(double px, double py, double pz) {
        x.setTarget((float) px);
        y.setTarget((float) py);
        z.setTarget((float) pz);
    }

    public void update(float dtSeconds) {
        x.update(dtSeconds);
        y.update(dtSeconds);
        z.update(dtSeconds);
    }

    public double x() { return x.value(); }
    public double y() { return y.value(); }
    public double z() { return z.value(); }
}
