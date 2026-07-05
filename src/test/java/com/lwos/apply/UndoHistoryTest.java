package com.lwos.apply;

import com.lwos.plan.BlockStateRef;
import com.lwos.plan.GridPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UndoHistoryTest {

    private static UndoHistory.BlockSnapshot snap(int x) {
        return new UndoHistory.BlockSnapshot(new GridPos(x, 64, 0), new BlockStateRef("minecraft:grass_block"));
    }

    @Test
    void popReturnsMostRecentCommitThenWalksBack() {
        UndoHistory h = new UndoHistory();
        UUID p = UUID.randomUUID();
        h.push(p, List.of(snap(1)));
        h.push(p, List.of(snap(2)));
        assertEquals(2, h.pop(p).get().get(0).pos().x(), "most recent commit pops first");
        assertEquals(1, h.pop(p).get().get(0).pos().x(), "then the previous one");
        assertTrue(h.pop(p).isEmpty(), "empty stack pops nothing");
    }

    @Test
    void stackIsBoundedToMaxDepth() {
        UndoHistory h = new UndoHistory();
        UUID p = UUID.randomUUID();
        for (int i = 0; i < UndoHistory.MAX_DEPTH + 5; i++) h.push(p, List.of(snap(i)));
        int count = 0;
        while (h.pop(p).isPresent()) count++;
        assertEquals(UndoHistory.MAX_DEPTH, count, "history is bounded; oldest evicted");
    }

    @Test
    void historyIsPerPlayer() {
        UndoHistory h = new UndoHistory();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        h.push(a, List.of(snap(7)));
        assertTrue(h.pop(b).isEmpty(), "one player's undo must not touch another's");
        assertEquals(7, h.pop(a).get().get(0).pos().x());
    }
}
