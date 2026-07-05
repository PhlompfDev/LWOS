package com.lwos.ui.components;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A custom-rendered slider for the frosted-glass panel: a thin track, an accent fill, and a round
 * knob — no vanilla button textures. Value↔pixel mapping is pure and unit-tested; rendering is
 * verified in the panel playtest. Colors are ARGB ints ({@code 0xAARRGGBB}).
 */
public final class SliderWidget {

    private static final int TRACK_BG   = 0x33FFFFFF;
    private static final int FILL       = 0xFF7CD3FF; // cool blue accent
    private static final int KNOB       = 0xFFEAF7FF;
    private static final int TRACK_H    = 6;

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
        int knobX = pixelFromValue(value, x, width, min, max);
        g.fill(x, y, x + width, y + TRACK_H, TRACK_BG);
        g.fill(x, y, knobX, y + TRACK_H, FILL);
        g.fill(knobX - 5, y - 2, knobX + 5, y + TRACK_H + 2, KNOB);
    }
}
