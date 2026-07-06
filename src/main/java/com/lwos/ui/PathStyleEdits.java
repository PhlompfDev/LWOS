package com.lwos.ui;

import com.lwos.config.PathStyle;
import com.lwos.config.StyleManager;

import java.util.ArrayList;
import java.util.List;

/** Mutation helpers that rebuild the immutable working PathStyle and publish it via StyleManager. */
public final class PathStyleEdits {
    private PathStyleEdits() { }

    private static PathStyle rebuild(List<PathStyle.Entry> core, List<PathStyle.Entry> edge, PathStyle s) {
        return new PathStyle(core, edge, s.edgeRoughness(), s.edgeFeatureSize(),
                s.featherDepth(), s.coreProtect(), s.defaultClusterSize());
    }

    public static void setCoreSlotBlock(int index, String blockId) {
        PathStyle s = StyleManager.active();
        List<PathStyle.Entry> core = new ArrayList<>(s.core());
        if (index == core.size()) core.add(new PathStyle.Entry(blockId, 1.0, 0.1, s.defaultClusterSize()));
        else if (index >= 0 && index < core.size()) {
            PathStyle.Entry e = core.get(index);
            core.set(index, new PathStyle.Entry(blockId, e.weight(), e.noiseScale(), e.clusterSize()));
        } else return;
        StyleManager.setActive(rebuild(core, s.edge(), s));
    }

    public static void setEdgeSlotBlock(int index, String blockId) {
        PathStyle s = StyleManager.active();
        List<PathStyle.Entry> edge = new ArrayList<>(s.edge());
        if (index == edge.size()) edge.add(new PathStyle.Entry(blockId, 1.0, 0.1, s.defaultClusterSize()));
        else if (index >= 0 && index < edge.size()) {
            PathStyle.Entry e = edge.get(index);
            edge.set(index, new PathStyle.Entry(blockId, e.weight(), e.noiseScale(), e.clusterSize()));
        } else return;
        StyleManager.setActive(rebuild(new ArrayList<>(s.core()), edge, s));
    }

    public static void setCoreWeight(int index, double weight) {
        PathStyle s = StyleManager.active();
        if (index < 0 || index >= s.core().size()) return;
        List<PathStyle.Entry> core = new ArrayList<>(s.core());
        PathStyle.Entry e = core.get(index);
        core.set(index, new PathStyle.Entry(e.id(), Math.max(0.01, weight), e.noiseScale(), e.clusterSize()));
        StyleManager.setActive(rebuild(core, s.edge(), s));
    }

    public static void setEdgeWeight(int index, double weight) {
        PathStyle s = StyleManager.active();
        if (index < 0 || index >= s.edge().size()) return;
        List<PathStyle.Entry> edge = new ArrayList<>(s.edge());
        PathStyle.Entry e = edge.get(index);
        edge.set(index, new PathStyle.Entry(e.id(), Math.max(0.01, weight), e.noiseScale(), e.clusterSize()));
        StyleManager.setActive(rebuild(new ArrayList<>(s.core()), edge, s));
    }

    public static void setFeatherDepth(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                s.edgeRoughness(), s.edgeFeatureSize(), v, s.coreProtect(), s.defaultClusterSize()));
    }

    public static void setClusterSize(double cluster) {
        PathStyle s = StyleManager.active();
        double c = Math.max(0.5, cluster);
        List<PathStyle.Entry> core = new ArrayList<>();
        for (PathStyle.Entry e : s.core()) core.add(new PathStyle.Entry(e.id(), e.weight(), e.noiseScale(), c));
        StyleManager.setActive(new PathStyle(core, new ArrayList<>(s.edge()),
                s.edgeRoughness(), s.edgeFeatureSize(), s.featherDepth(), s.coreProtect(), c));
    }

    public static void setEdgeRoughness(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                v, s.edgeFeatureSize(), s.featherDepth(), s.coreProtect(), s.defaultClusterSize()));
    }

    public static void setEdgeFeatureSize(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                s.edgeRoughness(), Math.max(0.001, v), s.featherDepth(), s.coreProtect(), s.defaultClusterSize()));
    }

    public static void setCoreProtect(double v) {
        PathStyle s = StyleManager.active();
        StyleManager.setActive(new PathStyle(new ArrayList<>(s.core()), new ArrayList<>(s.edge()),
                s.edgeRoughness(), s.edgeFeatureSize(), s.featherDepth(), v, s.defaultClusterSize()));
    }

    public static void removeCoreSlot(int index) {
        PathStyle s = StyleManager.active();
        if (index < 0 || index >= s.core().size() || s.core().size() == 1) return; // keep at least one core
        List<PathStyle.Entry> core = new ArrayList<>(s.core());
        core.remove(index);
        StyleManager.setActive(rebuild(core, s.edge(), s));
    }

    public static void removeEdgeSlot(int index) {
        PathStyle s = StyleManager.active();
        if (index < 0 || index >= s.edge().size()) return;
        List<PathStyle.Entry> edge = new ArrayList<>(s.edge());
        edge.remove(index);
        StyleManager.setActive(rebuild(new ArrayList<>(s.core()), edge, s));
    }
}
