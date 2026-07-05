package com.lwos.plan;

import com.lwos.config.OrganicTunables;
import com.lwos.geometry.ColumnPos;
import com.lwos.geometry.PathMask;
import com.lwos.geometry.PathSample;
import com.lwos.geometry.PathSampler;
import com.lwos.geometry.TerrainSampler;
import com.lwos.geometry.Vec3d;
import com.lwos.geometry.WorldView;
import com.lwos.organic.BlendEngine;
import com.lwos.organic.EdgeShaper;
import com.lwos.organic.GradientEngine;

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
    /** Placed as a foundation below the path when CUT_AND_FILL bridges a dip. */
    private static final BlockStateRef FILL = new BlockStateRef("minecraft:dirt");
    /** Target of a REMOVE change: carving is "set to air" on the apply side. */
    private static final BlockStateRef AIR = new BlockStateRef("minecraft:air");

    /**
     * Curve sampling step (blocks). Shared by the client preview and the server rebuild so both
     * derive an identical plan from the same control points + width (Global Constraint: determinism).
     */
    public static final double DEFAULT_SPACING = 0.25;

    /** Fractal octave count for the edge wobble; kept fixed (only scale/amplitude are tunable). */
    private static final int EDGE_OCTAVES = 3;
    private static final double EDGE_PERSISTENCE = 0.5;
    private static final double EDGE_LACUNARITY = 2.0;

    // Distinct salts so the three organic stages sample independent noise fields off one base seed,
    // rather than accidentally sharing a pattern (mixed via XOR into the derived operation seed).
    private static final long EDGE_SALT = 0xD1B54A32D192ED03L;
    private static final long GRAD_SALT = 0xA0761D6478BD642FL;
    private static final long BLEND_SALT = 0xE7037ED1A0B428DBL;

    private EditPlanBuilder() { }

    /** Builds in the default {@link TerrainMode#FOLLOW_SURFACE} mode with the real organic look. */
    public static EditPlan build(List<Vec3d> controlPoints, double spacing, double width, WorldView view) {
        return build(controlPoints, spacing, width, view, TerrainMode.FOLLOW_SURFACE, OrganicTunables.defaults());
    }

    /** Builds in {@code mode} with the real organic look ({@link OrganicTunables#defaults()}). */
    public static EditPlan build(List<Vec3d> controlPoints, double spacing, double width,
                                 WorldView view, TerrainMode mode) {
        return build(controlPoints, spacing, width, view, mode, OrganicTunables.defaults());
    }

    /**
     * Builds an {@link EditPlan}, threading the M5 organic stages (edge wobble, material gradient,
     * feather blend) through the geometric pipeline. Pure and deterministic: same
     * {@code (controlPoints, spacing, width, mode, tunables)} + same WorldView answers ->
     * byte-identical plan. The organic seed is derived from the control points inside this method
     * (see {@link #operationSeed}), so the client preview and the server rebuild produce the same
     * layout without any seed field crossing the wire.
     */
    public static EditPlan build(List<Vec3d> controlPoints, double spacing, double width,
                                 WorldView view, TerrainMode mode, OrganicTunables tunables) {
        List<PathSample> raw = PathSampler.sampleWithWidth(controlPoints, spacing, width);
        // The footprint (which columns the path covers) is derived from the terrain-snapped samples in
        // every mode, so switching modes changes only the Y handling, never the plan's horizontal extent.
        List<PathSample> grounded = TerrainSampler.snapToSurface(raw, view, 0.0);
        PathMask mask = PathMask.build(grounded);

        // Derive a stable operation seed from the control points and split it into per-stage sub-seeds
        // so the three noise fields don't correlate.
        long seed = operationSeed(controlPoints);
        long edgeSeed = seed ^ EDGE_SALT;
        long gradSeed = seed ^ GRAD_SALT;
        long blendSeed = seed ^ BLEND_SALT;

        // Stage 1 (edge wobble): amplitude 0 (neutral) leaves the mask untouched; EdgeShaper shapes the
        // shared mask, so both FOLLOW_SURFACE and CUT_AND_FILL inherit the same wandering boundary.
        mask = new EdgeShaper(tunables.edgeNoiseScale(), tunables.edgeErosionFactor(),
                EDGE_OCTAVES, EDGE_PERSISTENCE, EDGE_LACUNARITY).shape(mask, edgeSeed);

        // Stage 2 (material gradient): per-column clustered block choice. A single-entry palette
        // (neutral) always returns that one block, reproducing the pre-M5 uniform look.
        GradientEngine gradient = new GradientEngine(gradSeed, tunables.toPalette());

        // Stage 3 (feather blend): near-edge inside columns may be dropped back to terrain. Guarded so
        // skirt 0 (neutral) keeps EVERY inside column (BlendEngine requires skirt > 0).
        int skirt = tunables.blendSkirtWidth();
        BlendEngine blend = skirt > 0 ? new BlendEngine(blendSeed, skirt) : null;

        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (ColumnPos c : sortedColumns(mask.insideColumns())) {
            int surfaceY = view.surfaceHeight(c.x(), c.z());
            if (mode == TerrainMode.CUT_AND_FILL) {
                // Feathering is deliberately NOT applied to CUT_AND_FILL: dropping columns out of a carve
                // would leave floating terrain lips / gaps in the cutting wall, which reads as broken
                // rather than soft. Edge wobble (stage 1) already gives the cut an organic outline, and the
                // material gradient still varies the walkable floor. So cut/fill keeps every inside column;
                // only the path block's material comes from the gradient.
                BlockStateRef pathBlock = gradient.blockAt(c.x(), surfaceY, c.z());
                emitCutAndFill(changes, c, surfaceY, targetPathY(c, raw), pathBlock);
            } else {
                // FOLLOW_SURFACE: feather first — a dropped column is left as original terrain (no change
                // emitted) so the path edge fades in. Kept columns get a clustered material from the gradient.
                if (blend != null && !blend.keepsPathBlock(mask, c.x(), c.z())) {
                    continue;
                }
                BlockStateRef block = gradient.blockAt(c.x(), surfaceY, c.z());
                GridPos pos = new GridPos(c.x(), surfaceY, c.z());
                changes.put(pos, new PlannedChange(pos, ChangeKind.TERRAIN, block));
            }
        }
        return new EditPlan(changes);
    }

    /**
     * Deterministic operation seed from the control points. Mixes {@link Double#doubleToLongBits}
     * of each point's x/y/z through a splitmix64-style finalizer — deliberately NOT {@code List}/
     * record {@code hashCode()}, whose layout isn't guaranteed stable across JVM versions. This is
     * what makes the client preview and server apply agree: identical control points -> identical
     * seed -> identical organic layout, with no seed field on the wire and no wall-clock read.
     */
    static long operationSeed(List<Vec3d> controlPoints) {
        long h = 0x9E3779B97F4A7C15L;
        for (Vec3d p : controlPoints) {
            h = mix(h, Double.doubleToLongBits(p.x()));
            h = mix(h, Double.doubleToLongBits(p.y()));
            h = mix(h, Double.doubleToLongBits(p.z()));
        }
        return h;
    }

    /** splitmix64-style mix step: fold {@code value} into the running hash {@code h}. */
    private static long mix(long h, long value) {
        h ^= value;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 31);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 29);
        return h;
    }

    /**
     * CUT_AND_FILL: the path holds its own interpolated elevation. The walkable path block is placed at
     * {@code pathY}; terrain standing above it is carved to air (REMOVE), and a dirt foundation is placed
     * up to it where the ground has dropped away below (PLACE).
     */
    private static void emitCutAndFill(Map<GridPos, PlannedChange> changes, ColumnPos c, int surfaceY, int pathY,
                                       BlockStateRef pathBlock) {
        GridPos pathPos = new GridPos(c.x(), pathY, c.z());
        changes.put(pathPos, new PlannedChange(pathPos, ChangeKind.TERRAIN, pathBlock));

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
