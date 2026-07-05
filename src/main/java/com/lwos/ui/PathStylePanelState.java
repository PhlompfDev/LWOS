package com.lwos.ui;

/** Client-side UI state shared between the panel overlay and its input handler. */
public final class PathStylePanelState {
    private static volatile boolean open = false;
    /** Index of the palette slot targeted by pick-from-world; -1 = none selected. */
    private static volatile int activeSlot = -1;
    /** True while the edit modifier (Left-Ctrl) has freed the cursor for panel interaction. */
    private static volatile boolean editing = false;
    /** Vertical scroll of the panel body, in gui-scaled pixels; clamped to [0, maxScroll]. */
    private static volatile int scrollOffset = 0;
    /** Max scroll for the current frame's content; republished each render (§Task 2). */
    private static volatile int maxScroll = 0;

    private PathStylePanelState() { }

    public static boolean isOpen() { return open; }
    public static void toggleOpen() { open = !open; if (!open) { editing = false; scrollOffset = 0; } }
    public static void setOpen(boolean v) { open = v; if (!v) { editing = false; scrollOffset = 0; } }

    public static int activeSlot() { return activeSlot; }
    public static void setActiveSlot(int i) { activeSlot = i; }

    public static boolean isEditing() { return editing; }
    public static void setEditing(boolean v) { editing = v; }

    public static int scrollOffset() { return scrollOffset; }
    public static int maxScroll() { return maxScroll; }

    public static void setScrollOffset(int v) { scrollOffset = Math.max(0, Math.min(v, maxScroll)); }
    public static void addScroll(int delta) { setScrollOffset(scrollOffset + delta); }

    /** Publishes this frame's content height budget and re-clamps the offset into the new range. */
    public static void setMaxScroll(int m) {
        maxScroll = Math.max(0, m);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }
}
