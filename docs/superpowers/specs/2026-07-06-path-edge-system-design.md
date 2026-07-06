# LWOS — Path Edge System Overhaul: Design Spec

- **Date:** 2026-07-06
- **Status:** Approved design — ready for implementation planning
- **Scope:** Rework the path builder's **edge system** so paths read as hand-built: controllable
  ruggedness, sparse-and-clustered edge-block scatter, and an outward meld into surrounding terrain.
  Variable width & taper is a deliberate follow-up spec, not part of this one.

---

## 1. Motivation

The path builder produces a solid, terrain-draped ribbon with an organic palette, but three edge
behaviours fight the builder and block the "hand-made" look:

1. **Rugged edges are unreachable.** Edge erosion is *width-relative and core-protected*:
   `erosionAmp = edgeRoughness × edgeBandWidth`, where `edgeBandWidth = (1 − coreProtect) × halfWidth`.
   On a normal width-3 path with default `coreProtect 0.4`, the maximum wobble is **~0.9 blocks** even
   at roughness 1.0. The edge physically cannot bite bays deeper than ~1 block, so "rugged" is
   impossible by construction. (`EditPlanBuilder.build`, `EditPlanBuilder.java:84-93`.)

2. **No control over edge-block density.** Edge-palette blocks appear only where `BlendEngine` dropped
   a core column, and the density is hardwired to a distance smoothstep (dense at the core, fading to
   the rim). There is no "spawn edge blocks here and there, ~20% coverage" knob.
   (`EdgeBandEngine.edgeBlockAt`, `EdgeBandEngine.java:37-50`.)

3. **The cluster knob does nothing perceptible.** The *patchiness* of edge scatter is decided by a
   **fixed** coin field at `NOISE_SCALE = 0.35` (`EdgeBandEngine.java:25`). The `clusterSize` knob only
   feeds `GradientEngine`, which merely chooses *which* edge material wins a column — invisible when one
   edge block dominates. The cluster slider never touches the field that controls where edge blocks
   appear, so changing it is imperceptible.

**Builder technique this design serves** (from path-building guides and community practice):
irregular/uneven edges made "with a shovel and pickaxe"; coarse dirt as the trampled organic-to-mineral
bridge block; clustered variety with no two identical blocks adjacent; and scattering coarse
dirt/grass/moss *outward* onto surrounding ground to "blur the line between path and nature."

---

## 2. Goals & non-goals

**Goals**
- Rugged edges are achievable at any path width (erosion decoupled from width and from the movable band).
- Direct control over how many edge blocks appear (`edgeCoverage`) and how clustered they are
  (`edgeClusterSize`, actually wired to the scatter field).
- Edge blocks meld outward onto surrounding terrain, thinning with distance (`edgeReach`).
- A guaranteed-solid protected spine at every width, however aggressive the erosion.
- `preview ≡ apply` and determinism preserved; no wire-format change beyond added `PathStyle` JSON fields.
- Pure organic/geometry modules stay Minecraft-free and headlessly unit-tested.

**Non-goals (deferred to later specs)**
- Variable width & taper (per-node width).
- Edge blocks placed *on top of* the path surface.
- A jaggedness / detached-speck dial (single-block flecks beyond the eroded outline).
- Edge decoration pass (lantern posts, fences, flower borders).
- Node editing (insert mid-curve, drag existing, close loop).
- Outward scatter for `CUT_AND_FILL` mode.

---

## 3. The edge control model

Edge knobs move from **fractions of a hidden band** to **absolute blocks** a builder can picture. The
model reorganizes around three ingredients: **shape**, **scatter**, **blend**.

### ① Shape — how rugged the outline is

| Knob | Unit | Default | Behaviour |
|---|---|---|---|
| `edgeErosion` | blocks (0–8) | 1.5 | Depth the edge bites inward / bulges outward. **Not** capped by the band — carves bays several blocks deep. Replaces `edgeRoughness`. |
| `edgeFeatureSize` | blocks (1–16) | 5 | Broad bays (large) vs. fine rugged teeth (small). Unchanged field, unchanged meaning. |
| `coreProtect` | 0–1 | 0.4 | Guaranteed-solid inner spine as a fraction of the half-width. Now a true floor: erosion may chew past the movable band but never past this. Unchanged field. |

### ② Scatter — edge blocks that appear "here and there if at all"

| Knob | Unit | Default | Behaviour |
|---|---|---|---|
| `edgeCoverage` | 0–1 | 0.5 | Baseline density of edge-scatter blocks. 0 = none, 1 = solid shoulder. **New.** |
| `edgeClusterSize` | blocks (1–16) | 4 | Patch size of the scatter; drives the scatter coin field's frequency (`1/edgeClusterSize`). Big = broad clumps, small = fine speckle. **New, and the fix for the dead cluster knob.** |

### ③ Blend — melding into surrounding ground

| Knob | Unit | Default | Behaviour |
|---|---|---|---|
| `blendDepth` | blocks (0–8) | 2 | Inward feather skirt: how far the path fades back to terrain at the rim. Replaces `featherDepth`, now absolute. |
| `edgeReach` | blocks (0–6) | 2 | How far edge blocks scatter **outward onto surrounding terrain**, thinning with distance. **New.** |

Unchanged alongside these: the `core` and `edge` material palettes (per-entry block/weight/noiseScale/
clusterSize) and the core-material `defaultClusterSize`.

---

## 4. Pipeline & component changes

The pure `EditPlanBuilder` pipeline order is unchanged (Spline → sample → snap → mask → shape →
gradient → blend → scatter → `EditPlan`). Edge-stage internals change; each unit keeps one job.

### 4.1 `config/PathStyle` (pure)

- Replace edge scalars `edgeRoughness`, `featherDepth` with `edgeErosion`, `blendDepth`, `edgeCoverage`,
  `edgeClusterSize`, `edgeReach` (all absolute blocks / 0–1), keeping `edgeFeatureSize`, `coreProtect`,
  and both material palettes.
- Update the constructor, getters, `equals`/`hashCode`, `defaults()`, and `neutral()`
  (`neutral` = no erosion, no scatter, no reach, fully protected core, single `dirt_path`).
- `toJson`/`fromJson`: add the new fields. Missing fields on parse fall back to the **new defaults**
  (no fraction→block conversion; old saved styles simply load with default edges).

### 4.2 `plan/EditPlanBuilder` (pure)

- Resolve knobs directly to absolute amplitudes: `erosionAmp = edgeErosion`, `blendDepth`, `edgeReach`
  (no `edgeBandWidth` multiply). `coreRadius = coreProtect × halfWidth` remains the protected spine.
- **Core-protection clamp:** after `EdgeShaper` produces the shaped mask, force every column whose
  *original* signed distance is within the protected core (`original.edgeDistance ≤ −coreRadius`) to
  remain solidly inside the shaped field. This decouples erosion depth from the band while guaranteeing
  the spine can never be eroded into a hole — the invariant holds by construction, not by capping
  amplitude.
- **Broaden the column loop** from `shaped.insideColumns()` to all tracked columns with
  `edgeDistance ≤ edgeReach`, sorted for determinism. Per column, branch:
  1. **In protected core** → path block (`GradientEngine` over the core palette).
  2. **Inside and kept by `BlendEngine`** → path block.
  3. **Dropped inside (feathered) OR outside within `edgeReach`** → ask `EdgeScatterEngine`; place its
     edge block as a `TERRAIN` change on the surface if it returns one, else leave terrain.
- Grow the tracked halo: `halo = ceil(erosionAmp + edgeReach) + 1` so outward columns exist in the mask.
- `CUT_AND_FILL` branch unchanged (no outward scatter; carve walls keep today's behaviour).

### 4.3 `organic/EdgeShaper` (pure)

Mechanism unchanged — it already applies an absolute-block amplitude to the signed distance field. It is
simply fed the uncapped `edgeErosion`. Fine rugged teeth come from small `edgeFeatureSize` (its finest
fractal octave approaches per-block); broad bays from large feature size.

### 4.4 `organic/BlendEngine` (pure)

Mechanism unchanged. `skirtWidth` is now `blendDepth` in absolute blocks (it already accepts a fractional
skirt). Active only when `blendDepth > 0`.

### 4.5 `organic/EdgeBandEngine` → `organic/EdgeScatterEngine` (pure) — the rework

Decoupled from feather and given real density + cluster control. For a candidate column at signed
distance `d`:

- **Candidate band:** `−blendDepth < d ≤ edgeReach` — spans the inner feathered shoulder *and* the
  outward terrain band. (Kept path columns and protected-core columns are never candidates; the builder
  only calls the engine for dropped/outside columns.)
- **Clustered coin:** a seeded field sampled at frequency `1/edgeClusterSize`, so patch size tracks the
  knob (the fix). A separate salt keeps it independent of the material choice.
- **Falloff:** a distance shaping peaking near the rim (`d ≈ 0`) and thinning both inward toward
  `−blendDepth` and outward toward `edgeReach`.
- **Decision:** place an edge block iff `edgeCoverage × falloff(d) > coin(x, z)`. `edgeCoverage = 0`
  short-circuits to no scatter. The kept column's block comes from a `GradientEngine` over the edge
  palette so materials still cluster.
- Returns `Optional<BlockStateRef>`; the builder places it as `ChangeKind.TERRAIN` at the surface Y.

### 4.6 Client UI (Minecraft-bound)

- `ui/PathStylePanel`: rename sliders and add the two new ones — `Edge erosion` (0–8), `Edge feature
  size` (1–16), `Core protect` (0–1), `Edge coverage` (0–1), `Edge cluster` (1–16), `Blend depth` (0–8),
  `Edge reach` (0–6). Group under the existing `OUTSKIRTS · EDGE BLEND` / `ADVANCED` sections.
- `ui/PathStyleEdits`: update `rebuild(...)` and the per-knob setters to the new field set.
- `ui/PathStylePanelInput`: update the `applySlider` target strings to match.

---

## 5. Determinism & multiplayer

Unchanged by design. All new controls live in `PathStyle`, which already serializes to `styleJson` and
rides `EditRequestPacket` to the server. The client preview and the server apply both run the same pure
`EditPlanBuilder` with the same `PathStyle`, so `preview ≡ apply` holds by construction. The operation
seed is still derived from the control points; no seed or block payload crosses the wire. The only wire
change is additional JSON fields in the style blob, which `fromJson` tolerates in both directions.

---

## 6. Testing strategy

Pure modules are unit-tested headlessly with a fake `WorldView` (existing pattern). New/updated tests:

- **Core-protection invariant:** across a sweep of `edgeErosion` and `edgeCoverage`, every column within
  the protected core (`original.edgeDistance ≤ −coreRadius`) is a path block — never dropped, never a
  hole.
- **Erosion depth:** a large `edgeErosion` perturbs columns beyond the old `edgeBandWidth` cap (proves
  decoupling); `edgeErosion = 0` leaves the disc-union outline intact.
- **Coverage monotonicity:** `edgeCoverage = 0` → zero edge-scatter blocks; `= 1` → maximal per falloff;
  increasing coverage never yields fewer scattered columns.
- **Cluster visibility:** two different `edgeClusterSize` values on identical inputs produce different
  scattered-column sets (directly guards the "does nothing" regression).
- **Reach:** `edgeReach > 0` emits `TERRAIN` edge blocks at columns with `d > 0` (outside the original
  rim); `edgeReach = 0` emits none outside.
- **Determinism:** building the same `EditPlan` twice yields byte-identical output.
- **Neutral style:** `PathStyle.neutral()` reproduces the plain single-material, hard-edged path.

`ui/`, `apply/` verified in-game against the definition of done.

---

## 7. Definition of done

- All seven edge knobs exposed in the in-game Path Style panel and driving the preview live.
- A width-3 path with high `edgeErosion` + small `edgeFeatureSize` renders visibly rugged, bitten
  edges, with the spine intact.
- `edgeCoverage` sweeps from a clean path (0) to a scattered shoulder (1); `edgeClusterSize` visibly
  changes clump size; `edgeReach` scatters edge blocks onto surrounding terrain.
- `preview ≡ apply` verified on commit; determinism and core-protection tests green.
- Build + full test suite green.

---

## 8. Risks & mitigations

1. **Tuning "rugged" vs. "noisy."** Small feature size + large erosion can tip from hand-carved into
   static. Mitigation: sensible default clamps and ranges; all knobs live-previewed so tuning is cheap;
   golden expectations pinned by tests.
2. **Preview cost from a larger halo.** `edgeReach` grows the tracked column set. Mitigation: `edgeReach`
   is bounded (0–6) and the mask already pads by the halo; the scatter pass is O(columns).
3. **Old-style migration surprise.** Saved styles load with default edges rather than a converted look.
   Mitigation: documented behaviour; `neutral()`/`defaults()` cover the endpoints; re-save on next edit.
