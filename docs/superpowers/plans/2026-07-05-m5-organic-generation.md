# Milestone 5 — Organic Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform mathematically perfect, uniform paths into organic terrain that looks hand-built by a skilled builder. This milestone introduces the core value of the mod: using low-frequency noise to drive clustered block placements and irregular edges. This requires the `EdgeShaper`, `GradientEngine`, and `BlendEngine`, alongside exposed tunables for rapid iteration.

**Architecture:** 
- `organic/noise/`: Implement self-contained, pure-Java Perlin and Cellular/Worley noise functions to guarantee determinism.
- `organic/EdgeShaper`: Erode, grow, and blur the boolean `PathMask` using noise fields to create wandering, irregular boundaries.
- `organic/GradientEngine`: Map weighted palettes to noise thresholds to assign blocks in clusters (e.g., runs of coarse dirt) rather than uniform static.
- `organic/BlendEngine`: Feather the outer edge of the modified area into the surrounding terrain over an N-block skirt.
- `plan/`: Update `EditPlanBuilder` to execute these new pure stages in sequence.
- `config/`: Implement JSON-driven tunables for immediate reloading and testing of noise scales and palette weights.

**Tech Stack:** Java 17, MinecraftForge 1.20.1 (Forge 47.4.10), JUnit 5. 

## Global Constraints

- **Clustered Randomness:** Material choices and edge shapes must be driven by low-frequency noise. Standard `random()` is only a tie-breaker.
- **Determinism Guarantee:** Nudging a parameter or dragging a point must yield the exact same blocks for a given operation seed. No wall-clock or unseeded `Random` reads are permitted.
- **Compute/Apply Separation:** The `organic/` package must remain 100% independent of Minecraft rendering and world classes, verifiable via headless unit tests.

---

### Task 1: Pure Noise Primitives || COMPLETE

### I chose an object-per-seed API (new PerlinNoise(seed).noise(x,y)) rather than a static noise(x,y,seed) function — cleaner and it builds the permutation table once. The plan's (x, y, seed) intent is preserved (seed in constructor).

Implement self-contained noise functions so the compute layer doesn't depend on unseeded or opaque Minecraft RNG.

**Files:**
- Create: `src/main/java/com/lwos/organic/noise/PerlinNoise.java`
- Create: `src/main/java/com/lwos/organic/noise/CellularNoise.java`
- Create: `src/test/java/com/lwos/organic/noise/NoiseTest.java`

**Interfaces:**
- Functions taking `(x, y, seed)` and returning a deterministic `[-1.0, 1.0]` or `[0.0, 1.0]` float.
- Support for multiple octaves and frequency/amplitude scaling.

- [x] **Step 1: Implement PerlinNoise** with deterministic seeding. _DONE — object-based API: `new PerlinNoise(long seed)` then `noise(x,y)` in [-1,1] (0 at integer lattice) and `fractal(x,y,octaves,persistence,lacunarity)`. Seeds a permutation table via splitmix64 (no `java.util.Random`)._
- [x] **Step 2: Implement CellularNoise (Worley)** for clumped patch generation. _DONE — `new CellularNoise(long seed)` then `f1(x,y)` (nearest-feature distance, >=0) and `cellValue(x,y)` ([0,1), constant within a cell → drives clustered patches). One feature point per cell, 3×3 neighbour search._
- [x] **Step 3: Write headless tests** proving identical coordinates and seeds yield identical float values, and different seeds diverge. _DONE — `NoiseTest` (11 tests): determinism, seed divergence, range, Perlin zero-at-lattice + smoothness, and a clumping assertion (>60% of neighbours share a cellValue)._
- [x] **Step 4: Commit** with message "feat(organic): implement pure deterministic noise primitives". _DONE._

---

### Task 2: The `EdgeShaper`

Transform the geometrically perfect `PathMask` into an organic, irregular occupancy field.

**Files:**
- Create: `src/main/java/com/lwos/organic/EdgeShaper.java`
- Create: `src/test/java/com/lwos/organic/EdgeShaperTest.java`

**Interfaces:**
- Takes a `PathMask` (distance field) and an operation seed.
- Uses low-frequency Perlin noise to add/subtract from the edge distance.
- Outputs a modified `PathMask` where the boundary wanders naturally.

- [x] **Step 1: Implement EdgeShaper logic** to apply noise offsets to the distance field. _DONE — `EdgeShaper.shape(PathMask, long seed)` perturbs the signed distance via low-freq Perlin fractal; added `PathMask.edgeDistances()` + `PathMask.of(map)`. Outward bulge bounded by EDGE_HALO (pre-approved)._
- [x] **Step 2: Write headless golden-image/matrix tests** printing a 2D ascii grid to prove the edge goes from straight to jagged/wavy. _DONE — EdgeShaperTest asserts boundary change, per-row min-x variance, determinism, seed divergence._
- [x] **Step 3: Commit** with message "feat(organic): implement EdgeShaper for irregular path boundaries". _DONE (08030b9)._

---

### Task 3: The `GradientEngine` & Palettes

Replace single-block assignments with a clustered, weighted palette system. 

**Files:**
- Create: `src/main/java/com/lwos/organic/Palette.java`
- Create: `src/main/java/com/lwos/organic/GradientEngine.java`
- Modify: `src/test/java/com/lwos/organic/GradientEngineTest.java`

**Interfaces:**
- `Palette` holds tuples of `(BlockStateRef, weight, noiseScale, clusterSize)`.
- `GradientEngine` assigns a `BlockStateRef` to a given `(x, y, z)` by evaluating the palette's noise fields and weights.

- [x] **Step 1: Define the Palette class** supporting multiple block entries with individual noise properties. _DONE — immutable `Palette(List<Entry>)`, `Entry(BlockStateRef, weight, noiseScale, clusterSize)`._
- [x] **Step 2: Implement GradientEngine** to sample Cellular/Perlin noise and pick the block with the highest local score based on weights. _DONE — `GradientEngine(seed, palette).blockAt(x,y,z)`; clusterSize=Cellular patch size, noiseScale=Perlin jitter, weight biases area; per-entry sub-seeds._
- [x] **Step 3: Write headless tests** asserting that a large area produces clustered patches, not uniform static. _DONE — GradientEngineTest: >0.6 same-neighbour clustering, weight-bias majority, determinism, seed divergence, both noise fields load-bearing._
- [x] **Step 4: Commit** with message "feat(organic): implement GradientEngine and clustered palettes". _DONE (60e20e8)._

---

### Task 4: The `BlendEngine`

Ensure the transition from the drawn path to the original terrain is soft and feathered.

**Files:**
- Create: `src/main/java/com/lwos/organic/BlendEngine.java`

**Interfaces:**
- Takes the `PathMask` distance field.
- At the outer N-block skirt (where distance to edge approaches 0), smoothly increase the probability of leaving the original terrain block intact.
- Uses noise to make the feathering irregular.

- [x] **Step 1: Implement BlendEngine** to drop out blocks randomly (driven by noise) near the edge limits. _DONE — `BlendEngine(seed, skirtWidth)`; smoothstep keep-probability that is 1.0 deep inside (dist<=-N) and falls to 0 at the edge, resolved against a seeded noise coin. `keepsPathBlock(mask,x,z)` / `feather(mask)`._
- [x] **Step 2: Write headless tests** confirming a solid path correctly degrades into the background block state at the extremities. _DONE — BlendEngineTest: interior all-kept, keep-fraction drops toward rim, irregular (noise crossover) not a clean ring, determinism, seed divergence._
- [x] **Step 3: Commit** with message "feat(organic): implement BlendEngine for feathered transitions". _DONE (defafb5)._

---

### Task 5: Pipeline Integration and Live Tunables

Wire the `organic/` modules into the `EditPlanBuilder` and expose configuration so noise scales can be tuned live.

**Files:**
- Modify: `src/main/java/com/lwos/plan/EditPlanBuilder.java`
- Create: `src/main/java/com/lwos/config/OrganicTunables.java`
- Modify: `src/main/java/com/lwos/tool/ToolManager.java` (or config watcher)

**Interfaces:**
- `OrganicTunables`: A JSON-backed config class tracking `edgeErosionFactor`, `defaultClusterSize`, etc.
- `EditPlanBuilder`: Add `EdgeShaper`, `GradientEngine`, and `BlendEngine` to the sequence between geometry generation and the final `EditPlan`.
- Enable a command or hotkey to reload tunables and trigger a debounce preview re-render.

- [x] **Step 1: Update EditPlanBuilder** to thread data through the three new organic stages. _DONE — `build(...,OrganicTunables)` overload: PathMask.build → EdgeShaper.shape → per inside column BlendEngine gate → GradientEngine material. Operation seed = explicit splitmix hash of control points (preview==apply, no packet change); distinct per-stage sub-seeds; builder stays pure (tunables passed in). `neutral()` reproduces pre-M5 geometric output._
- [x] **Step 2: Implement OrganicTunables** and hot-reload mechanism. _DONE — immutable snapshot in `com.lwos.config`, Gson JSON load, `current()`/`reload()`; reload keybind (R) in the client input layer (kept out of pure ToolManager); bad edits rejected at reload (fromJson validates palette), prior snapshot retained._
- [ ] **Step 3: Manual in-game verification** (Draw a path, verify the edge is irregular and the materials cluster. Edit the JSON tunables, hit reload, and watch the preview update immediately). _DEFERRED TO USER — Forge glue is compile-only (`./gradlew build` green); see handoff below._
- [x] **Step 4: Commit** with message "feat(plan): integrate organic generation stages into pipeline". _DONE (2204996; final-review fixes in 2cec60c)._

---

## Definition of Done (Milestone 5)
- The preview mesh reveals paths that wander, bulge, and pinch organically rather than maintaining a perfect mathematical width.
- The path material consists of clustered patches (e.g., runs of coarse dirt next to runs of path block), driven by noise fields, avoiding a uniformly random "static" look.
- Edges feather naturally into the surrounding terrain.
- Determinism is perfectly maintained (reloading or nudging a point with the same tunables yields the same random seed layout).
- A developer/builder can tweak noise settings in a JSON file and immediately see the preview mesh recompute in-game without restarting.
