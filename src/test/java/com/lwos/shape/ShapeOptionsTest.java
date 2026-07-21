package com.lwos.shape;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShapeOptionsTest {
    @Test
    void clickCounts() {
        assertEquals(3, ShapeMode.CUBE.clickCount());
        for (ShapeMode m : ShapeMode.values()) {
            if (m != ShapeMode.CUBE) assertEquals(2, m.clickCount());
        }
    }

    @Test
    void lineHasNoFill() {
        assertFalse(ShapeMode.LINE.supportsFill());
        assertTrue(ShapeMode.SPHERE.supportsFill());
    }

    @Test
    void jsonRoundTrip() {
        ShapeOptions hollow = new ShapeOptions(ShapeOptions.Fill.HOLLOW);
        assertEquals(hollow, ShapeOptions.fromJson(hollow.toJson()));
    }

    @Test
    void fromJsonLenient() {
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson(null));
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson(""));
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson("not json"));
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson("{}"));
        assertEquals(ShapeOptions.DEFAULT, ShapeOptions.fromJson("{\"unknown\":1}"));
    }

    @Test
    void cycleFillTogglesBothWays() {
        assertEquals(ShapeOptions.Fill.HOLLOW, ShapeOptions.DEFAULT.cycleFill().fill());
        assertEquals(ShapeOptions.Fill.FILLED, ShapeOptions.DEFAULT.cycleFill().cycleFill().fill());
    }
}
