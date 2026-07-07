package com.lwos.ui.components;

import com.lwos.ui.theme.JournalTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * A 30x30 block-icon slot in the palette editor: renders the 3D item icon and an active-slot
 * highlight ring. Hit-testing is a pure rectangle check. Clicking a slot selects it (target of
 * pick-from-world and the search modal, handled by the panel input).
 */
public final class BlockSlotWidget {

    public static final int SIZE = 30;

    private final int x, y;

    public BlockSlotWidget(int x, int y) { this.x = x; this.y = y; }

    public int x() { return x; }
    public int y() { return y; }

    public boolean isOver(double mx, double my) {
        return mx >= x && mx <= x + SIZE && my >= y && my <= y + SIZE;
    }

    public void render(GuiGraphics g, ItemStack icon, boolean active) {
        JournalTheme.blitWidgetNineSlice(g, JournalTheme.SLOT_U, JournalTheme.SLOT_V,
                JournalTheme.SLOT_W, JournalTheme.SLOT_H, JournalTheme.SLOT_INSET, x, y, SIZE, SIZE);
        if (active) {
            // wax selection ring just inside the ink frame
            g.fill(x + 1, y + 1, x + SIZE - 1, y + 2, JournalTheme.WAX);
            g.fill(x + 1, y + SIZE - 2, x + SIZE - 1, y + SIZE - 1, JournalTheme.WAX);
            g.fill(x + 1, y + 1, x + 2, y + SIZE - 1, JournalTheme.WAX);
            g.fill(x + SIZE - 2, y + 1, x + SIZE - 1, y + SIZE - 1, JournalTheme.WAX);
        }
        if (icon != null && !icon.isEmpty()) {
            g.renderItem(icon, x + 7, y + 7); // 16x16 item centered in the 30px slot
        }
    }
}
