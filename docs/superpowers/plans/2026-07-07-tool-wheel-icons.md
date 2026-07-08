# Tool Wheel Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the tool wheel's overflowing text labels with ink pictogram icons, mark the selected tool with a wax scribble ring + centered wax name, and delete the leftover black dim box.

**Architecture:** Extends the existing journal-UI pipeline: `tools/texgen/generate.py` gains six new regions in `widgets.png` (five 16×16 tool icons indexed by `ToolType.ordinal()`, one 24×24 wax ring); `JournalTheme` gains the atlas constants; `ToolWheelOverlay.render` swaps label drawing for `blitRegion` icon draws. No new blit helpers, no logic changes.

**Tech Stack:** Minecraft Forge 1.20.1 (official mappings), Java 17, GuiGraphics blits; Python 3 + Pillow (already installed) for texture generation.

**Spec:** `docs/superpowers/specs/2026-07-07-tool-wheel-icons-design.md` (approved 2026-07-07).

## Global Constraints

- Gradle MUST run on JDK 17. Every gradle command from Git Bash: `export JAVA_HOME="/c/Program Files/Java/jdk-17"` first.
- Conventional-commit subjects (`feat(ui): …`, `chore(texgen): …`).
- Existing `widgets.png` atlas regions MUST NOT move; new regions only, per the reskin asset contract. `py -3 tools/texgen/generate.py --verify` must pass after regeneration.
- Text on parchment is drawn WITHOUT shadow (`drawString(..., false)`).
- Selection/scroll logic and `ToolType` are untouched.
- GuiGraphics blits: only the 11-arg overload (already the rule; `JournalTheme.blitRegion` wraps it).

---

### Task 1: Texgen — five ink tool icons + wax scribble ring

**Files:**
- Modify: `tools/texgen/generate.py` (inside `gen_widgets`, append before `return img`)
- Regenerate: `src/main/resources/assets/lwos/textures/gui/journal/widgets.png`

**Interfaces:**
- Consumes: existing helpers `value_noise`, `INK`, `WAX` palette constants, the `d = ImageDraw.Draw(img)` handle already in scope in `gen_widgets`.
- Produces: `widgets.png` regions — tool icon *i* at `(0 + i*16, 64)` 16×16 in `ToolType.ordinal()` order (PATH, LINE, CIRCLE, FILL, TERRAIN_BLEND); wax scribble ring at `(80, 64)` 24×24. Consumed by Task 2's `JournalTheme` constants.

- [ ] **Step 1: Add the icon drawing code**

In `tools/texgen/generate.py`, inside `gen_widgets(rng)`, immediately before the final `return img`, insert:

```python
    # --- tool icons (0,64)..(80,80), 16x16 each, ToolType.ordinal() order ---
    # PATH (0,64): wavy dotted trail
    ox, oy = 0, 64
    for i in range(0, 14, 3):
        yy = 8 + round(3 * math.sin(i * 0.9))
        d.line([(ox + 1 + i, oy + yy), (ox + 2 + i, oy + yy)], fill=INK + (255,))

    # LINE (16,64): straight diagonal rule with perpendicular end ticks
    ox, oy = 16, 64
    d.line([(ox + 2, oy + 13), (ox + 13, oy + 2)], fill=INK + (255,))
    d.line([(ox + 1, oy + 11), (ox + 4, oy + 14)], fill=INK + (220,))
    d.line([(ox + 11, oy + 1), (ox + 14, oy + 4)], fill=INK + (220,))

    # CIRCLE (32,64): compass circle with center prick
    ox, oy = 32, 64
    d.ellipse([ox + 2, oy + 2, ox + 13, oy + 13], outline=INK + (255,))
    d.point((ox + 8, oy + 8), fill=INK + (255,))

    # FILL (48,64): bucket with pour stream and drop
    ox, oy = 48, 64
    d.polygon([(ox + 3, oy + 5), (ox + 10, oy + 5), (ox + 9, oy + 12), (ox + 4, oy + 12)],
              outline=INK + (255,))
    d.arc([ox + 3, oy + 1, ox + 10, oy + 8], 180, 360, fill=INK + (220,))
    d.line([(ox + 11, oy + 6), (ox + 12, oy + 9)], fill=INK + (255,))
    d.point((ox + 12, oy + 12), fill=INK + (255,))

    # TERRAIN_BLEND (64,64): two overlapping hills, ground line, blend hatching
    ox, oy = 64, 64
    d.arc([ox + 1, oy + 6, ox + 9, oy + 18], 180, 360, fill=INK + (255,))
    d.arc([ox + 6, oy + 8, ox + 14, oy + 20], 180, 360, fill=INK + (255,))
    d.line([(ox + 1, oy + 12), (ox + 14, oy + 12)], fill=INK + (150,))
    for hx in (4, 7, 10):
        d.line([(ox + hx, oy + 9), (ox + hx + 2, oy + 11)], fill=INK + (140,))

    # --- wax scribble ring (80,64) 24x24: hand-wobbled 1.5-turn circling ---
    rcx, rcy = 80 + 11.5, 64 + 11.5
    for i in range(540):
        ang = math.pi * i / 180.0
        rr = 9.0 + (rng.random() - 0.5) * 1.8 + (i / 540.0) * 0.8
        d.point((round(rcx + rr * math.cos(ang)), round(rcy + rr * math.sin(ang))),
                fill=WAX + (230,))
```

(The scribble ring consumes `rng` draws *after* every existing region is painted, so all existing pixels are byte-identical; only the new regions change.)

- [ ] **Step 2: Regenerate and verify determinism**

Run: `py -3 tools/texgen/generate.py`
Expected: four `wrote ...` lines.
Run: `py -3 tools/texgen/generate.py --verify`
Expected: `OK` for all four sheets, exit code 0.

- [ ] **Step 3: Eyeball the sheet (the test for this task)**

Open `src/main/resources/assets/lwos/textures/gui/journal/widgets.png` (Read the PNG). Verify: five distinct ink pictograms across y=64, a wax-red circling at (80,64), and every pre-existing region (track, knob, chips, slot, field, stitches, strap, buttons) visually unchanged in its original position.

- [ ] **Step 4: Commit**

```bash
git add tools/texgen/generate.py src/main/resources/assets/lwos/textures/gui/journal/widgets.png
git commit -m "chore(texgen): ink tool icons and wax scribble ring in widgets.png"
```

---

### Task 2: JournalTheme constants + ToolWheelOverlay icon rendering

**Files:**
- Modify: `src/main/java/com/lwos/ui/theme/JournalTheme.java` (constants only)
- Modify: `src/main/java/com/lwos/client/ToolWheelOverlay.java` (render body + radius constant)

**Interfaces:**
- Consumes: `widgets.png` regions from Task 1; existing `JournalTheme.blitRegion(GuiGraphics g, int u, int v, int rw, int rh, int x, int y)`, `JournalTheme.WAX`; `ToolManager.get().selected()`, `ToolType.values()`, `ToolType.displayName()`.
- Produces: `JournalTheme.TOOL_ICON_U/V/SIZE`, `SEL_RING_U/V/SIZE` (public static final int). No API change on the overlay.

- [ ] **Step 1: Add the atlas constants to JournalTheme**

In `JournalTheme.java`, after the `STRAP_*` constants line, add:

```java
    public static final int TOOL_ICON_U = 0, TOOL_ICON_V = 64, TOOL_ICON_SIZE = 16;
    public static final int SEL_RING_U = 80,  SEL_RING_V = 64, SEL_RING_SIZE = 24;
```

- [ ] **Step 2: Rewrite the ToolWheelOverlay drawing**

In `ToolWheelOverlay.java`:
- Replace the constant `private static final int RADIUS = 60;` with `private static final int ICON_RADIUS = 40;` (icons must sit fully inside the compass ring at r=53).
- Replace everything in `render` after the `int cy = screenHeight / 2;` line with:

```java
        // Parchment compass disc (128x128, 1:1) — no dim box; the disc carries its own contrast.
        RenderSystem.enableBlend();
        g.blit(JournalTheme.TOOL_WHEEL, cx - 64, cy - 64, 128, 128, 0.0F, 0.0F, 128, 128, 128, 128);
        RenderSystem.disableBlend();

        ToolType[] tools = ToolType.values();
        int n = tools.length;
        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i / n) - Math.PI / 2; // first tool at top
            int x = cx + (int) Math.round(Math.cos(angle) * ICON_RADIUS);
            int y = cy + (int) Math.round(Math.sin(angle) * ICON_RADIUS);
            if (tools[i] == tm.selected()) {
                JournalTheme.blitRegion(g, JournalTheme.SEL_RING_U, JournalTheme.SEL_RING_V,
                        JournalTheme.SEL_RING_SIZE, JournalTheme.SEL_RING_SIZE, x - 12, y - 12);
            }
            JournalTheme.blitRegion(g, JournalTheme.TOOL_ICON_U + i * JournalTheme.TOOL_ICON_SIZE,
                    JournalTheme.TOOL_ICON_V, JournalTheme.TOOL_ICON_SIZE, JournalTheme.TOOL_ICON_SIZE,
                    x - 8, y - 8);
        }
        String name = tm.selected().displayName();
        g.drawString(font, name, cx - font.width(name) / 2, cy + 6, JournalTheme.WAX, false);
```

(The `0x66000000` dim fill and the "Scroll to change tool" hint are gone. Icon *i* reads its atlas cell by ordinal. The wax ring draws *behind* the selected icon. The name never overflows: the widest, "Terrain Blend", is ~70px < ring diameter 106px.)

- [ ] **Step 3: Compile**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/lwos/ui/theme/JournalTheme.java src/main/java/com/lwos/client/ToolWheelOverlay.java
git commit -m "feat(ui): tool wheel ink icons with wax selection ring, drop dim box"
```

---

### Task 3: Full verification and push

**Files:** none — verification only.

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Full build + tests**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew build`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 2: Manual playtest (spec verification list)**

Run: `export JAVA_HOME="/c/Program Files/Java/jdk-17" && ./gradlew runClient`, hold Alt in builder mode:

1. No black box behind the wheel.
2. Five ink icons, all fully inside the compass ring, at GUI scales 1–4.
3. Alt+scroll moves the wax scribble ring and updates the centered wax name.
4. Nothing renders outside the torn disc edge.

If an icon reads poorly, fix it in `tools/texgen/generate.py`, regenerate, re-verify — code should not change for art problems.

- [ ] **Step 3: Push**

```bash
git push origin main
```
