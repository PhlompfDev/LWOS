package com.lwos.plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable contract between compute, preview, and apply (spec §5). Built only by
 * EditPlanBuilder; never mutated after construction.
 */
public final class EditPlan {
    private final Map<GridPos, PlannedChange> changes;

    public EditPlan(Map<GridPos, PlannedChange> changes) {
        this.changes = Collections.unmodifiableMap(new LinkedHashMap<>(changes));
    }

    public Map<GridPos, PlannedChange> changes() { return changes; }

    public int size() { return changes.size(); }

    public boolean isEmpty() { return changes.isEmpty(); }
}
