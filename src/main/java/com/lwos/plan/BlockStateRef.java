package com.lwos.plan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure-data stand-in for a block state (spec §5 boundary rule): the {@code plan} package
 * cannot import Minecraft types, so a target block is carried as its registry id string
 * (e.g. {@code "minecraft:dirt_path"}). The apply/preview sides resolve it to a Forge
 * {@code BlockState} via {@code ForgeRegistries.BLOCKS}.
 *
 * <p>{@code properties} carries block-state variant values purely as strings
 * (e.g. {@code {"facing":"east","half":"bottom"}} for stairs). The apply side
 * ({@code PlacementEngine}) translates each entry into the matching Forge
 * {@code Property<?>} value — the {@code plan} package still never imports Minecraft.
 * The map is immutable; equality (and therefore {@code EditPlan} determinism) accounts
 * for the properties.
 */
public record BlockStateRef(String id, Map<String, String> properties) {

    public BlockStateRef {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    /** Convenience for the common no-properties case (keeps existing call sites unchanged). */
    public BlockStateRef(String id) {
        this(id, Map.of());
    }

    /** Returns a copy of this ref with {@code key=value} added/overridden in its property map. */
    public BlockStateRef with(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(properties);
        next.put(key, value);
        return new BlockStateRef(id, next);
    }
}
