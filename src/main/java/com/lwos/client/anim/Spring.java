package com.lwos.client.anim;

/**
 * Underdamped spring follower (spec §3): x'' = w^2 (target - x) - 2 z w x', integrated
 * semi-implicitly with real frame time. The house motion feel is ZETA/HZ; the shape
 * preview and (sub-project C) the 2D UI tweens share it. Presentation-only by contract —
 * never feeds plan geometry. No Minecraft imports; headless-testable.
 */
public final class Spring {
    /** House damping ratio (slight underdamp = one visible bounce). */
    public static final float ZETA = 0.8f;
    /** House response frequency in Hz. */
    public static final float HZ = 3.0f;
    /** Max integration step; larger frame gaps are clamped for stability. */
    private static final float MAX_DT = 0.05f;
    /** Internal substep size (see update). */
    private static final float SUBSTEP = 1f / 240f;
    private static final float SETTLE_EPS = 1e-3f;

    private final float omega; // angular frequency, 2*pi*hz
    private final float zeta;
    private float value;
    private float velocity;
    private float target;

    public Spring(float zeta, float hz) {
        this.zeta = zeta;
        this.omega = (float) (2.0 * Math.PI * hz);
    }

    public void snapTo(float v) {
        value = v;
        target = v;
        velocity = 0f;
    }

    public void setTarget(float t) { target = t; }

    public void update(float dtSeconds) {
        float remaining = Math.min(dtSeconds, MAX_DT);
        if (remaining <= 0f) return;
        // Substep at <=1/240s: plain semi-implicit Euler at 60fps steps adds enough numerical
        // damping to visibly eat the zeta=0.8 overshoot; smaller steps keep the bounce honest.
        while (remaining > 0f) {
            float dt = Math.min(remaining, SUBSTEP);
            float accel = omega * omega * (target - value) - 2f * zeta * omega * velocity;
            velocity += accel * dt;   // semi-implicit Euler: velocity first,
            value += velocity * dt;   // then position from the NEW velocity
            remaining -= dt;
        }
    }

    public float value() { return value; }
    public float target() { return target; }

    public boolean isSettled() {
        return Math.abs(value - target) < SETTLE_EPS && Math.abs(velocity) < SETTLE_EPS;
    }
}
