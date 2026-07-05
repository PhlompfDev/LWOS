package com.lwos.ui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * A 30x30 block-icon slot in the palette editor: renders the 3D item icon and an active-slot
 * highlight ring. Hit-testing is a pure rectangle check. Clicking a slot selects it (target of
 * pick-from-world and the search modal, handled by the panel input).
 */
public final class BlockSlotWidget {

    public static final int SIZE = 30;
    private static final int BG        = 0x22FFFFFF;
    private static final int BORDER    = 0x33FFFFFF;
    private static final int ACTIVE    = 0xFF7CD3FF;

    private final int x, y;

    public BlockSlotWidget(int x, int y) { this.x = x; this.y = y; }

    public int x() { return x; }
    public int y() { return y; }

    public boolean isOver(double mx, double my) {
        return mx >= x && mx <= x + SIZE && my >= y && my <= y + SIZE;
    }

    public void render(GuiGraphics g, ItemStack icon, boolean active) {
        g.fill(x, y, x + SIZE, y + SIZE, BG);
        int border = active ? ACTIVE : BORDER;
        // 1px frame
        g.fill(x, y, x + SIZE, y + 1, border);
        g.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, border);
        g.fill(x, y, x + 1, y + SIZE, border);
        g.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, border);
        if (icon != null && !icon.isEmpty()) {
            g.renderItem(icon, x + 7, y + 7); // 16x16 item centered in the 30px slot
        }
    }
}
