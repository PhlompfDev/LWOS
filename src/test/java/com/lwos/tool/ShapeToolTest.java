package com.lwos.tool;

import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeMode;
import com.lwos.shape.ShapeOptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShapeToolTest {
    @Test
    void anchorAdvancesState() {
        ShapeTool t = new ShapeTool();
        assertEquals(ShapeTool.State.IDLE, t.state());
        assertTrue(t.addAnchor(new GridPos(0, 0, 0), false));
        assertEquals(ShapeTool.State.ANCHORED, t.state());
        assertFalse(t.breakMode());
    }

    @Test
    void mixedIntentClickCancels() {
        ShapeTool t = new ShapeTool();
        t.addAnchor(new GridPos(0, 0, 0), false);
        assertFalse(t.addAnchor(new GridPos(1, 0, 0), true)); // left-click mid place-gesture
        assertEquals(ShapeTool.State.IDLE, t.state());
        assertTrue(t.anchors().isEmpty());
    }

    @Test
    void twoClickShapeCompletesAfterOneAnchor() {
        ShapeTool t = new ShapeTool();
        t.addAnchor(new GridPos(0, 0, 0), false);
        assertTrue(t.isComplete(ShapeMode.RECT));   // next click commits
        assertFalse(t.isComplete(ShapeMode.CUBE));  // cube needs a second anchor first
        t.addAnchor(new GridPos(2, 0, 2), false);
        assertEquals(ShapeTool.State.BASE_DONE, t.state());
        assertTrue(t.isComplete(ShapeMode.CUBE));
    }

    @Test
    void fillCycleBumpsRevision() {
        ShapeTool t = new ShapeTool();
        long r = t.revision();
        t.cycleFill();
        assertEquals(ShapeOptions.Fill.HOLLOW, t.options().fill());
        assertTrue(t.revision() > r);
    }

    @Test
    void toolTypeMapping() {
        assertNull(ToolType.PATH.shapeMode());
        assertNull(ToolType.TERRAIN.shapeMode());
        assertEquals(ShapeMode.RECT, ToolType.RECT.shapeMode());
        assertEquals(0, ToolType.PATH.iconIndex());
        assertEquals(1, ToolType.TERRAIN.iconIndex());
        assertEquals(7, ToolType.SPHERE.iconIndex());
    }

    @Test
    void managerRoutesShapes() {
        ToolManager tm = ToolManager.get();
        if (!tm.isEnabled()) tm.toggleEnabled();
        while (tm.selected() != ToolType.RECT) tm.cycle(1);
        assertTrue(tm.isShapeToolActive());
        assertEquals(ShapeMode.RECT, tm.activeShapeMode());
        assertFalse(tm.isPathToolActive());
        tm.currentShape().addAnchor(new GridPos(0, 0, 0), false);
        tm.cycle(1); // switching tools abandons the gesture
        assertEquals(ShapeTool.State.IDLE, tm.currentShape().state());
        while (tm.selected() != ToolType.PATH) tm.cycle(1);
        if (tm.isEnabled()) tm.toggleEnabled(); // restore singleton state for other tests
    }
}
