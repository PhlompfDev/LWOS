# Milestone 4 — Terrain Modification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transition from placing blocks in mid-air or clipping through terrain to intelligently modifying the terrain surface. The mod will preserve terrain shape by replacing top blocks by default, introduce an opt-in cut/fill grading mode for flattening, and handle basic slopes by generating stairs automatically. 

**Architecture:** 
- `plan/`: Introduce `TerrainMode` (e.g., `FOLLOW_SURFACE`, `CUT_AND_FILL`). Update `EditPlanBuilder` to calculate Y-coordinates based on the `WorldView` surface height, replacing top blocks or issuing fill/remove commands based on the mode.
- `plan/`: Update `BlockStateRef` to support block state properties (like `facing` or `half`) to support stairs.
- `geometry/`: Leverage `TerrainSampler` to understand the surface height and slope gradient under the path.
- `apply/`: Update `PlacementEngine` to apply block state properties and handle `REMOVE` commands by placing air or restoring original blocks on undo.
- `client/`: Add UI indicators for the current `TerrainMode` and ensure `PreviewRenderer` visualizes `REMOVE` actions (e.g., red translucent outlines for blocks to be carved).

**Tech Stack:** Java 17, MinecraftForge 1.20.1 (Forge 47.4.10), JUnit 5. 

## Global Constraints

- **Preserve Terrain Default:** The tool must follow the surface and replace top blocks without levelling hills, unless explicitly requested.
- **Compute/apply boundary:** `plan/` remains pure. Block states and properties must be represented purely (e.g., `Map<String, String>` for properties) without importing `net.minecraft`.
- **Determinism:** The `EditPlan` generation must remain perfectly deterministic based on inputs, seed, and `WorldView`.

---

### Task 1: `BlockStateRef` Properties and `TerrainMode` Enum

Enhance the pure block representation to support properties (essential for stairs) and define the terrain modification modes.

**Files:**
- Modify: `src/main/java/com/lwos/plan/BlockStateRef.java`
- Create: `src/main/java/com/lwos/plan/TerrainMode.java`
- Modify: `src/main/java/com/lwos/apply/PlacementEngine.java`

**Interfaces:**
- `BlockStateRef` adds a `Map<String, String> properties` (e.g., `{"facing": "east", "half": "bottom"}`).
- `TerrainMode` enum with `FOLLOW_SURFACE` and `CUT_AND_FILL`.
- `PlacementEngine` translates string properties into Forge `Property<?>` values when resolving the `BlockState`.

- [ ] **Step 1: Update BlockStateRef** to include a properties map and write unit tests.
- [ ] **Step 2: Update PlacementEngine** to parse and apply these properties to the Forge `BlockState`.
- [ ] **Step 3: Create TerrainMode Enum**.
- [ ] **Step 4: Commit** with message "feat(plan): support block state properties and add TerrainMode".

---

### Task 2: Terrain-Following Surface Replacement (Default Mode)

Update `EditPlanBuilder` to use `WorldView` to place blocks along the natural surface rather than a flat Y-plane.

**Files:**
- Modify: `src/main/java/com/lwos/plan/EditPlanBuilder.java`
- Modify: `src/test/java/com/lwos/plan/EditPlanBuilderTest.java`

**Interfaces:**
- When in `FOLLOW_SURFACE` mode, for each `(x, z)` in the `PathMask`, read the highest solid block Y from `WorldView`.
- Generate a `PLACE` change at that surface `(x, y, z)` instead of the path's interpolated Y.
- Ensure only top surface blocks (like grass/dirt) are replaced.

- [ ] **Step 1: Write headless tests** asserting that `EditPlanBuilder` follows the mock `WorldView` heightmap.
- [ ] **Step 2: Implement surface-following logic** in `EditPlanBuilder`.
- [ ] **Step 3: Manual in-game verification** (Draw a path over a hill; it should drape over the hill and replace the grass blocks).
- [ ] **Step 4: Commit** with message "feat(plan): implement terrain-following surface replacement".

---

### Task 3: Opt-in Cut/Fill Grading

Implement the `CUT_AND_FILL` mode where the path enforces its own Y-level, carving through hills and building bridges over dips.

**Files:**
- Modify: `src/main/java/com/lwos/plan/EditPlanBuilder.java`
- Modify: `src/main/java/com/lwos/client/PreviewRenderer.java`

**Interfaces:**
- When in `CUT_AND_FILL` mode, use the path's interpolated Y.
- If surface Y > path Y: Issue `REMOVE` changes for blocks above the path.
- If surface Y < path Y: Issue `PLACE` changes for the path block, and fill blocks (e.g., dirt) below it down to the surface.
- `PreviewRenderer` should render `REMOVE` changes distinctively (e.g., translucent red blocks).

- [ ] **Step 1: Add logic for carving (REMOVE) and filling (PLACE)** to `EditPlanBuilder` when `CUT_AND_FILL` is active.
- [ ] **Step 2: Update PreviewRenderer** to visualize `REMOVE` changes in red.
- [ ] **Step 3: Test headlessly** to ensure air blocks are targeted for removal.
- [ ] **Step 4: Manual in-game verification** (Draw a path through a hill; it should preview a tunnel and fill gaps).
- [ ] **Step 5: Commit** with message "feat(plan): implement cut/fill grading mode".

---

### Task 4: Basic Slope Handling & Stairs

Detect steep gradients along the path and automatically place stairs instead of flat blocks to ensure navigability.

**Files:**
- Modify: `src/main/java/com/lwos/geometry/PathSampler.java` (calculate gradient)
- Modify: `src/main/java/com/lwos/plan/EditPlanBuilder.java` (or new `ProfileEngine`)

**Interfaces:**
- Calculate the `dy` over distance along the spline.
- If the slope is steep (e.g., > 0.5 blocks per horizontal block), swap the standard block for a stair block (e.g., `"minecraft:oak_stairs"`).
- Calculate the correct `facing` property based on the path's forward tangent vector.

- [ ] **Step 1: Calculate slope/gradient** at each path sample.
- [ ] **Step 2: Apply stair blocks** with correct facing properties when the slope exceeds the threshold.
- [ ] **Step 3: Write headless tests** asserting stairs are placed on steep inclines with correct facing.
- [ ] **Step 4: Manual in-game verification** (Draw a path up a steep hill; it should automatically generate stairs).
- [ ] **Step 5: Commit** with message "feat(plan): automatic stair placement on steep slopes".

---

### Task 5: UI Integration for Terrain Modes

Allow the user to toggle between `FOLLOW_SURFACE` and `CUT_AND_FILL`, and display the current mode on the HUD.

**Files:**
- Modify: `src/main/java/com/lwos/tool/PathTool.java`
- Modify: `src/main/java/com/lwos/client/LwosInputHandler.java`
- Modify: `src/main/java/com/lwos/client/ToolRenderer.java`
- Modify: `src/main/java/com/lwos/apply/net/EditRequestPacket.java` (include TerrainMode)

**Interfaces:**
- Add a keybind (e.g., `M`) to toggle `TerrainMode` in `PathTool`.
- Pass the selected mode in `EditRequestPacket` to the server.
- `ToolRenderer` displays "Mode: Surface" or "Mode: Cut & Fill" near the tool UI.

- [ ] **Step 1: Add keybind and toggle logic**.
- [ ] **Step 2: Update packet payload** to transmit the mode to the server.
- [ ] **Step 3: Update ToolRenderer** to display the active mode.
- [ ] **Step 4: Commit** with message "feat(tool): UI and keybinds for toggling TerrainMode".

---

## Definition of Done (Milestone 4)
- Path naturally drapes over the terrain, replacing top blocks (grass/dirt) rather than floating or clipping.
- Pressing a toggle key switches to Cut/Fill mode, which carves through hills (previewed in red) and builds foundations over dips.
- Paths drawn up steep hills automatically convert into correctly-facing stairs.
- All modifications correctly commit to the server using the designated `TerrainMode`.
- `plan/` remains purely isolated from Minecraft dependencies, correctly using property maps to describe block state variants.
