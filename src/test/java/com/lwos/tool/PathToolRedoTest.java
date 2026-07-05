package com.lwos.tool;

import com.lwos.geometry.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathToolRedoTest {

    private static Vec3d v(double x) { return new Vec3d(x, 64, 0); }

    @Test
    void redoRestoresDeletedPointsInOrder() {
        PathTool t = new PathTool();
        t.addPoint(v(1));
        t.addPoint(v(2));
        t.addPoint(v(3));
        t.deleteLast(); // removes 3
        t.deleteLast(); // removes 2
        assertEquals(1, t.nodes().size());

        assertTrue(t.redoPoint());
        assertEquals(2.0, t.nodes().get(1).position().x(), "first redo restores the most recently deleted (2)");
        assertTrue(t.redoPoint());
        assertEquals(3.0, t.nodes().get(2).position().x(), "second redo restores 3");
    }

    @Test
    void redoOnEmptyStackReturnsFalse() {
        PathTool t = new PathTool();
        t.addPoint(v(1));
        assertFalse(t.redoPoint(), "nothing to redo");
    }

    @Test
    void addingAPointClearsTheRedoStack() {
        PathTool t = new PathTool();
        t.addPoint(v(1));
        t.deleteLast();          // redo stack now holds 1
        t.addPoint(v(9));        // new work invalidates redo
        assertFalse(t.redoPoint(), "a fresh point clears the redo stack");
    }

    @Test
    void clearAlsoEmptiesTheRedoStack() {
        PathTool t = new PathTool();
        t.addPoint(v(1));
        t.deleteLast();
        t.clear();
        assertFalse(t.redoPoint(), "clear() empties redo too");
    }
}
