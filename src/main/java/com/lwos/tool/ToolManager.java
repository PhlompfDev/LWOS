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

    private ToolManager() { }

    public boolean isEnabled() { return enabled; }

    public void toggleEnabled() {
        enabled = !enabled;
        if (!enabled) pathTool.clear(); // leaving builder mode discards the in-progress path
    }

    public void cycle(int dir) {
        ToolType[] all = ToolType.values();
        int i = (selected.ordinal() + Integer.signum(dir) + all.length) % all.length;
        selected = all[i];
    }

    public ToolType selected() { return selected; }

    public boolean isPathToolActive() { return enabled && selected == ToolType.PATH; }

    public PathTool currentPath() { return pathTool; }
}
