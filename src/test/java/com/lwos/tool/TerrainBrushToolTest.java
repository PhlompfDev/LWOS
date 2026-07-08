package com.lwos.tool;

import com.lwos.brush.BrushOp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainBrushToolTest {

    @Test
    void defaultsAreSmoothAtRadiusSix() {
        TerrainBrushTool tool = new TerrainBrushTool();
        assertEquals(BrushOp.SMOOTH, tool.op());
        assertEquals(6, tool.radius());
    }

    @Test
    void mCycleWrapsThroughAllFourOps() {
        TerrainBrushTool tool = new TerrainBrushTool();
        tool.cycleOp();
        assertEquals(BrushOp.MELT, tool.op());
        tool.cycleOp();
        assertEquals(BrushOp.FILL, tool.op());
        tool.cycleOp();
        assertEquals(BrushOp.LIFT, tool.op());
        tool.cycleOp();
        assertEquals(BrushOp.SMOOTH, tool.op());
    }

    @Test
    void radiusClampsToTwoThroughSixteen() {
        TerrainBrushTool tool = new TerrainBrushTool();
        for (int i = 0; i < 30; i++) tool.adjustRadius(-1);
        assertEquals(2, tool.radius());
        for (int i = 0; i < 30; i++) tool.adjustRadius(1);
        assertEquals(16, tool.radius());
    }

    @Test
    void everyMutationBumpsTheRevision() {
        TerrainBrushTool tool = new TerrainBrushTool();
        long r0 = tool.revision();
        tool.cycleOp();
        long r1 = tool.revision();
        assertTrue(r1 > r0);
        tool.adjustRadius(1);
        long r2 = tool.revision();
        assertTrue(r2 > r1);
        tool.bumpRevision();
        assertTrue(tool.revision() > r2);
    }
}
