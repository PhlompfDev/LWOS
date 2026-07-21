package com.lwos.tool;

import com.lwos.plan.BlockStateRef;
import com.lwos.plan.GridPos;
import com.lwos.shape.ShapeMode;
import com.lwos.shape.ShapeOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shape gesture state machine (spec §5): anchors accumulate until the mode's clickCount
 * is reached (the final click is supplied at commit time from the live aim target).
 * One instance serves all six shape ToolTypes. Pure — no Minecraft imports.
 */
public class ShapeTool {
    public enum State { IDLE, ANCHORED, BASE_DONE }

    private final List<GridPos> anchors = new ArrayList<>();
    private State state = State.IDLE;
    private boolean breakMode = false;
    private ShapeOptions options = ShapeOptions.DEFAULT;
    private BlockStateRef material = new BlockStateRef("minecraft:stone");
    private long revision = 0;

    public State state() { return state; }
    public List<GridPos> anchors() { return Collections.unmodifiableList(anchors); }
    public boolean breakMode() { return breakMode; }
    public ShapeOptions options() { return options; }
    public BlockStateRef material() { return material; }

    /** Monotonic counter bumped on every mutation; the shape preview cache keys on it. */
    public long revision() { return revision; }

    /**
     * Adds an anchor. A click of the opposite intent mid-gesture (asBreak != breakMode)
     * cancels instead — one gesture, one intent (spec §2). Returns whether the anchor
     * was accepted.
     */
    public boolean addAnchor(GridPos p, boolean asBreak) {
        if (state != State.IDLE && asBreak != breakMode) {
            clear();
            return false;
        }
        if (state == State.IDLE) breakMode = asBreak;
        anchors.add(p);
        state = anchors.size() == 1 ? State.ANCHORED : State.BASE_DONE;
        revision++;
        return true;
    }

    /** True when the NEXT click supplies the final anchor and commits. */
    public boolean isComplete(ShapeMode mode) {
        return state != State.IDLE && anchors.size() == mode.clickCount() - 1;
    }

    /** M key: FILLED <-> HOLLOW. A persistent preference, unaffected by clear(). */
    public void cycleFill() {
        options = options.cycleFill();
        revision++;
    }

    /** Captured from the main hand at first anchor (place gestures only). */
    public void setMaterial(BlockStateRef material) {
        this.material = material;
        revision++;
    }

    public void clear() {
        anchors.clear();
        state = State.IDLE;
        breakMode = false;
        revision++;
    }

    /** Forces the next preview rebuild (e.g. after a commit changed the world under it). */
    public void bumpRevision() { revision++; }
}
