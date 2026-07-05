package com.lwos.ui;

/** Client-side UI state shared between the panel overlay and its input handler. */
public final class PathStylePanelState {
    private static volatile boolean open = false;
    /** Index of the palette slot targeted by pick-from-world; -1 = none selected. */
    private static volatile int activeSlot = -1;
    /** True while the edit modifier (Left-Ctrl) has freed the cursor for panel interaction. */
    private static volatile boolean editing = false;

    private PathStylePanelState() { }

    public static boolean isOpen() { return open; }
    public static void toggleOpen() { open = !open; if (!open) editing = false; }
    public static void setOpen(boolean v) { open = v; if (!v) editing = false; }

    public static int activeSlot() { return activeSlot; }
    public static void setActiveSlot(int i) { activeSlot = i; }

    public static boolean isEditing() { return editing; }
    public static void setEditing(boolean v) { editing = v; }
}
