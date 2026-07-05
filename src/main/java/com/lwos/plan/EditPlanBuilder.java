package com.lwos.plan;

import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import com.lwos.geometry.PathSample;
import com.lwos.geometry.PathSampler;
import com.lwos.geometry.TerrainSampler;
import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** Builds in the default {@link TerrainMode#FOLLOW_SURFACE} mode. */
    public static EditPlan build(List<Vec3d> controlPoints, double spacing, double width, WorldView view) {
        return build(controlPoints, spacing, width, view, TerrainMode.FOLLOW_SURFACE);
    }

    public static EditPlan build(List<Vec3d> controlPoints, double spacing, double width,
                                 WorldView view, TerrainMode mode) {
        List<PathSample> raw = PathSampler.sampleWithWidth(controlPoints, spacing, width);
        // The footprint (which columns the path covers) is derived from the terrain-snapped samples in
        // every mode, so switching modes changes only the Y handling, never the plan's horizontal extent.
        List<PathSample> grounded = TerrainSampler.snapToSurface(raw, view, 0.0);
        PathMask mask = PathMask.build(grounded);

        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (ColumnPos c : sortedColumns(mask.insideColumns())) {
            int surfaceY = view.surfaceHeight(c.x(), c.z());
            // FOLLOW_SURFACE: drape over the terrain, replacing the topmost solid block of the column so
            // hills are preserved rather than levelled (Global Constraint: preserve terrain by default).
            GridPos pos = new GridPos(c.x(), surfaceY, c.z());
            changes.put(pos, new PlannedChange(pos, ChangeKind.TERRAIN, DEFAULT_TERRAIN));
        }
        return new EditPlan(changes);
    }

    /**
     * Deterministic column order. {@link PathMask#insideColumns()} returns a {@link Set} whose
     * iteration order is unspecified; sorting by (x, z) makes the emitted plan byte-stable and keeps
     * stacked changes (M4 carve/fill) grouped per column.
     */
    private static List<ColumnPos> sortedColumns(Set<ColumnPos> columns) {
        List<ColumnPos> out = new ArrayList<>(columns);
        out.sort(Comparator.comparingInt(ColumnPos::x).thenComparingInt(ColumnPos::z));
        return out;
    }
}
