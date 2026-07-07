# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

LWOS Builder Tools — a Minecraft Forge 1.20.1 (Forge 47.4.10, official mappings, mod id `lwos`) client+server mod providing interactive path-building tools with live preview.

> **Knowledge vault**: a distilled overview of this project (architecture, invariants, status, roadmap) lives at `C:\Users\monst\Desktop\All\AI\Brain\wiki\projects\minecraft\lwos-builder-tools\` (hub: `lwos-builder-tools.md`; per-system subpages for rendering, networking, UI, known problems, future ideas). Read it for fast orientation. If you learn something durable that isn't there, file it into the vault yourself automatically (following `Brain\CLAUDE.md`'s schema — living sections + log entry); don't ask or offer first.

## Build & test

Gradle must run on **JDK 17** (ForgeGradle rejects newer JDKs; the machine default is newer). `gradle.properties` pins `org.gradle.java.home` to `C:/Program Files/Java/jdk-17`, but when running from Git Bash also export it before every command as belt-and-braces:

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17"
./gradlew build                                   # full build + all tests
./gradlew test                                    # tests only
./gradlew test --tests "com.lwos.plan.EditPlanBuilderEdgeScatterTest"   # single class
./gradlew runClient                               # dev client (working dir: run/)
./gradlew runServer                               # dev server
```

Use Git Bash for gradle commands. Commits use conventional-commit subjects (`feat(organic): …`, `test(config,plan): …`).

## Architecture

The codebase is split by a **purity boundary** that every change must respect:

- **Pure core** — `com.lwos.plan`, `config`, `geometry`, `organic` MUST NOT import `net.minecraft.*`, `com.mojang.*`, or `net.minecraftforge.*`. All path math, styling, and plan generation lives here, fully unit-testable without Minecraft.
- **Minecraft-bound** — `client`, `apply`, `apply.net`, `tool`, `ui` may use Forge/MC types.

### The plan pipeline (heart of the mod)

`EditPlanBuilder.build(controlPoints, spacing, width, WorldView, TerrainMode, PathStyle)` → `EditPlan` (a map of `GridPos` → `PlannedChange`). Stages: `PathSampler` samples a Catmull-Rom spline → `TerrainSampler` snaps to the surface → `PathMask` builds a signed-distance column field (with a halo wide enough for outward effects) → `EdgeShaper` wobbles the edge with seeded noise → a `protectCore` clamp guarantees the spine can never be eroded away → per column, `GradientEngine` picks core materials, `BlendEngine` feathers the inside edge, `EdgeScatterEngine` scatters edge-palette blocks across the feather band and outward `edgeReach` band. `TerrainMode.CUT_AND_FILL` keeps its carved footprint and never scatters outward.

Two invariants bind all of this:

- **Determinism:** identical inputs + identical `WorldView` answers must produce a byte-identical `EditPlan`. No wall-clock, no unseeded `Random`; the operation seed is derived from the control points and salted per engine. Never put the seed on the wire.
- **preview == apply:** the client preview (`client/PathRenderer` + `PreviewPlanCache` over `ForgeWorldView`) and the server apply (`apply/net/EditRequestPacket` over `ServerWorldView`) call the *same* `build(...)`. Every styling control must be a field on `PathStyle`, serialized in the packet's `styleJson` — never a new packet field.

### Client → server flow

`LwosKeyMappings`/`LwosInputHandler` drive `tool/ToolManager` + `PathTool` (node placement); overlays (`ModeHudOverlay`, `ToolWheelOverlay`) and `PreviewRenderer` show state. Committing sends `EditRequestPacket` (control points + styleJson); the server re-derives the plan and `apply/PlacementEngine` mutates the world. `UndoHistory`/`LwosServerState` back Ctrl+Z undo and Ctrl+Y redo.

### Access gate

`com.lwos.LwosAccess` is the single allowlist (player names) for the builder tools. The client uses it as a UX gate, but authority lives server-side: every packet handler re-checks it, so keep both checks when touching packets.

### Styling

`config/PathStyle` is the immutable style (core/edge palettes + absolute-unit edge knobs: `edgeErosion`, `blendDepth`, `edgeCoverage`, `edgeClusterSize`, `edgeReach`, `edgeFeatureSize`, `coreProtect`). `fromJson` is lenient by contract: unknown/legacy keys ignored, missing scalars and core palette default, missing edge palette stays empty (= no scatter). `StyleManager` persists presets; `ui/PathStylePanel(+Input)` edits the active style via `PathStyleEdits`.

## Design docs

`docs/superpowers/specs/` and `docs/superpowers/plans/` hold the design specs and implementation plans for major features (dated files); `docs/bug_fix/` holds post-mortems. Check the most recent ones before reworking a subsystem.

## Always push finished work

When a unit of work is complete and verified (build + tests green), push it to the
branch where my working files live — normally `main`, or whichever branch holds the real source. Concretely: commit the change, then
`git push origin <current-branch>`. Don't leave finished, verified commits sitting
only on my local machine, and don't wait to be told to push each time — this is
standing authorization to push completed work. (Still branch first if I explicitly
ask for a PR, and never force-push or push half-done/failing work.)

## Offloading large, low-value inputs

Before reading a large stack trace, source file, or Java package just to
summarize/explain it, prefer the `offload` MCP tools
(`explain_stack_trace`, `summarize_file`, `summarize_package`,
`explain_compiler_error`, `draft_javadoc`). They run a local model to keep
raw dumps out of context and off the usage cap. If a result comes back with
`fallback: true` or low confidence, do the task yourself.
If the offload tools are deferred, load them before deciding you can't use them.
