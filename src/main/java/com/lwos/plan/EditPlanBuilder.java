package com.lwos.plan;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import com.lwos.geometry.PathSample;
import com.lwos.geometry.PathSampler;
import com.lwos.geometry.TerrainSampler;
import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the M2 pipeline: Spline+width sampling -> terrain snap -> disc-union mask -> EditPlan.
 * The only code that runs this pipeline (spec §4.3) — nothing else recomputes geometry.
 * Pure and deterministic: same inputs + same WorldView answers -> identical EditPlan.
 */
public final class EditPlanBuilder {
    /** Default material for TERRAIN changes until the organic engine (M5) chooses per-cell materials. */
    private static final BlockStateRef DEFAULT_TERRAIN = new BlockStateRef("minecraft:dirt_path");

    /**
     * Curve sampling step (blocks). Shared by the client preview and the server rebuild so both
     * derive an identical plan from the same control points + width (Global Constraint: determinism).
     */
    public static final double DEFAULT_SPACING = 0.25;

    private EditPlanBuilder() { }

    public static EditPlan build(List<Vec3d> controlPoints, double spacing, double width, WorldView view) {
        List<PathSample> raw = PathSampler.sampleWithWidth(controlPoints, spacing, width);
        List<PathSample> grounded = TerrainSampler.snapToSurface(raw, view, 0.0);
        PathMask mask = PathMask.build(grounded);

        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (ColumnPos c : mask.insideColumns()) {
            int y = view.surfaceHeight(c.x(), c.z());
            GridPos pos = new GridPos(c.x(), y, c.z());
            changes.put(pos, new PlannedChange(pos, ChangeKind.TERRAIN, DEFAULT_TERRAIN));
        }
        return new EditPlan(changes);
    }
}
