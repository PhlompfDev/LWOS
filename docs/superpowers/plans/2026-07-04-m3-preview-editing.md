# Milestone 3 — Preview & Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the visual debugging outlines from M2 into a real **translucent block preview mesh**, introduce **width handles** to visually adjust the global path width, and add the **Confirm/Cancel workflow**. This is the first milestone where the mod places actual blocks in the world upon commit. (Point dragging and EditHistory/Undo are explicitly deferred to later milestones).

**Architecture:** 
- `plan/`: `PlannedChange` is updated to carry a pure `BlockStateRef` (a pure-data stand-in for block states). `EditPlanBuilder` assigns a default block (e.g., `"minecraft:dirt_path"`) for `ChangeKind.TERRAIN` changes.
- `preview/`: A new `PreviewRenderer` on the Forge client side builds a translucent block mesh from the `EditPlan` instead of wireframe boxes.
- `tool/`: `PathRenderer` adds interactive "width handles" to adjust the global path width via dragging, complementing the `[`/`]` keys.
- `apply/`: A new `PlacementEngine` on the Forge server side translates `GridPos` to `BlockPos` and applies blocks to the server level when the player confirms. 
- `apply/net/`: A new `EditRequestPacket` sends the path's control points and width from client to server, allowing the server to deterministically rebuild the identical `EditPlan` and apply it.

**Tech Stack:** Java 17, MinecraftForge 1.20.1 (Forge 47.4.10), JUnit 5. 

## Global Constraints

- **Loader/version:** MinecraftForge, Minecraft **1.20.1**, Forge **47.4.10**, Java **17**.
- **Group / base package:** `com.lwos`. **Mod id:** `lwos`.
- **Compute/apply boundary:** `plan/` remains pure and cannot import Minecraft types. Block states must be represented purely (e.g., as String IDs).
- **Determinism:** The server MUST rebuild the `EditPlan` using its own `ServerWorldView` instead of accepting a giant payload of blocks from the client.
- **Client-guarded:** UI, rendering, and keybinds are `Dist.CLIENT`.

---

### Task 1: `BlockStateRef` and Pure Block States in `plan/`

Update the pure `plan` package to include block state information so the apply and preview sides know what blocks to use.

**Files:**
- Create: `src/main/java/com/lwos/plan/BlockStateRef.java`
- Modify: `src/main/java/com/lwos/plan/PlannedChange.java`
- Modify: `src/main/java/com/lwos/plan/EditPlanBuilder.java`
- Modify: `src/test/java/com/lwos/plan/EditPlanTest.java`
- Modify: `src/test/java/com/lwos/plan/EditPlanBuilderTest.java`

**Interfaces:**
- Produces: `record BlockStateRef(String id)`.
- Updates `PlannedChange(GridPos pos, ChangeKind kind, BlockStateRef state)`.
- `EditPlanBuilder` should assign `new BlockStateRef("minecraft:dirt_path")` to the changes it generates.

- [ ] **Step 1: Write the failing tests** (Update existing tests to expect the new parameter).
- [ ] **Step 2: Run tests to verify they fail**.
- [ ] **Step 3: Write minimal implementation**.
- [ ] **Step 4: Run tests to verify they pass**.
- [ ] **Step 5: Commit** with message "feat(plan): add BlockStateRef to PlannedChange".

---

### Task 2: `PreviewRenderer` Translucent Block Mesh

Replace the M2 blue debug outlines with an actual batched translucent mesh of blocks.

**Files:**
- Create: `src/main/java/com/lwos/client/PreviewRenderer.java`
- Modify: `src/main/java/com/lwos/client/PathRenderer.java` (remove blue boxes, delegate to `PreviewRenderer`)

**Interfaces:**
- `PreviewRenderer.render(EditPlan plan, PoseStack ps, MultiBufferSource buffers)`
- Iterates over `PlannedChange`, maps pure `BlockStateRef` to a Forge `BlockState` (using `ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ref.id()))`), and renders it using `Minecraft.getInstance().getBlockRenderer()` into a translucent buffer.

- [ ] **Step 1: Write the PreviewRenderer implementation**.
- [ ] **Step 2: Integrate with PathRenderer** (remove the AABB debug box rendering and call `PreviewRenderer`).
- [ ] **Step 3: Manual in-game verification** (Place points, expect to see translucent dirt path blocks hugging the terrain).
- [ ] **Step 4: Commit** with message "feat(preview): render translucent block mesh for EditPlan".

---

### Task 3: Visual Width Handles

Add width handles to visually adjust the global path width, augmenting the `[`/`]` keys.

**Files:**
- Modify: `src/main/java/com/lwos/client/PathRenderer.java` (render width handle gizmos)
- Modify: `src/main/java/com/lwos/tool/PathTool.java` (handle dragging the handle)
- Modify: `src/main/java/com/lwos/client/LwosInputHandler.java` (raycast to handles)

**Interfaces:**
- Render a draggable gizmo (e.g., a colored box) on the left and right edges of the `PathRibbon` at the midpoint of the path.
- Clicking and dragging these gizmos horizontally updates the global `PathTool.setWidth()`.

- [ ] **Step 1: Add handle rendering** to `PathRenderer`.
- [ ] **Step 2: Implement interaction logic** in `LwosInputHandler` and `PathTool` (detecting mouse down on the handle and adjusting width based on mouse movement).
- [ ] **Step 3: Manual in-game verification** (Drag the handle and see the width update live).
- [ ] **Step 4: Commit** with message "feat(tool): interactive width handles for global path width".

---

### Task 4: ServerWorldView & Networking Seam

Implement the network packet to send the user's intent to the server, and a `ServerWorldView` so the server can deterministically rebuild the plan.

**Files:**
- Create: `src/main/java/com/lwos/apply/ServerWorldView.java`
- Create: `src/main/java/com/lwos/apply/net/EditRequestPacket.java`
- Modify: `src/main/java/com/lwos/LwosMod.java` (register packet channel)

**Interfaces:**
- `ServerWorldView` implements `WorldView` using a `ServerLevel`.
- `EditRequestPacket` contains `List<Vec3d> controlPoints` and `double width`.

- [ ] **Step 1: Write ServerWorldView**.
- [ ] **Step 2: Write EditRequestPacket** with encode/decode methods.
- [ ] **Step 3: Register packet channel** using Forge `SimpleChannel`.
- [ ] **Step 4: Commit** with message "feat(apply): add ServerWorldView and EditRequestPacket".

---

### Task 5: PlacementEngine & Confirm/Cancel Workflow

Introduce `Enter` to commit the plan (placing the blocks) and `Esc` to cancel it.

**Files:**
- Create: `src/main/java/com/lwos/apply/PlacementEngine.java`
- Modify: `src/main/java/com/lwos/apply/net/EditRequestPacket.java` (handle method)
- Modify: `src/main/java/com/lwos/client/LwosKeyMappings.java` (Add COMMIT keybind, default Enter)
- Modify: `src/main/java/com/lwos/client/LwosClientModEvents.java`
- Modify: `src/main/java/com/lwos/client/LwosInputHandler.java`

**Interfaces:**
- `PlacementEngine.apply(ServerLevel level, EditPlan plan)` maps `GridPos` to `BlockPos` and pure `BlockStateRef` to Forge `BlockState`, calling `level.setBlock(...)`.
- The packet handle method runs `EditPlanBuilder.build` using `ServerWorldView` and passes the result to `PlacementEngine.apply`.
- The commit key sends the `EditRequestPacket` to the server and clears the tool.
- The cancel key (already mapped) just clears the tool.

- [ ] **Step 1: Create PlacementEngine**.
- [ ] **Step 2: Handle packet on server** (rebuild plan and apply).
- [ ] **Step 3: Register COMMIT keybind** and wire in `LwosInputHandler` to send packet.
- [ ] **Step 4: Manual in-game verification** (Hit Enter -> blocks are permanently placed; Esc -> preview disappears without placing).
- [ ] **Step 5: Commit** with message "feat(apply): implement PlacementEngine and confirm/cancel workflow".

---

## Definition of Done (Milestone 3)
- `PlannedChange` pure record stores a `BlockStateRef`.
- `PreviewRenderer` draws a translucent block mesh of the `EditPlan` rather than just debug lines.
- Players can adjust the global path width visually using draggable width handles.
- Pressing `Enter` commits the path, sending a packet to the server. The server reconstructs the `EditPlan` and runs `PlacementEngine` to place real blocks in the world.
- Pressing `Esc` cancels the path session without changing blocks.
