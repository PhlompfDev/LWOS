package com.lwos.plan;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EditPlanTest {

    @Test
    void wrapsChangesAndReportsSize() {
        GridPos pos = new GridPos(1, 64, 2);
        PlannedChange change = new PlannedChange(pos, ChangeKind.TERRAIN, new BlockStateRef("minecraft:dirt_path"));
        Map<GridPos, PlannedChange> map = new LinkedHashMap<>();
        map.put(pos, change);

        EditPlan plan = new EditPlan(map);
        assertEquals(1, plan.size());
        assertFalse(plan.isEmpty());
        assertEquals(change, plan.changes().get(pos));
        assertEquals("minecraft:dirt_path", plan.changes().get(pos).state().id());
    }

    @Test
    void emptyPlanIsEmpty() {
        EditPlan plan = new EditPlan(Map.of());
        assertTrue(plan.isEmpty());
        assertEquals(0, plan.size());
    }

    @Test
    void changesMapIsUnmodifiable() {
        EditPlan plan = new EditPlan(Map.of());
        assertThrows(UnsupportedOperationException.class,
                () -> plan.changes().put(new GridPos(0, 0, 0),
                        new PlannedChange(new GridPos(0, 0, 0), ChangeKind.PLACE, new BlockStateRef("minecraft:stone"))));
    }

    @Test
    void mutatingSourceMapAfterConstructionDoesNotAffectPlan() {
        Map<GridPos, PlannedChange> map = new LinkedHashMap<>();
        EditPlan plan = new EditPlan(map);
        map.put(new GridPos(0, 0, 0), new PlannedChange(new GridPos(0, 0, 0), ChangeKind.REMOVE, new BlockStateRef("minecraft:air")));
        assertTrue(plan.isEmpty()); // defensive copy taken at construction time
    }
}
