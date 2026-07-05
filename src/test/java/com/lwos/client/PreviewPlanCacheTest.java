package com.lwos.client;

import com.lwos.plan.EditPlan;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreviewPlanCacheTest {

    private static EditPlan empty() { return new EditPlan(Map.of()); }

    @Test
    void rebuildsOnFirstUse() {
        PreviewPlanCache cache = new PreviewPlanCache();
        assertTrue(cache.needsRebuild(new PreviewPlanCache.Key(1, 1, 3.0, 0), 1000));
    }

    @Test
    void doesNotRebuildForSameKeyImmediately() {
        PreviewPlanCache cache = new PreviewPlanCache();
        PreviewPlanCache.Key k = new PreviewPlanCache.Key(1, 1, 3.0, 0);
        cache.accept(k, empty(), 1000);
        assertFalse(cache.needsRebuild(k, 1010), "same key just accepted: no rebuild");
    }

    @Test
    void rebuildsWhenKeyChangesAndIntervalElapsed() {
        PreviewPlanCache cache = new PreviewPlanCache();
        cache.accept(new PreviewPlanCache.Key(1, 1, 3.0, 0), empty(), 1000);
        // key changed (style version 2) and 80ms > 75ms elapsed
        assertTrue(cache.needsRebuild(new PreviewPlanCache.Key(2, 1, 3.0, 0), 1080));
    }

    @Test
    void throttlesRapidChangesWithinTheInterval() {
        PreviewPlanCache cache = new PreviewPlanCache();
        cache.accept(new PreviewPlanCache.Key(1, 1, 3.0, 0), empty(), 1000);
        // key changed but only 30ms elapsed (< 75ms): throttle
        assertFalse(cache.needsRebuild(new PreviewPlanCache.Key(2, 1, 3.0, 0), 1030));
    }
}
