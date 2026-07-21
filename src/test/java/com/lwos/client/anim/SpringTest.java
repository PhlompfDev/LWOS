package com.lwos.client.anim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpringTest {
    @Test
    void convergesToTarget() {
        Spring s = new Spring(Spring.ZETA, Spring.HZ);
        s.snapTo(0f);
        s.setTarget(1f);
        for (int i = 0; i < 600; i++) s.update(1f / 60f); // 10 simulated seconds
        assertEquals(1f, s.value(), 1e-3);
        assertTrue(s.isSettled());
    }

    @Test
    void underdampedOvershoots() {
        Spring s = new Spring(0.8f, 3.0f);
        s.snapTo(0f);
        s.setTarget(1f);
        float max = 0f;
        for (int i = 0; i < 300; i++) { s.update(1f / 60f); max = Math.max(max, s.value()); }
        assertTrue(max > 1.005f, "zeta=0.8 must visibly overshoot, peaked at " + max);
        assertTrue(max < 1.10f, "overshoot should stay subtle, peaked at " + max);
    }

    @Test
    void largeDtClampedStable() {
        Spring s = new Spring(Spring.ZETA, Spring.HZ);
        s.snapTo(0f);
        s.setTarget(1f);
        for (int i = 0; i < 100; i++) s.update(5f); // absurd dt, must not explode
        assertTrue(Float.isFinite(s.value()));
        assertEquals(1f, s.value(), 0.05f);
    }

    @Test
    void snapKillsMotion() {
        Spring s = new Spring(Spring.ZETA, Spring.HZ);
        s.setTarget(5f);
        s.update(0.1f);
        s.snapTo(2f);
        assertEquals(2f, s.value(), 0f);
        assertEquals(2f, s.target(), 0f);
        assertTrue(s.isSettled());
    }
}
