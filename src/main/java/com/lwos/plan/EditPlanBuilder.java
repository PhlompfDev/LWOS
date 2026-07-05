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
    /** Placed as a foundation below the path when CUT_AND_FILL bridges a dip. */
    private static final BlockStateRef FILL = new BlockStateRef("minecraft:dirt");
    /** Target of a REMOVE change: carving is "set to air" on the apply side. */
    private static final BlockStateRef AIR = new BlockStateRef("minecraft:air");

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
            if (mode == TerrainMode.CUT_AND_FILL) {
                emitCutAndFill(changes, c, surfaceY, targetPathY(c, raw));
            } else {
                // FOLLOW_SURFACE: drape over the terrain, replacing the topmost solid block of the column
                // so hills are preserved rather than levelled (Global Constraint: preserve terrain by default).
                GridPos pos = new GridPos(c.x(), surfaceY, c.z());
                changes.put(pos, new PlannedChange(pos, ChangeKind.TERRAIN, DEFAULT_TERRAIN));
            }
        }
        return new EditPlan(changes);
    }

    /**
     * CUT_AND_FILL: the path holds its own interpolated elevation. The walkable path block is placed at
     * {@code pathY}; terrain standing above it is carved to air (REMOVE), and a dirt foundation is placed
     * up to it where the ground has dropped away below (PLACE).
     */
    private static void emitCutAndFill(Map<GridPos, PlannedChange> changes, ColumnPos c, int surfaceY, int pathY) {
        GridPos pathPos = new GridPos(c.x(), pathY, c.z());
        changes.put(pathPos, new PlannedChange(pathPos, ChangeKind.TERRAIN, DEFAULT_TERRAIN));

        if (surfaceY > pathY) {
            // Cut: remove every solid block sitting above the path, opening a cutting through the hill.
            for (int y = pathY + 1; y <= surfaceY; y++) {
                GridPos p = new GridPos(c.x(), y, c.z());
                changes.put(p, new PlannedChange(p, ChangeKind.REMOVE, AIR));
            }
        } else if (surfaceY < pathY) {
            // Fill: bridge the gap between the existing surface and the path with a dirt foundation.
            for (int y = surfaceY + 1; y < pathY; y++) {
                GridPos p = new GridPos(c.x(), y, c.z());
                changes.put(p, new PlannedChange(p, ChangeKind.PLACE, FILL));
            }
        }
    }

    /** Path elevation over a column: the Y of the nearest (horizontally) raw spline sample, rounded to a block. */
    private static int targetPathY(ColumnPos c, List<PathSample> raw) {
        double cx = c.x() + 0.5;
        double cz = c.z() + 0.5;
        double bestDistSq = Double.POSITIVE_INFINITY;
        double bestY = 0.0;
        for (PathSample s : raw) {
            double dx = cx - s.position().x();
            double dz = cz - s.position().z();
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestY = s.position().y();
            }
        }
        return (int) Math.round(bestY);
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
