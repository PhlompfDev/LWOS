package com.lwos.plan;

/**
 * Pure-data stand-in for a block state (spec §5 boundary rule): the {@code plan} package
 * cannot import Minecraft types, so a target block is carried as its registry id string
 * (e.g. {@code "minecraft:dirt_path"}). The apply/preview sides resolve it to a Forge
 * {@code BlockState} via {@code ForgeRegistries.BLOCKS}.
 */
public record BlockStateRef(String id) { }
