package com.lwos.client;

import com.lwos.plan.EditPlan;

/**
 * Debounced cache for the live preview plan. The renderer asks {@link #needsRebuild} each frame;
 * a rebuild happens only when the key (style version + path revision + width + mode) changed AND at
 * least {@link #MIN_REBUILD_INTERVAL_MS} elapsed since the last accepted rebuild. This is the
 * "continuous with a safety debounce" behavior and replaces the previous per-frame rebuild.
 * Pure (clock is passed in) so it is headless-testable.
 */
public final class PreviewPlanCache {

    public static final long MIN_REBUILD_INTERVAL_MS = 75;

    /** Path-preview key (PathRenderer). Other tools may key with any equals-comparable record. */
    public record Key(long styleVersion, long pathRevision, double width, int modeOrdinal) { }

    private Object acceptedKey = null;
    private EditPlan last = null;
    private long lastRebuildMillis = Long.MIN_VALUE;

    public boolean needsRebuild(Object key, long nowMillis) {
        if (last == null || acceptedKey == null) return true;
        if (key.equals(acceptedKey)) return false;
        return nowMillis - lastRebuildMillis >= MIN_REBUILD_INTERVAL_MS;
    }

    public void accept(Object key, EditPlan plan, long nowMillis) {
        this.acceptedKey = key;
        this.last = plan;
        this.lastRebuildMillis = nowMillis;
    }

    public EditPlan last() { return last; }
}
