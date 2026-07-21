# Shape playtest fixes + free-placement revision (2026-07-21)

First in-game playtest of the shape build modes (both 2026-07-20 checklists otherwise
green) surfaced two bugs, one design miss, and a feature request. All fixed/shipped in
this change set.

## 1. Cube crash: `IllegalArgumentException: CUBE needs 3 anchors, got 2`

**Symptom:** switching to the Cube tool and right-clicking once crashed the client
(render thread) on the next frame.

**Root cause:** `ShapeRenderer.onRenderLevel` builds the live preview from
`committed anchors + aim`. During cube's base-stretch phase that is 1 + 1 = 2 anchors,
but `ShapePlanBuilder.build` throws on any count != `mode.clickCount()` (3 for CUBE) —
an exception we throw ourselves, raised inside `RenderLevelStageEvent`, killing the game.
The 2026-07-20 plan only exercised two-click shapes in the preview path; the cube's
intermediate state was never previewed in a test.

**Fix:** the preview pads its anchor list to `clickCount` by repeating the live aim
(`ShapeRenderer`), so a mid-gesture cube previews as its base slab. The builder stays
strict — commits still validate exactly.

**Lesson:** any state machine whose *intermediate* states feed a renderer needs a test
per state, not per finished gesture. Also: pure-core exceptions crossing into render
events are client crashes; render-side callers of throwing pure APIs must be total.

## 2. Wall tool "stuck" to an axis

**Symptom:** wall gestures locked to X or Z seemingly at random.

**Root cause:** by design — the wall's construction plane normal was latched from the
dominant horizontal look axis at anchor time. Correct per spec, but it *plays* as
stuck: turning the camera after anchoring never changes the plane.

**Fix:** subsumed by the free-placement revision (below). The latched-plane model is
deleted; rectangle orientation now derives from the two clicked corners.

## 3. Grass punches holes in builds

**Symptom:** building over grassy fields left plant-shaped gaps.

**Root cause:** place mode planned only strictly-air cells (`minecraft:air` /
`cave_air` / `void_air`); grass, tall grass, flowers, and snow layers are real blocks,
so their cells were skipped.

**Fix:** `WorldView.isReplaceableAt(x,y,z)` (default: air-like; Forge/Server views
override with `BlockState.canBeReplaced()` via `SurfaceScan.isReplaceable`) and place
mode now fills every replaceable cell. Both views share the same implementation, so
preview == apply holds. Solid blocks are still respected — placements don't carve.

## 4. Free placement (feature): one Rect tool, corners anywhere

Request: "place wherever I want — corners on a ceiling or floor, a corner up top and
another further down makes a wall — don't limit tools per axis."

Shipped as a revision of the shape model:

- **WALL + FLOOR merged into RECT.** Corners are free 3D points;
  `ShapeGeometry.rectAuto` infers the plane: an agreeing axis wins, otherwise the
  smallest |delta| axis collapses to the first corner's value ("most planar" reading).
- **Free aiming** (`ShapeAim.freeAim`): stretch targets are real terrain hits wherever
  you look (floor, ceiling, wall), falling back to a fixed-distance air point along
  the view ray (eye→anchor distance). The old per-mode construction planes are gone.
- **Cube extrudes perpendicular to its base plane** (base on a wall extrudes
  horizontally): extrusion axis = `rectFixedAxis(a, b)`.
- **Circle orients to the clicked face** (click a wall → vertical circle): the face
  axis rides in `ShapeOptions.axis` (in `optionsJson`, so no packet change; lenient
  default Y keeps old payloads horizontal).
- **Sphere radius is true 3D distance.**

ToolType shrinks to 7 wheel sectors (RECT reuses the floor-diamond glyph; the wall
glyph is retired in the sheet). `ShapeMode` ordinals changed — client+server ship
together, same protocol version.
