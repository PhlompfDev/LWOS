package com.lwos.brush;

import com.lwos.geometry.WorldView;
import com.lwos.plan.BlockStateRef;
import com.lwos.plan.ChangeKind;
import com.lwos.plan.EditPlan;
import com.lwos.plan.GridPos;
import com.lwos.plan.PlannedChange;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@link EditPlan} for one brush dab (spec §2): sample the local ground
 * {@link HeightField}, run the {@link BrushOp} kernel, and diff old->new heights per column.
 * Raises stack copies of the column's own current surface block (spec §3); lowers carve to air.
 * Iteration order is fixed (z ascending, then x ascending) into a LinkedHashMap, so identical
 * inputs + identical WorldView answers produce a byte-identical plan — the same determinism
 * contract as EditPlanBuilder, which is what lets preview and apply share this one method.
 */
public final class BrushPlanBuilder {
    private static final BlockStateRef AIR = new BlockStateRef("minecraft:air");

    private BrushPlanBuilder() { }

    public static EditPlan build(BrushOp op, int cx, int cz, int radius, WorldView view) {
        HeightField before = HeightField.sample(view, cx, cz, radius);
        HeightField after = op.apply(before, cx, cz, radius);
        Map<GridPos, PlannedChange> changes = new LinkedHashMap<>();
        for (int z = cz - radius; z <= cz + radius; z++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                int oldH = before.height(x, z);
                int newH = after.height(x, z);
                if (newH > oldH) {
                    // Raise: grass-topped columns grow grass-topped (spec §3 — v1 has no
                    // stratigraphy; material fidelity belongs to a later masks/painting phase).
                    BlockStateRef surface = new BlockStateRef(view.surfaceBlockId(x, z));
                    for (int y = oldH + 1; y <= newH; y++) {
                        GridPos pos = new GridPos(x, y, z);
                        changes.put(pos, new PlannedChange(pos, ChangeKind.PLACE, surface));
                    }
                } else if (newH < oldH) {
                    for (int y = newH + 1; y <= oldH; y++) {
                        GridPos pos = new GridPos(x, y, z);
                        changes.put(pos, new PlannedChange(pos, ChangeKind.REMOVE, AIR));
                    }
                }
            }
        }
        return new EditPlan(changes);
    }
}
