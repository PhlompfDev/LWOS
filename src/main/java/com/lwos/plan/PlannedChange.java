package com.lwos.plan;

/** One entry of an EditPlan: a grid cell, the kind of change, and the target block state (pure ref). */
public record PlannedChange(GridPos pos, ChangeKind kind, BlockStateRef state) { }
