package com.lwos.shape;

import com.lwos.geometry.WorldView;
import com.lwos.plan.BlockStateRef;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a shape gesture (anchors + mode + options) to an {@link EditPlan} (spec §4).
 * The client preview and the server's ShapeRequestPacket handler both call this over
 * their own {@link WorldView}, so preview == apply holds by construction. Deterministic:
 * no RNG, no clocks — identical anchors + identical world answers = identical plan.
 */
public final class ShapePlanBuilder {
    private static final BlockStateRef AIR = new BlockStateRef("minecraft:air");

    private ShapePlanBuilder() { }

    public static EditPlan build(List<GridPos> anchors, ShapeMode mode, ShapeOptions options,
                                 BlockStateRef material, boolean breakMode, WorldView world) {
        if (anchors.size() != mode.clickCount()) {
            throw new IllegalArgumentException(mode + " needs " + mode.clickCount()
                    + " anchors, got " + anchors.size());
        }
        boolean hollow = mode.supportsFill() && options.hollow();
        List<GridPos> cells = rasterize(anchors, mode, options, hollow);

        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (GridPos p : cells) {
            if (breakMode) {
                // Break clears anything that isn't already air (grass and snow included).
                if (!isAirLike(world.blockIdAt(p.x(), p.y(), p.z()))) {
                    changes.put(p, new PlannedChange(p, ChangeKind.REMOVE, AIR));
                }
            } else {
                // Place fills anything replaceable — air, grass, flowers, snow layers —
                // so builds don't come out with plant-shaped holes (2026-07-21 playtest fix).
                if (world.isReplaceableAt(p.x(), p.y(), p.z())) {
                    changes.put(p, new PlannedChange(p, ChangeKind.PLACE, material));
                }
            }
        }
        return new EditPlan(changes);
    }

    private static List<GridPos> rasterize(List<GridPos> anchors, ShapeMode mode,
                                           ShapeOptions options, boolean hollow) {
        GridPos a = anchors.get(0);
        GridPos b = anchors.get(1);
        return switch (mode) {
            case LINE -> ShapeGeometry.line(a, b);
            case RECT -> ShapeGeometry.rectAuto(a, b, hollow);
            case CUBE -> ShapeGeometry.box(a, extrudedCorner(a, b, anchors.get(2)), hollow);
            case CIRCLE -> ShapeGeometry.circle(a, planeRadius(a, b, options.axis()), hollow,
                    options.axis().ordinal());
            case SPHERE -> ShapeGeometry.sphere(a, radius3d(a, b), hollow);
        };
    }

    /**
     * Cube's far corner: the base rectangle is the free rect of a→b (collapsed plane),
     * and the third anchor extrudes it along the base plane's fixed axis — a base drawn
     * on a wall extrudes horizontally out of the wall, a floor base extrudes vertically.
     */
    private static GridPos extrudedCorner(GridPos a, GridPos b, GridPos c) {
        int axis = ShapeGeometry.rectFixedAxis(a, b);
        GridPos base = ShapeGeometry.collapseToPlane(a, b);
        return switch (axis) {
            case 0 -> new GridPos(c.x(), base.y(), base.z());
            case 1 -> new GridPos(base.x(), c.y(), base.z());
            default -> new GridPos(base.x(), base.y(), c.z());
        };
    }

    /** Radius = rounded distance center → edge, projected onto the circle's plane. */
    private static int planeRadius(GridPos center, GridPos edge, ShapeOptions.Axis axis) {
        double dx = edge.x() - center.x(), dy = edge.y() - center.y(), dz = edge.z() - center.z();
        return (int) Math.round(switch (axis) {
            case X -> Math.sqrt(dz * dz + dy * dy);
            case Z -> Math.sqrt(dx * dx + dy * dy);
            case Y -> Math.sqrt(dx * dx + dz * dz);
        });
    }

    /** Sphere radius = rounded true 3D distance (free-placement revision). */
    private static int radius3d(GridPos center, GridPos edge) {
        double dx = edge.x() - center.x(), dy = edge.y() - center.y(), dz = edge.z() - center.z();
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static boolean isAirLike(String id) {
        return "minecraft:air".equals(id) || "minecraft:cave_air".equals(id) || "minecraft:void_air".equals(id);
    }
}
