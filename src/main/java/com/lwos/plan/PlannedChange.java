package com.lwos.plan;

/** One entry of an EditPlan. Target block state is added in M5 once the organic engine chooses materials. */
public record PlannedChange(GridPos pos, ChangeKind kind) { }
