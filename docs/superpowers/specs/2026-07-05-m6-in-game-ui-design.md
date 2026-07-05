# Milestone 6 — In-Game Path Style UI · Design

> Status: approved design (brainstorm output). The task-by-task implementation plan is
> derived from this document via the `writing-plans` step.

**Goal:** Give builders an intuitive, premium in-game UI to adjust path styles live — swapping
palettes (e.g. packed mud with mud-brick variants, blending into coarse-dirt and moss edges) and
seeing the translucent preview update in real time — replacing manual JSON edits.

**Tech stack:** Java 17, MinecraftForge 1.20.1 (Forge 47.4.10), Minecraft client GUI framework.

---

## 1. Decisions locked during brainstorming

| Area | Decision |
|------|----------|
| Interaction model | Docked side panel; **world stays live** (not a modal Screen). |
| Cursor / editing | **Hold Left-Ctrl** to free the cursor and operate the panel; release returns to building. (Alt is reserved for the tool wheel.) |
| Block picking | **Both**: aim-and-pick from world (`P`), and a mini creative **search grid** modal. |
| Preview timing | **Continuous with a safety debounce** (~75 ms rebuild throttle). |
| Control depth | **Curated core + always-visible Advanced section** in one scrollable panel. |
| Presets | **First-class**: a top preset-chip bar with load + Save. |
| Open gating | **Anytime in builder mode** (pre-configure a style before drawing). |
| Aesthetic | **Frosted glass** — translucent blurred dark panel, thin hairline dividers, cool blue accent. |
| Edge palette | **Edge-material shoulder that fades to terrain** (new generation capability). |

---

## 2. Architecture overview

```
StyleManager (active PathStyle + version + presets on disk)
        │  read each frame                    │ carried on commit
        ▼                                       ▼
PathRenderer ──build(PathStyle)──► EditPlan ──► PreviewRenderer (translucent mesh)
   ▲  (cached, debounced)                        EditRequestPacket(style) ──► server rebuild ──► PlacementEngine
   │
PathStylePanel (IGuiOverlay, frosted glass)  ◄── mutates ── PathStylePanelInput (Ctrl-hold, P, C)
   └─ SliderWidget / BlockSlotWidget / BlockSearchScreen
```

**Purity boundary is preserved:** the UI layer (`ui/`) only mutates a `PathStyle` on `StyleManager`
and bumps a version counter. Pure computation (`organic/`, `plan/`) never imports `ui/`;
`EditPlanBuilder.build` takes a `PathStyle` as a parameter and remains deterministic.

---

## 3. Data model

### `com.lwos.config.PathStyle` (immutable, Minecraft-free)
- **Core palette:** `List<Entry>` where `Entry = {id, weight, clusterSize, noiseScale}`.
- **Edge palette (Outskirts):** `List<Entry>`, same shape.
- **Global params:** `blendSkirtWidth`, `edgeErosionFactor`, `edgeNoiseScale`, `defaultClusterSize`.
- **Behavior:** Gson-serializable; `toCorePalette()` / `toEdgePalette()` produce `organic.Palette`s;
  `defaults()` and `neutral()` factories; value-based `equals`/`hashCode` for cheap change detection.
- **Supersedes `OrganicTunables`.** The old flat tunables type and its `config/lwos-organic.json`
  file are removed. M5 tests that referenced `OrganicTunables` are migrated onto
  `PathStyle.neutral()` / `defaults()`, which reproduce the same plan output so assertions still hold.

### `com.lwos.config.StyleManager` (client-side, mutable holder + IO)
- Holds the **active working style** and a monotonic **`version`** counter, incremented on every
  mutation. The preview cache and the panel read this.
- **Presets:** loads/saves named styles as JSON under `config/lwos/styles/<name>.json`; ships
  built-in presets (e.g. *Muddy Trail*, *Gravel Road*). Auto-persists the working style between
  sessions.
- All file IO and mutable global state live here, keeping `EditPlanBuilder` pure (mirrors the old
  `OrganicTunables.current()`/`reload()` split).

---

## 4. Generation change — the "Outskirts" shoulder (new capability)

Today `BlendEngine` only decides *keep the path block* vs. *drop back to original terrain* in the
skirt; there is no edge-block placement. Milestone 6 adds an **edge-material shoulder**:

- The path footprint gains an outer **edge band** of width `blendSkirtWidth`, covering the
  core-skirt columns dropped by the core feather **plus** a halo just outside the path
  (`edgeDistance ∈ (0, blendSkirtWidth]`, available from `PathMask`'s edge-distance halo).
- Band columns are assigned an **edge-palette** block via a `GradientEngine` over the edge palette,
  so coarse dirt / moss cluster like the core rather than reading as noise.
- A smoothstep **keep-probability** (high next to the path, → 0 at the outer rim) dithers the
  shoulder out to bare terrain, giving a soft halo that melts into the ground.
- **Determinism preserved:** seeded off the same operation seed with a new distinct salt, so
  preview == apply. Applies to `FOLLOW_SURFACE`; `CUT_AND_FILL` keeps its existing no-feather
  behavior (dropping cut columns would leave floating lips).

`EditPlanBuilder.build` is updated to consume `PathStyle`, thread the edge palette + shoulder stage,
and emit the extra edge-band changes.

---

## 5. UI layer (`ui/`)

All widgets are **custom-rendered** for the frosted-glass look (translucent dark fill via `g.fill`
with alpha, hairline dividers, blue accent); no vanilla button textures.

- **`ui/components/SliderWidget`** — custom track/fill/knob + value label; click-to-set and drag;
  value↔pixel mapping is a pure, unit-testable helper.
- **`ui/components/BlockSlotWidget`** — renders the 3D block item icon; shows the active-slot
  highlight; click selects the slot (target of pick-from-world and the search modal).
- **`ui/PathStylePanel`** (`IGuiOverlay`) — docked right panel assembling the agreed layout:
  **preset chip bar** (load + `＋ Save`) → **Core Materials** → **Outskirts · Edge Blend**
  (blocks + a single *Blend amount* slider) → **Advanced** (cluster size, edge erosion, edge noise).
  Scrolls if it overflows the viewport height. Footer hint: *Hold Ctrl to edit · Look + P to pick*.
- **`ui/BlockSearchScreen`** — the mini creative picker: a real modal `Screen` with a search
  `EditBox` and a scrollable block-icon grid. Selecting a block assigns it to the active slot and
  returns to the panel.

### Input controller — `ui/PathStylePanelInput` (Forge client events)
- **`C`** toggles panel visibility (only while builder mode is enabled).
- **Hold Left-Ctrl** → `mc.mouseHandler.releaseMouse()` and route mouse position + clicks/drags to
  the panel's widget hit-testing; on release, `grabMouse()` and return to building.
- **`P`** (while building, panel open) → raycast the looked-at block and assign its id to the active
  slot.
- Every mutation calls into `StyleManager` (updates the working style, bumps `version`).

---

## 6. Live preview, debounce & determinism

- **Preview cache:** `PathRenderer` no longer rebuilds the `EditPlan` every frame. It caches the
  last plan keyed by `(style version, path revision, width, mode)` and rebuilds only when the key
  changes **and** ≥ ~75 ms have elapsed — implementing "continuous with safety debounce" and cutting
  today's per-frame rebuild cost. (`PathTool` gains a small revision counter bumped on point/width
  edits.)
- **Commit determinism:** `EditRequestPacket` is extended to **carry the resolved `PathStyle`**
  (encode/decode via `FriendlyByteBuf`, with bounds validation matching the existing point/width
  guards). The server builds with that style instead of a JVM-local static, so the placed path
  matches the preview and the previous singleplayer-only limitation is removed.

---

## 7. Keybindings (`LwosKeyMappings`)

- **Add:** `TOGGLE_STYLE_PANEL` (`C`), `PICK_BLOCK` (`P`). The Left-Ctrl edit-hold is read directly
  via `InputConstants.isKeyDown` (like the existing Alt check), not a bindable mapping.
- **Remove:** `RELOAD_TUNABLES` (`R`) and its handler — superseded by the UI as the source of truth.

---

## 8. Testing

**Pure / headless JUnit:**
- `PathStyle` JSON serialize/deserialize round-trip (incl. missing-field defaults).
- `StyleManager` preset save/load and built-in preset loading.
- Edge-shoulder generation: determinism (same seed+style+points → identical plan) and fade behavior
  (density high near path, → 0 at rim).
- `EditRequestPacket` encode/decode round-trip including the carried style.
- **Preview == apply**: building with the same style/points/width/mode yields an equal `EditPlan`.
- `SliderWidget` value↔pixel math and `BlockSlotWidget` hit-testing.

**Playtest (`runClient`):** panel open/close, Ctrl-hold cursor handoff, pick-from-world, search
modal, preset load/save, and live preview updates while dragging sliders behind the panel.

---

## 9. Scope / non-goals

- **Single active style** (no per-path styles).
- **Dedicated-server config sync** remains out of scope; the packet-carried style covers
  singleplayer / integrated server / LAN commits.
- No animation or transition polish beyond the static frosted-glass look.

---

## 10. Definition of Done

- Open the Path Style panel anytime in builder mode; it docks right in frosted glass with the world
  live behind it.
- Distinct **Core Materials** and **Outskirts** sections, plus an always-visible **Advanced**
  section.
- Blocks selected by aim-and-pick (`P`) or the search grid; weights and cluster sizes adjusted with
  styled sliders.
- Any change updates the 3D translucent preview in the background within ~75 ms.
- The edge shoulder places coarse-dirt/moss blending that fades into terrain.
- Styles can be saved as named presets and reloaded; the committed path matches the preview.
