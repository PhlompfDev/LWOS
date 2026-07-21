# Radial Wheel Redesign + UI Tweens — Design Spec (sub-projects B+C)

Date: 2026-07-20. Status: approved (user delegated approval; B and C merged into one
spec because both rework the same overlay surfaces).

Follows the shape-build-modes spec (sub-project A). Goal: an EB-quality radial wheel —
cursor-driven sector picking, redesigned icon set, spring-tweened highlighting — plus
spring glide on the style panel sliders. Motion built on `client/anim/Spring` (house
feel ζ=0.8 / 3 Hz; sliders use a snappier ζ=0.9 / 6 Hz).

## 1. Wheel interaction (B)

- Alt-held wheel stays an `IGuiOverlay` (no Screen — the world must stay live).
- **While the wheel is visible the OS cursor is released** (the `PathStylePanelInput`
  Ctrl-edit pattern): move the mouse to hover a sector, click to select, or just
  **release Alt to select the hovered tool** (EB's release-commits gesture).
  Alt+scroll cycling still works (fallback and muscle memory).
- Hover picking: cursor angle from screen center via `atan2`, 8 equal sectors, first
  tool at top; dead zone (distance < 12 gui-px) and far zone (> 90) hover nothing.
- `ToolManager.select(ToolType)` added (direct selection; clears the shape gesture
  like `cycle` does).

## 2. Wheel visuals & motion (B+C)

- Parchment disc + compass art unchanged (tool_wheel.png).
- **Open animation**: disc + contents scale 0.85 → 1.0 with the house spring,
  alpha fades in with it. Retriggers on every open.
- **Hovered icon** spring-scales to 1.25 (per-icon springs, target 1.0 when not
  hovered); hovered tool's name shows at disc center in ink.
- **Selected tool** keeps the wax scribble ring; the ring's angular position is a
  spring — on selection change it sweeps around the disc to the new sector
  (shortest arc) instead of teleporting. Selected-and-no-hover shows the selected
  name in wax at center.
- HUD/hit-safety: overlay early-outs exactly as before (screen open, tool disabled).

## 3. Icon set v2 (B)

- All 8 tool glyphs redrawn as **pixel maps** (16×16, journal ink tones: full ink,
  mid ink, faint ink, wax accent) — designed at 16× scale in a visual iteration loop,
  then baked verbatim into texgen.
  *(Process note: the user asked for Claude Design here; its consent gate couldn't be
  granted in this autonomous session, so the same design-visually-then-bake flow ran
  through a local Pillow preview loop instead. The pixel-map format makes a future
  Claude Design pass trivial to port.)*
- texgen: a `draw_pixmap` helper + row 3 of widgets.png (v=96, u=index·16, 8 slots)
  drawn from the maps, **appended after every existing region** (RNG append-only rule;
  the maps use no rng). Rows 1–2 (old icons) stay byte-identical, now unused.
- `ToolType.iconIndex()` renumbered 0..7 in enum order; `JournalTheme.blitToolIcon`
  reads row 3 only.

## 4. Slider glide (C)

- `SliderWidget` knob/fill drawn at a spring-followed position: springs persist in a
  static id-keyed registry (`SliderWidget.springFor(id)`), since widget instances are
  rebuilt per frame; `render(g, id)` overload opts a call site in. `PathStylePanel`
  passes stable ids (section + row). Value logic untouched — the spring is
  presentation-only; hit-testing and `setFromMouse` still use exact pixels.
- Effect: preset switches and Ctrl-drags glide with a slight settle instead of
  snapping.

## 5. Testing

- Pure: ToolManager.select behavior (clears gesture, sets selection); sector-from-angle
  math extracted pure (`WheelMath.sectorAt(dx, dy, count)` + dead zone) and unit-tested;
  spring registry identity test.
- texgen `--verify` byte-identity for all previously committed art; only widgets.png
  changes.
- Build gate + manual playtest checklist (wheel hover/click/release-select at GUI
  scales 1–4, ring sweep, slider glide).
