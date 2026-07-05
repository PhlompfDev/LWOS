package com.lwos.geometry;

/** A resampled centerline point carrying a path width (spec §4.2, "PathSampler carries width/profile/bank per sample"). */
public record PathSample(Vec3d position, double width) { }
