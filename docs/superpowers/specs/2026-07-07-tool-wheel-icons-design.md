# Tool Wheel Icons — Design Spec

**Date:** 2026-07-07 · **Status:** approved · **Follows up:** `2026-07-07-journal-ui-reskin-design.md` (playtest finding)

## Problem

Post-reskin playtest found two defects in `ToolWheelOverlay`:

1. A leftover dim rectangle (`g.fill(..., 0x66000000)`) draws a black box behind the parchment disc.
2. Text labels are *centered* at radius 60, but the disc's torn edge sits at radius ~62 — half of every label (worst: "Terrain Blend", ~70px) hangs off the parchment onto the raw world background.

## Decision

Replace the five text labels with hand-drawn **ink pictogram icons** on the disc; show the **selected tool's name in wax red at the disc center**; mark the selected icon with a **wax scribble ring**; **delete the dim rectangle** entirely (the 240-alpha parchment provides its own contrast). The "Scroll to change tool" hint is dropped — it has no parchment to sit on and the interaction is established.

Rejected alternatives: vanilla item icons via `renderItem` (colorful 3D sprites clash with ink-on-parchment; Terrain Blend has no natural item); enlarging the disc to fit text (fragile against future long names, more art churn).

## Asset contract (additions to `widgets.png`, 128×128)

New regions only — **existing regions must not move** (per the reskin asset contract). All generated deterministically by `tools/texgen/generate.py`; `--verify` must pass.

| Region | Pos | Size | Content |
|---|---|---|---|
| icon: Path | (0,64) | 16×16 | wavy dotted trail, ink |
| icon: Line | (16,64) | 16×16 | straight rule with end ticks, ink |
| icon: Circle | (32,64) | 16×16 | compass circle (small center prick), ink |
| icon: Fill | (48,64) | 16×16 | bucket with pour, ink |
| icon: Terrain Blend | (64,64) | 16×16 | two hills with blend hatching, ink |
| wax scribble ring | (80,64) | 24×24 | hand-wobbled wax-red circling, transparent center |

Icon regions are indexed by `ToolType.ordinal()`: icon *i* at `(TOOL_ICON_U + i * TOOL_ICON_SIZE, TOOL_ICON_V)`. Adding a tool = appending an icon cell (row has room for 5; overflow starts a new row at (0,80) — cells (0,80)…(64,80) stay clear of the 24×24 ring region at x≥80).

## Layout (`ToolWheelOverlay.render`)

- Disc blit unchanged: 128×128, 1:1, centered.
- Icons: centers at radius **40** (fully inside the compass ring at r=53), first tool at top, clockwise — same angle math as today. Blit at `(x−8, y−8)`.
- Selected marker: scribble ring blitted centered behind the selected icon at `(x−12, y−12)`.
- Selected name: `JournalTheme.WAX`, no shadow, horizontally centered at `cx`, top at `cy+6` (just below the compass diamond; every tool name is narrower than the ring diameter).
- Deleted: the `0x66000000` dim fill and the hint string.

## Out of scope / unchanged

Selection & scroll logic, `ToolType` (its `color()` simply goes unused by the wheel), all other UI surfaces, in-world rendering, the pure core. No new `JournalTheme` blit helpers needed (`blitRegion` covers everything).

## Verification

1. `py -3 tools/texgen/generate.py` then `--verify` → all sheets OK.
2. `./gradlew build` green (JDK 17).
3. In-game: no black box; five ink icons inside the ring at every GUI scale; wax ring + centered wax name track Alt+scroll selection; nothing renders outside the torn disc edge.
