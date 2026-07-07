package com.lwos.ui.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Single source of truth for the builder's-journal UI theme: every ResourceLocation, atlas
 * coordinate, and theme color, plus the slice-blit helpers all four surfaces draw with.
 * The atlas layout mirrors the asset contract in
 * docs/superpowers/specs/2026-07-07-journal-ui-reskin-design.md — regions may be added to
 * widgets.png but existing ones must never move.
 */
public final class JournalTheme {
    private JournalTheme() { }

    public static final ResourceLocation PANEL      = new ResourceLocation("lwos", "textures/gui/journal/panel.png");
    public static final ResourceLocation WIDGETS    = new ResourceLocation("lwos", "textures/gui/journal/widgets.png");
    public static final ResourceLocation TOOL_WHEEL = new ResourceLocation("lwos", "textures/gui/journal/tool_wheel.png");
    public static final ResourceLocation HUD_PLATE  = new ResourceLocation("lwos", "textures/gui/journal/hud_plate.png");

    // Ink & material colors (ARGB).
    public static final int INK       = 0xFF3B2E1E;
    public static final int INK_FADED = 0xFF7A6A50;
    public static final int WAX       = 0xFFA8352C;
    /** Translucent ink wash for the filled portion of slider tracks. */
    public static final int INK_FILL  = 0x733B2E1E;

    // widgets.png atlas (128x128).
    public static final int ATLAS = 128;
    public static final int TRACK_U = 0,   TRACK_V = 0,  TRACK_W = 32, TRACK_TEX_H = 8, TRACK_CAP = 4;
    public static final int KNOB_U = 0,    KNOB_V = 8,   KNOB_W = 8,   KNOB_H = 12;
    public static final int CHIP_U = 32,   CHIP_V = 0;
    public static final int CHIP_SEL_U = 32, CHIP_SEL_V = 16;
    public static final int CHIP_W = 24,   CHIP_H = 16,  CHIP_INSET = 5;
    public static final int SLOT_U = 64,   SLOT_V = 0,   SLOT_W = 20,  SLOT_H = 20, SLOT_INSET = 6;
    public static final int FIELD_U = 0,   FIELD_V = 32, FIELD_W = 48, FIELD_H = 16, FIELD_INSET = 5;
    public static final int STITCH_U = 0,  STITCH_V = 48, STITCH_W = 64, STITCH_H = 4;
    public static final int STRAP_U = 88,  STRAP_V = 0,  STRAP_W = 6,  STRAP_H = 24, STRAP_CAP = 6;

    // Standalone sheets.
    public static final int PANEL_TEX = 48,  PANEL_INSET = 12;
    public static final int HUD_TEX_W = 32,  HUD_TEX_H = 16, HUD_INSET = 6;

    /** 1:1 blit of a widgets.png region. */
    public static void blitRegion(GuiGraphics g, int u, int v, int rw, int rh, int x, int y) {
        RenderSystem.enableBlend();
        g.blit(WIDGETS, x, y, rw, rh, u, v, rw, rh, ATLAS, ATLAS);
        RenderSystem.disableBlend();
    }

    /** Horizontal 3-slice from widgets.png: fixed end caps, stretched middle. */
    public static void blitH3(GuiGraphics g, int u, int v, int rw, int rh, int cap, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        g.blit(WIDGETS, x, y, cap, h, u, v, cap, rh, ATLAS, ATLAS);
        g.blit(WIDGETS, x + w - cap, y, cap, h, u + rw - cap, v, cap, rh, ATLAS, ATLAS);
        if (w > 2 * cap) {
            g.blit(WIDGETS, x + cap, y, w - 2 * cap, h, u + cap, v, rw - 2 * cap, rh, ATLAS, ATLAS);
        }
        RenderSystem.disableBlend();
    }

    /** Vertical 3-slice from widgets.png: fixed top/bottom caps, stretched middle. */
    public static void blitV3(GuiGraphics g, int u, int v, int rw, int rh, int cap, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        g.blit(WIDGETS, x, y, w, cap, u, v, rw, cap, ATLAS, ATLAS);
        g.blit(WIDGETS, x, y + h - cap, w, cap, u, v + rh - cap, rw, cap, ATLAS, ATLAS);
        if (h > 2 * cap) {
            g.blit(WIDGETS, x, y + cap, w, h - 2 * cap, u, v + cap, rw, rh - 2 * cap, ATLAS, ATLAS);
        }
        RenderSystem.disableBlend();
    }

    /** Tiles a widgets.png region horizontally across `w` pixels (last tile clipped). */
    public static void blitTiledH(GuiGraphics g, int u, int v, int rw, int rh, int x, int y, int w) {
        RenderSystem.enableBlend();
        for (int dx = 0; dx < w; dx += rw) {
            int seg = Math.min(rw, w - dx);
            g.blit(WIDGETS, x + dx, y, seg, rh, u, v, seg, rh, ATLAS, ATLAS);
        }
        RenderSystem.disableBlend();
    }

    /** Shortcut: nine-slice a widgets.png region. */
    public static void blitWidgetNineSlice(GuiGraphics g, int u, int v, int rw, int rh, int inset,
                                           int x, int y, int w, int h) {
        blitNineSlice(g, WIDGETS, ATLAS, ATLAS, u, v, rw, rh, inset, x, y, w, h);
    }

    /**
     * Nine-slice blit with TILED (not stretched) edges and center, so parchment grain never
     * smears on tall panels. Corners 1:1; edges tile along their axis; center tiles both ways.
     */
    public static void blitNineSlice(GuiGraphics g, ResourceLocation tex, int texW, int texH,
                                     int u, int v, int rw, int rh, int inset,
                                     int x, int y, int w, int h) {
        int cw = rw - 2 * inset, ch = rh - 2 * inset;  // source center size
        if (cw <= 0 || ch <= 0) {
            throw new IllegalArgumentException("nine-slice inset " + inset
                    + " leaves no center in region " + rw + "x" + rh);
        }
        RenderSystem.enableBlend();
        int dw = w - 2 * inset,  dh = h - 2 * inset;   // destination center size
        // corners
        g.blit(tex, x, y, inset, inset, u, v, inset, inset, texW, texH);
        g.blit(tex, x + w - inset, y, inset, inset, u + rw - inset, v, inset, inset, texW, texH);
        g.blit(tex, x, y + h - inset, inset, inset, u, v + rh - inset, inset, inset, texW, texH);
        g.blit(tex, x + w - inset, y + h - inset, inset, inset, u + rw - inset, v + rh - inset, inset, inset, texW, texH);
        // top/bottom edges, tiled horizontally
        for (int dx = 0; dx < dw; dx += cw) {
            int seg = Math.min(cw, dw - dx);
            g.blit(tex, x + inset + dx, y, seg, inset, u + inset, v, seg, inset, texW, texH);
            g.blit(tex, x + inset + dx, y + h - inset, seg, inset, u + inset, v + rh - inset, seg, inset, texW, texH);
        }
        // left/right edges, tiled vertically
        for (int dy = 0; dy < dh; dy += ch) {
            int seg = Math.min(ch, dh - dy);
            g.blit(tex, x, y + inset + dy, inset, seg, u, v + inset, inset, seg, texW, texH);
            g.blit(tex, x + w - inset, y + inset + dy, inset, seg, u + rw - inset, v + inset, inset, seg, texW, texH);
        }
        // center, tiled both ways
        for (int dy = 0; dy < dh; dy += ch) {
            for (int dx = 0; dx < dw; dx += cw) {
                int sw = Math.min(cw, dw - dx), sh = Math.min(ch, dh - dy);
                g.blit(tex, x + inset + dx, y + inset + dy, sw, sh, u + inset, v + inset, sw, sh, texW, texH);
            }
        }
        RenderSystem.disableBlend();
    }
}
