package com.lwos.shape;

import com.google.gson.Gson;

/**
 * Per-gesture shape options (spec §4; axis added in the 2026-07-21 free-placement
 * revision). Immutable; lenient JSON round-trip on the PathStyle contract: unknown keys
 * ignored, missing/malformed input falls back to defaults so old clients and crafted
 * packets degrade safely.
 *
 * <p>{@code axis} is the plane normal for CIRCLE (captured from the clicked face:
 * click a wall, get a vertical circle) — other modes ignore it. Default Y keeps the
 * classic horizontal circle for old payloads.
 */
public record ShapeOptions(Fill fill, Axis axis) {
    public enum Fill { FILLED, HOLLOW }

    /** A world axis (plane normal). Ordinals are stable: X=0, Y=1, Z=2. */
    public enum Axis { X, Y, Z }

    public static final ShapeOptions DEFAULT = new ShapeOptions(Fill.FILLED, Axis.Y);

    private static final Gson GSON = new Gson();

    public ShapeOptions {
        if (fill == null) fill = Fill.FILLED;
        if (axis == null) axis = Axis.Y;
    }

    /** Convenience for the common default-axis case (keeps existing call sites unchanged). */
    public ShapeOptions(Fill fill) {
        this(fill, Axis.Y);
    }

    public ShapeOptions cycleFill() {
        return new ShapeOptions(fill == Fill.FILLED ? Fill.HOLLOW : Fill.FILLED, axis);
    }

    public ShapeOptions withAxis(Axis newAxis) {
        return new ShapeOptions(fill, newAxis);
    }

    public boolean hollow() { return fill == Fill.HOLLOW; }

    /** Serialized shape carried in ShapeRequestPacket's optionsJson. */
    private record Dto(String fill, String axis) { }

    public String toJson() {
        return GSON.toJson(new Dto(fill.name(), axis.name()));
    }

    public static ShapeOptions fromJson(String json) {
        if (json == null || json.isBlank()) return DEFAULT;
        try {
            Dto dto = GSON.fromJson(json, Dto.class);
            if (dto == null) return DEFAULT;
            Fill fill = dto.fill() == null ? Fill.FILLED : Fill.valueOf(dto.fill());
            Axis axis = dto.axis() == null ? Axis.Y : Axis.valueOf(dto.axis());
            return new ShapeOptions(fill, axis);
        } catch (RuntimeException e) {
            return DEFAULT; // malformed json or unknown enum value — lenient by contract
        }
    }
}
