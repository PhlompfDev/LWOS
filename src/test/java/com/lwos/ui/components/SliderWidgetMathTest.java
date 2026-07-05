package com.lwos.ui.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SliderWidgetMathTest {

    @Test
    void pixelAndValueAreInverse() {
        int trackX = 100, trackW = 200;
        double min = 0.0, max = 10.0;
        for (double v = 0; v <= 10; v += 2.5) {
            int px = SliderWidget.pixelFromValue(v, trackX, trackW, min, max);
            double back = SliderWidget.valueFromPixel(px, trackX, trackW, min, max);
            assertEquals(v, back, 0.05, "round-trip value at " + v);
        }
    }

    @Test
    void valueClampsToRangeOutsideTheTrack() {
        assertEquals(0.0, SliderWidget.valueFromPixel(50, 100, 200, 0.0, 10.0), 1e-9, "left of track clamps to min");
        assertEquals(10.0, SliderWidget.valueFromPixel(999, 100, 200, 0.0, 10.0), 1e-9, "right of track clamps to max");
    }
}
