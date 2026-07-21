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
        List<GridPos> cells = rasterize(anchors, mode, hollow);

        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (GridPos p : cells) {
            boolean airHere = isAirLike(world.blockIdAt(p.x(), p.y(), p.z()));
            if (breakMode) {
                if (!airHere) changes.put(p, new PlannedChange(p, ChangeKind.REMOVE, AIR));
            } else {
                if (airHere) changes.put(p, new PlannedChange(p, ChangeKind.PLACE, material));
            }
        }
        return new EditPlan(changes);
    }

    private static List<GridPos> rasterize(List<GridPos> anchors, ShapeMode mode, boolean hollow) {
        GridPos a = anchors.get(0);
        GridPos b = anchors.get(1);
        return switch (mode) {
            case LINE -> ShapeGeometry.line(a, b);
            case WALL, FLOOR -> ShapeGeometry.rect(a, b, hollow);
            case CUBE -> ShapeGeometry.box(a, new GridPos(b.x(), anchors.get(2).y(), b.z()), hollow);
            case CIRCLE -> ShapeGeometry.circle(a, horizontalRadius(a, b), hollow);
            case SPHERE -> ShapeGeometry.sphere(a, horizontalRadius(a, b), hollow);
        };
    }

    /** Radius = rounded horizontal distance center → radius point (both aim on the center's Y plane). */
    private static int horizontalRadius(GridPos center, GridPos edge) {
        double dx = edge.x() - center.x(), dz = edge.z() - center.z();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    private static boolean isAirLike(String id) {
        return "minecraft:air".equals(id) || "minecraft:cave_air".equals(id) || "minecraft:void_air".equals(id);
    }
}
