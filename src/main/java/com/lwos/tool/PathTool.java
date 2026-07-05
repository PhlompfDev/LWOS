package com.lwos.tool;

import com.lwos.geometry.PathNode;
import com.lwos.geometry.Vec3d;
import com.lwos.plan.TerrainMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Path tool session state machine. Pure — no Minecraft imports (spec §3.6).
 * M1 uses IDLE and PLACING; PREVIEW is reserved for M3 (block preview) and is
 * never entered in M1 (no blocks are placed).
 */
public class PathTool {
    public enum State { IDLE, PLACING, PREVIEW }

    private static final double MIN_WIDTH = 1.0;
    private static final double MAX_WIDTH = 15.0;

    private final List<PathNode> nodes = new ArrayList<>();
    private State state = State.IDLE;
    private double width = 3.0;
    private boolean draggingWidth = false;
    private TerrainMode terrainMode = TerrainMode.FOLLOW_SURFACE;

    public State state() { return state; }

    /** How the committed path reconciles with the terrain (draped surface vs. cut/fill grading). */
    public TerrainMode terrainMode() { return terrainMode; }

    /** Cycles to the next {@link TerrainMode}; a persistent preference, unaffected by {@link #clear()}. */
    public void toggleTerrainMode() { terrainMode = terrainMode.next(); }

    /** True while a width handle is grabbed; suppresses point placement during the drag. */
    public boolean isDraggingWidth() { return draggingWidth; }

    public void beginWidthDrag() { draggingWidth = true; }

    public void endWidthDrag() { draggingWidth = false; }

    public List<PathNode> nodes() { return Collections.unmodifiableList(nodes); }

    public double width() { return width; }

    public void setWidth(double w) {
        width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, w));
    }

    public void addPoint(Vec3d position) {
        nodes.add(new PathNode(position));
        state = State.PLACING;
    }

    public void deleteLast() {
        if (!nodes.isEmpty()) nodes.remove(nodes.size() - 1);
        if (nodes.isEmpty()) state = State.IDLE;
    }

    public void clear() {
        nodes.clear();
        state = State.IDLE;
        draggingWidth = false;
    }
}
