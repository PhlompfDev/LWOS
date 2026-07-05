package com.lwos.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathStylePanelScrollTest {

    @BeforeEach
    void reset() {
        PathStylePanelState.setOpen(false); // resets scroll to 0
        PathStylePanelState.setMaxScroll(0);
    }

    @Test
    void offsetClampsToMaxScroll() {
        PathStylePanelState.setMaxScroll(100);
        PathStylePanelState.setScrollOffset(250);
        assertEquals(100, PathStylePanelState.scrollOffset(), "offset cannot exceed maxScroll");
    }

    @Test
    void offsetClampsToZero() {
        PathStylePanelState.setMaxScroll(100);
        PathStylePanelState.setScrollOffset(-40);
        assertEquals(0, PathStylePanelState.scrollOffset(), "offset cannot go negative");
    }

    @Test
    void shrinkingMaxScrollPullsOffsetIntoRange() {
        PathStylePanelState.setMaxScroll(100);
        PathStylePanelState.setScrollOffset(90);
        PathStylePanelState.setMaxScroll(50); // content got shorter this frame
        assertEquals(50, PathStylePanelState.scrollOffset(), "offset re-clamped when maxScroll shrinks");
    }

    @Test
    void closingThePanelResetsScroll() {
        PathStylePanelState.setMaxScroll(100);
        PathStylePanelState.setScrollOffset(75);
        PathStylePanelState.setOpen(false);
        assertEquals(0, PathStylePanelState.scrollOffset(), "closing the panel resets scroll to top");
    }
}
