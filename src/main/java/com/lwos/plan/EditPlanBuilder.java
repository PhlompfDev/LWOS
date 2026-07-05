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
    private EditPlanBuilder() { }

    public static EditPlan build(List<Vec3d> controlPoints, double spacing, double width, WorldView view) {
        List<PathSample> raw = PathSampler.sampleWithWidth(controlPoints, spacing, width);
        List<PathSample> grounded = TerrainSampler.snapToSurface(raw, view, 0.0);
        PathMask mask = PathMask.build(grounded);

        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (ColumnPos c : mask.insideColumns()) {
            int y = view.surfaceHeight(c.x(), c.z());
            GridPos pos = new GridPos(c.x(), y, c.z());
            changes.put(pos, new PlannedChange(pos, ChangeKind.TERRAIN));
        }
        return new EditPlan(changes);
    }
}
