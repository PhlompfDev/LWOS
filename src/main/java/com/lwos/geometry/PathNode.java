package com.lwos.geometry;

/**
 * A control point placed by the player. M1 uses only position; width, height
 * offset and bank angle (spec §4.2) are added in later milestones.
 */
public record PathNode(Vec3d position) { }
