package com.lwos.tool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WheelMathTest {
    @Test
    void deadAndFarZonesHoverNothing() {
        assertEquals(-1, WheelMath.sectorAt(0, 0, 8));
        assertEquals(-1, WheelMath.sectorAt(5, 5, 8));
        assertEquals(-1, WheelMath.sectorAt(200, 0, 8));
    }

    @Test
    void cardinalDirectionsPickExpectedSectors() {
        assertEquals(0, WheelMath.sectorAt(0, -40, 8));  // up = first sector
        assertEquals(2, WheelMath.sectorAt(40, 0, 8));   // right = quarter turn clockwise
        assertEquals(4, WheelMath.sectorAt(0, 40, 8));   // down
        assertEquals(6, WheelMath.sectorAt(-40, 0, 8));  // left
    }

    @Test
    void sectorBoundariesSplitBetweenSpokes() {
        // Slightly clockwise of straight up stays sector 0; past the 22.5deg boundary -> 1.
        assertEquals(0, WheelMath.sectorAt(10, -40, 8));
        assertEquals(1, WheelMath.sectorAt(35, -40, 8));
    }

    @Test
    void sectorAngleMatchesIconLayout() {
        // Must agree with the wheel's icon placement formula (first tool at top).
        assertEquals(-Math.PI / 2, WheelMath.sectorAngle(0, 8), 1e-9);
        assertEquals(0.0, WheelMath.sectorAngle(2, 8), 1e-9);
    }

    @Test
    void shortestArcWraps() {
        assertEquals(Math.PI / 4, WheelMath.shortestArc(0, Math.PI / 4), 1e-9);
        // 7/8 turn clockwise is 1/8 turn counter-clockwise the short way.
        assertEquals(-Math.PI / 4, WheelMath.shortestArc(0, 2 * Math.PI * 7 / 8), 1e-9);
    }

    @Test
    void selectSetsToolAndClearsGesture() {
        ToolManager tm = ToolManager.get();
        boolean wasEnabled = tm.isEnabled();
        if (!tm.isEnabled()) tm.toggleEnabled();
        tm.currentShape().addAnchor(new com.lwos.plan.GridPos(0, 0, 0), false);
        tm.select(ToolType.SPHERE);
        assertEquals(ToolType.SPHERE, tm.selected());
        assertEquals(ShapeTool.State.IDLE, tm.currentShape().state());
        tm.select(ToolType.PATH); // restore singleton for other tests
        if (tm.isEnabled() != wasEnabled) tm.toggleEnabled();
    }
}
