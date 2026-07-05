package com.lwos.ui;

import com.lwos.config.PathStyle;
import com.lwos.config.StyleManager;

import java.util.ArrayList;
import java.util.List;

/** Mutation helpers that rebuild the working PathStyle and publish it via StyleManager. */
public final class PathStyleEdits {
    private PathStyleEdits() { }

    /** Sets the block id of core slot {@code index}, adding a slot if index == core size. */
    public static void setCoreSlotBlock(int index, String blockId) {
        PathStyle s = StyleManager.active();
        List<PathStyle.Entry> core = new ArrayList<>(s.core());
        if (index == core.size()) {
            core.add(new PathStyle.Entry(blockId, 1.0, 0.1, s.defaultClusterSize()));
        } else if (index >= 0 && index < core.size()) {
            PathStyle.Entry e = core.get(index);
            core.set(index, new PathStyle.Entry(blockId, e.weight(), e.noiseScale(), e.clusterSize()));
        } else {
            return;
        }
        StyleManager.setActive(new PathStyle(core, s.edge(), s.edgeErosionFactor(),
                s.edgeNoiseScale(), s.blendSkirtWidth(), s.defaultClusterSize()));
    }
}
