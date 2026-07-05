package com.lwos.apply;

/** JVM-local server singletons (undo history). Kept out of the packet types so both packets share it. */
public final class LwosServerState {
    public static final UndoHistory UNDO = new UndoHistory();
    private LwosServerState() { }
}
