package com.lwos.shape;

import com.google.gson.Gson;

/**
 * Per-gesture shape options (spec §4). Immutable; lenient JSON round-trip on the
 * PathStyle contract: unknown keys ignored, missing/malformed input falls back to
 * defaults so old clients and crafted packets degrade safely.
 */
public record ShapeOptions(Fill fill) {
    public enum Fill { FILLED, HOLLOW }

    public static final ShapeOptions DEFAULT = new ShapeOptions(Fill.FILLED);

    private static final Gson GSON = new Gson();

    public ShapeOptions {
        if (fill == null) fill = Fill.FILLED;
    }

    public ShapeOptions cycleFill() {
        return new ShapeOptions(fill == Fill.FILLED ? Fill.HOLLOW : Fill.FILLED);
    }

    public boolean hollow() { return fill == Fill.HOLLOW; }

    /** Serialized shape carried in ShapeRequestPacket's optionsJson. */
    private record Dto(String fill) { }

    public String toJson() {
        return GSON.toJson(new Dto(fill.name()));
    }

    public static ShapeOptions fromJson(String json) {
        if (json == null || json.isBlank()) return DEFAULT;
        try {
            Dto dto = GSON.fromJson(json, Dto.class);
            if (dto == null || dto.fill() == null) return DEFAULT;
            return new ShapeOptions(Fill.valueOf(dto.fill()));
        } catch (RuntimeException e) {
            return DEFAULT; // malformed json or unknown enum value — lenient by contract
        }
    }
}
