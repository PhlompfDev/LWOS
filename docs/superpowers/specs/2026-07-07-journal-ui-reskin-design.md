# Builder's Journal UI Reskin — Design

**Date:** 2026-07-07
**Status:** Approved (design review with Phlompf)
**Scope:** Visual rewrite of all four 2D UI surfaces with hand-made custom textures. Architecture preserved.

## 1. Goal

Replace the flat-color "frosted glass" look (translucent dark fill `0xB0121419`, blue accent
`0x7CD3FF`) across every UI surface with a **builder's field journal** identity: parchment
panels with torn edges, ink linework, leather/wood trim, brass hardware. The mod currently
ships zero GUI textures (assets contain only `lang/en_us.json`); this feature introduces the
texture pipeline and the theme layer.

**Explicitly not a structural rewrite.** The overlay-first immediate-mode architecture
(HUD-overlay panel, frame-published layout records, Ctrl-to-edit cursor mode) is the point of
the mod — styling while watching the live preview — and is kept as-is.

## 2. Surfaces in scope

| Surface | File(s) | Treatment |
|---|---|---|
| Path Style panel | `ui/PathStylePanel.java` | Parchment nine-slice body, ink header, stitch dividers, paper-tag chips, leather scrollbar strap, faded-ink footer hint |
| Panel widgets | `ui/components/SliderWidget.java`, `ui/components/BlockSlotWidget.java` | Ink slider track + brass knob; framed block slots |
| Block picker | `ui/BlockSearchScreen.java` | Centered parchment sheet, ink-ruled search field, framed grid slots, ink hover outline, wax selection |
| Tool wheel | `client/ToolWheelOverlay.java` | Parchment compass disc, wax-red selected wedge, ink labels |
| Mode HUD | `client/ModeHudOverlay.java` | Small parchment plate, ink text |

**Out of scope:** in-world rendering (`PathRenderer`, `PreviewRenderer`, node markers,
preview tint) — that is world rendering, not UI, and the rendering subsystem has known
battle scars. No `PathStyle`, packet, or plan-pipeline changes of any kind.

## 3. Visual identity

- **Resolution:** vanilla-style pixel art — 1 texture pixel = 1 GUI pixel. The hand-made
  quality comes from wobbly ink lines, uneven parchment tone, deckled edges, and stitch
  marks, **not** from hi-res painterly art (which blurs and looks out of place at Minecraft
  GUI scale).
- **Translucency:** the style panel parchment is ~90% opaque so the world/preview stays
  visible behind it. The modal `BlockSearchScreen` sheet may be fully opaque.
- **Text:** vanilla font throughout, recolored to ink. **No text shadow** on parchment
  surfaces (shadows are a dark-background idiom).

### Palette

| Role | Value | Notes |
|---|---|---|
| Parchment light | `#E4D5AC` | base fill, fiber noise toward dark |
| Parchment dark | `#CBB98A` | noise floor / edge shading |
| Ink | `#3B2E1E` | body text, linework, slider tracks |
| Faded ink | `#7A6A50` | hints, footer, disabled |
| Leather | `#6B4A2B` | corner caps, scrollbar strap, trim |
| Brass | `#B08D4F` | slider knobs, button hardware |
| Wax red (accent) | `#A8352C` | selection, active chip, selected wheel wedge — replaces `0x7CD3FF` everywhere |

## 4. Asset architecture

New directory: `src/main/resources/assets/lwos/textures/gui/journal/`

### Asset contract

Any sheet can be hand-repainted later; only the sizes, slice insets, and region positions
below are load-bearing.

| File | Size | Contents / slicing |
|---|---|---|
| `panel.png` | 48×48 | Nine-slice parchment panel, 12px insets. Torn/deckled edges and leather corner caps live in the border regions; center tile is seamless parchment. |
| `widgets.png` | 128×128 atlas | Regions below. |
| `tool_wheel.png` | 128×128 | Round parchment disc with compass-rose ink detail, centered, transparent corners. Selected-wedge highlight stays code-drawn (translucent wax red) so wedge count is free to change. |
| `hud_plate.png` | 32×16 | Nine-slice parchment strip, 6px insets. |

`widgets.png` regions:

| Region | Pos / size | Slicing |
|---|---|---|
| Slider track | (0,0) 32×8 | horizontal 3-slice, 4px end caps |
| Slider knob (brass) | (0,8) 8×12 | fixed |
| Chip / paper tag, normal | (32,0) 24×16 | nine-slice, 5px insets |
| Chip, selected (wax edge) | (32,16) 24×16 | nine-slice, 5px insets |
| Block slot frame | (64,0) 20×20 | nine-slice, 6px insets (panel slots are 30×30, search-grid cells 18×18 — one frame serves both) |
| Stitch divider | (0,48) 64×4 | horizontally tiled |
| Scrollbar strap (leather) | (88,0) 6×24 | vertical 3-slice, 6px end caps |
| Small button | (96,0) 16×16 | nine-slice, 5px insets |
| Small button, pressed | (96,16) 16×16 | nine-slice, 5px insets |
| Search field frame | (0,32) 48×16 | nine-slice, 5px insets |

Unlisted atlas space is reserved for future regions; additions must not move existing ones.

### Texture generation

`tools/texgen/generate.py` (Python 3 + Pillow) procedurally paints every sheet — layered
value noise for parchment fiber, darkened deckled-edge alpha, slightly wobbly ink strokes,
leather/brass shading — using a **fixed seed** so output is deterministic, and writes the
PNGs directly into the assets directory. **Both the script and the generated PNGs are
committed.** No Gradle/build changes (the JDK-17 ForgeGradle build stays untouched); the
game loads plain resources.

Rejected alternatives: build-time generation (fragile build, not hand-editable),
runtime dynamic textures (most complexity, no editable files). Claude Design / DesignSync
was evaluated and is not applicable — it manages HTML/CSS design-system projects, not
game-ready PNG assets.

## 5. Code changes

One new class: `com.lwos.ui.theme.JournalTheme` (Minecraft-bound, client-side).

- All `ResourceLocation`s for the journal sheets (single source of truth — makes
  missing-texture checkerboards structurally unlikely).
- Sprite-coordinate constants matching the asset contract table.
- Color constants (`INK`, `INK_FADED`, `WAX`, …) replacing scattered hex literals.
- `blitNineSlice(GuiGraphics, ...)` and small blit helpers for 3-slice/tiled regions.

All five surface/widget files change **only in their draw code**: fills and hex colors are
replaced with `JournalTheme` blits and color constants. Specifically untouched:

- `PathStylePanelInput` hit-testing and the frame-published layout records
  (`currentLayout()` geometry may shift a few pixels for texture insets; the input handler
  reads the published rects, so it self-adjusts).
- `PathStylePanelState`, `PathStyleEdits`, `StyleManager`, keybinds, scissor scrolling.
- Everything in the pure core (`plan`, `config`, `geometry`, `organic`).

## 6. Invariants & error handling

- **Purity boundary:** all changes live in `ui`/`client`; no new imports in the pure core.
- **Determinism / preview==apply:** zero risk — no plan, style, or packet changes.
- Missing texture → Minecraft's purple/black checkerboard; centralizing paths in
  `JournalTheme` plus the manual checklist below is the mitigation.

## 7. Testing

- Existing pure-core test suite must stay green (nothing in scope touches it).
- Texgen determinism: run the generator twice, assert byte-identical output (checked in the
  script's own `--verify` mode or a trivial CI-less shell check).
- Manual `runClient` checklist:
  1. Panel: open/close, scroll, Ctrl-to-edit slider drag and chip clicks at GUI scales 1–4.
  2. Block picker: open from a slot, search, assign; hover/selection states.
  3. Tool wheel: open, select each wedge.
  4. Mode HUD: renders on plate, updates on mode change.
  5. One full commit → server apply round trip to confirm behavior is unchanged.
