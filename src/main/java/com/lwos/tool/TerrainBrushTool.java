package com.lwos.tool;

import com.lwos.brush.BrushOp;

/**
 * Terrain-brush session state (spec §1): the active op and brush radius. Client-side tool
 * state alongside PathTool — not part of any style, never serialized, never on the wire
 * beyond the per-dab request. Pure — no Minecraft imports.
 */
public class TerrainBrushTool {
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 16;
    public static final int DEFAULT_RADIUS = 6;

    private BrushOp op = BrushOp.SMOOTH;
    private int radius = DEFAULT_RADIUS;
    private long revision = 0;

    public BrushOp op() { return op; }

    /** M key: Smooth -> Melt -> Fill -> Lift -> Smooth (spec §1). */
    public void cycleOp() {
        op = op.next();
        revision++;
    }

    public int radius() { return radius; }

    /** Ctrl+scroll: steps the radius by delta, integer clamp 2..16 (spec §1). */
    public void adjustRadius(int delta) {
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius + delta));
        revision++;
    }

    /** Monotonic counter bumped on every mutation; the brush preview cache keys on it. */
    public long revision() { return revision; }

    /** Forces the next preview rebuild (e.g. after a committed dab changed the ground under it). */
    public void bumpRevision() { revision++; }
}
