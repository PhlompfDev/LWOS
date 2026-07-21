package com.lwos.tool;

/**
 * Client-side session singleton: selected tool, builder-mode enabled flag, and
 * the current Path tool. Pure — no Minecraft imports (spec §3.6). World picking
 * and rendering live in the Forge client layer, which calls into this.
 */
public class ToolManager {
    private static final ToolManager INSTANCE = new ToolManager();

    public static ToolManager get() { return INSTANCE; }

    private boolean enabled = false;
    private ToolType selected = ToolType.PATH;
    private final PathTool pathTool = new PathTool();
    private final TerrainBrushTool brushTool = new TerrainBrushTool();
    private final ShapeTool shapeTool = new ShapeTool();

    private ToolManager() { }

    public boolean isEnabled() { return enabled; }

    public void toggleEnabled() {
        enabled = !enabled;
        if (!enabled) {
            pathTool.clear();  // leaving builder mode discards the in-progress path
            shapeTool.clear(); // ... and any in-progress shape gesture
        }
    }

    public void cycle(int dir) {
        ToolType[] all = ToolType.values();
        int i = (selected.ordinal() + Integer.signum(dir) + all.length) % all.length;
        selected = all[i];
        shapeTool.clear(); // switching tools abandons the gesture (anchors are mode-specific)
    }

    /** Direct selection (wheel hover-pick, spec §1). Same gesture-abandon rule as cycle. */
    public void select(ToolType type) {
        selected = type;
        shapeTool.clear();
    }

    public ToolType selected() { return selected; }

    public boolean isPathToolActive() { return enabled && selected == ToolType.PATH; }

    public PathTool currentPath() { return pathTool; }

    public boolean isTerrainToolActive() { return enabled && selected == ToolType.TERRAIN; }

    public TerrainBrushTool currentBrush() { return brushTool; }

    public boolean isShapeToolActive() { return enabled && selected.shapeMode() != null; }

    public ShapeTool currentShape() { return shapeTool; }

    /** The ShapeMode of the selected tool, or null when a bespoke tool is selected. */
    public com.lwos.shape.ShapeMode activeShapeMode() { return selected.shapeMode(); }
}
