package com.lwos.ui.components;

import com.lwos.client.anim.Spring;
import com.lwos.ui.theme.JournalTheme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

/**
 * A custom-rendered slider for the frosted-glass panel: a thin track, an accent fill, and a round
 * knob — no vanilla button textures. Value↔pixel mapping is pure and unit-tested; rendering is
 * verified in the panel playtest. Colors and textures are drawn through the journal theme.
 *
 * <p>Knob glide (spec: wheel-redesign-ui-tweens §4): widget instances are rebuilt every frame, so
 * the knob's spring lives in a static id-keyed registry. The spring is presentation-only — value
 * logic and hit-testing stay exact; only where the knob/fill DRAW moves through the spring.
 */
public final class SliderWidget {

    private static final int TRACK_H    = 6;

    /** Snappier than the house feel: drags should track closely, preset jumps still glide. */
    private static final float GLIDE_ZETA = 0.9f;
    private static final float GLIDE_HZ = 6.0f;

    private static final class Glide {
        final Spring spring = new Spring(GLIDE_ZETA, GLIDE_HZ);
        long lastNanos = 0;
        boolean fresh = true;
    }

    private static final Map<String, Glide> GLIDES = new HashMap<>();

    private final int x, y, width;
    private final double min, max;
    private double value;

    public SliderWidget(int x, int y, int width, double min, double max, double value) {
        this.x = x; this.y = y; this.width = width; this.min = min; this.max = max;
        this.value = Math.max(min, Math.min(max, value));
    }

    public double value() { return value; }

    public static double valueFromPixel(int px, int trackX, int trackW, double min, double max) {
        double t = (double) (px - trackX) / trackW;
        t = Math.max(0.0, Math.min(1.0, t));
        return min + t * (max - min);
    }

    public static int pixelFromValue(double value, int trackX, int trackW, double min, double max) {
        double t = (value - min) / (max - min);
        t = Math.max(0.0, Math.min(1.0, t));
        return trackX + (int) Math.round(t * trackW);
    }

    public boolean isOver(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y - 4 && my <= y + TRACK_H + 4;
    }

    /** Sets the value from a mouse x within the track and returns the new value. */
    public double setFromMouse(double mx) {
        value = valueFromPixel((int) Math.round(mx), x, width, min, max);
        return value;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        drawAt(g, pixelFromValue(value, x, width, min, max));
    }

    /**
     * Renders with the knob gliding through a persistent spring keyed by {@code springId}
     * (stable per slider across frames). New ids snap so panels don't animate on first open.
     */
    public void render(GuiGraphics g, String springId) {
        int exactX = pixelFromValue(value, x, width, min, max);
        Glide glide = GLIDES.computeIfAbsent(springId, k -> new Glide());
        if (glide.fresh) {
            glide.spring.snapTo(exactX);
            glide.fresh = false;
        }
        glide.spring.setTarget(exactX);
        long now = System.nanoTime();
        float dt = glide.lastNanos == 0 ? 1f / 60f : (now - glide.lastNanos) / 1_000_000_000f;
        glide.lastNanos = now;
        glide.spring.update(dt);
        int knobX = Math.max(x, Math.min(x + width, Math.round(glide.spring.value())));
        drawAt(g, knobX);
    }

    private void drawAt(GuiGraphics g, int knobX) {
        // 8px groove art drawn 1px above the 6px logical track; hitbox (y-4 .. y+TRACK_H+4) unchanged.
        JournalTheme.blitH3(g, JournalTheme.TRACK_U, JournalTheme.TRACK_V, JournalTheme.TRACK_W,
                JournalTheme.TRACK_TEX_H, JournalTheme.TRACK_CAP, x, y - 1, width, 8);
        if (knobX > x + 1) {
            g.fill(x + 1, y, knobX, y + TRACK_H, JournalTheme.INK_FILL); // ink wash up to the knob
        }
        JournalTheme.blitRegion(g, JournalTheme.KNOB_U, JournalTheme.KNOB_V, JournalTheme.KNOB_W,
                JournalTheme.KNOB_H, knobX - 4, y - 3);
    }
}
