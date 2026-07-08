package com.lwos.tool;

import com.lwos.geometry.PathNode;
import com.lwos.geometry.Vec3d;
import com.lwos.plan.TerrainMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolSessionTest {

    @BeforeEach
    void reset() {
        // Return the singleton to a known state between tests.
        ToolManager tm = ToolManager.get();
        tm.currentPath().clear();
        while (tm.selected() != ToolType.PATH) tm.cycle(1);
        if (tm.isEnabled()) tm.toggleEnabled();
    }

    @Test
    void pathToolStartsIdleAndEmpty() {
        PathTool t = new PathTool();
        assertEquals(PathTool.State.IDLE, t.state());
        assertTrue(t.nodes().isEmpty());
    }

    @Test
    void addPointMovesToPlacing() {
        PathTool t = new PathTool();
        t.addPoint(new Vec3d(1, 0, 1));
        assertEquals(PathTool.State.PLACING, t.state());
        assertEquals(1, t.nodes().size());
        assertEquals(new Vec3d(1, 0, 1), t.nodes().get(0).position());
    }

    @Test
    void deleteLastReturnsToIdleWhenEmptied() {
        PathTool t = new PathTool();
        t.addPoint(new Vec3d(0, 0, 0));
        t.addPoint(new Vec3d(1, 0, 0));
        t.deleteLast();
        assertEquals(1, t.nodes().size());
        assertEquals(PathTool.State.PLACING, t.state());
        t.deleteLast();
        assertTrue(t.nodes().isEmpty());
        assertEquals(PathTool.State.IDLE, t.state());
    }

    @Test
    void deleteLastOnEmptyIsSafe() {
        PathTool t = new PathTool();
        t.deleteLast();
        assertTrue(t.nodes().isEmpty());
        assertEquals(PathTool.State.IDLE, t.state());
    }

    @Test
    void nodesListIsUnmodifiable() {
        PathTool t = new PathTool();
        t.addPoint(new Vec3d(0, 0, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> t.nodes().add(new PathNode(new Vec3d(1, 1, 1))));
    }

    @Test
    void cycleWrapsThroughAllToolTypes() {
        ToolManager tm = ToolManager.get();
        assertEquals(ToolType.PATH, tm.selected());
        tm.cycle(1);
        assertEquals(ToolType.LINE, tm.selected());
        // A full loop (values().length tools) returns to the same tool.
        for (int i = 0; i < ToolType.values().length; i++) tm.cycle(1);
        assertEquals(ToolType.LINE, tm.selected());
        tm.cycle(-1);
        assertEquals(ToolType.PATH, tm.selected());
        tm.cycle(-1);
        assertEquals(ToolType.TERRAIN, tm.selected()); // wrap backwards
    }

    @Test
    void pathToolActiveOnlyWhenEnabledAndPathSelected() {
        ToolManager tm = ToolManager.get();
        assertFalse(tm.isPathToolActive()); // disabled by default
        tm.toggleEnabled();
        assertTrue(tm.isPathToolActive());
        tm.cycle(1); // now LINE
        assertFalse(tm.isPathToolActive());
    }

    @Test
    void defaultWidthIsThree() {
        PathTool t = new PathTool();
        assertEquals(3.0, t.width(), 1e-9);
    }

    @Test
    void setWidthClampsToValidRange() {
        PathTool t = new PathTool();
        t.setWidth(7.5);
        assertEquals(7.5, t.width(), 1e-9);
        t.setWidth(100.0);
        assertEquals(15.0, t.width(), 1e-9);
        t.setWidth(-5.0);
        assertEquals(1.0, t.width(), 1e-9);
    }

    @Test
    void clearDoesNotResetWidth() {
        PathTool t = new PathTool();
        t.setWidth(9.0);
        t.addPoint(new Vec3d(0, 0, 0));
        t.clear();
        assertEquals(9.0, t.width(), 1e-9);
    }

    @Test
    void defaultTerrainModeIsFollowSurface() {
        PathTool t = new PathTool();
        assertEquals(TerrainMode.FOLLOW_SURFACE, t.terrainMode());
    }

    @Test
    void toggleTerrainModeCyclesAndWraps() {
        PathTool t = new PathTool();
        t.toggleTerrainMode();
        assertEquals(TerrainMode.CUT_AND_FILL, t.terrainMode());
        t.toggleTerrainMode();
        assertEquals(TerrainMode.FOLLOW_SURFACE, t.terrainMode()); // wraps back
    }

    @Test
    void clearDoesNotResetTerrainMode() {
        PathTool t = new PathTool();
        t.toggleTerrainMode();
        t.addPoint(new Vec3d(0, 0, 0));
        t.clear();
        assertEquals(TerrainMode.CUT_AND_FILL, t.terrainMode());
    }

}
