package com.lwos.tool;

import com.lwos.geometry.PathNode;
import com.lwos.geometry.Vec3d;
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

    private final List<PathNode> nodes = new ArrayList<>();
    private State state = State.IDLE;

    public State state() { return state; }

    public List<PathNode> nodes() { return Collections.unmodifiableList(nodes); }

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
    }
}
