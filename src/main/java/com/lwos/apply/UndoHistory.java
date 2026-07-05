package com.lwos.apply;

import com.lwos.plan.BlockStateRef;
import com.lwos.plan.GridPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, per-player stack of committed-block snapshots for undo. Server-side, in-memory,
 * per-session (not persisted across restart — see design §10). Each Enter-commit pushes the prior
 * states it overwrote; undo pops the most recent and restores it, walking back on repeat.
 */
public final class UndoHistory {

    public static final int MAX_DEPTH = 16;

    /** One overwritten block: its position and the state that was there before the edit. */
    public record BlockSnapshot(GridPos pos, BlockStateRef priorState) { }

    private final Map<UUID, Deque<List<BlockSnapshot>>> stacks = new ConcurrentHashMap<>();

    public void push(UUID player, List<BlockSnapshot> commit) {
        Deque<List<BlockSnapshot>> stack = stacks.computeIfAbsent(player, k -> new ArrayDeque<>());
        stack.push(List.copyOf(commit));
        while (stack.size() > MAX_DEPTH) stack.removeLast(); // evict oldest
    }

    public Optional<List<BlockSnapshot>> pop(UUID player) {
        Deque<List<BlockSnapshot>> stack = stacks.get(player);
        if (stack == null || stack.isEmpty()) return Optional.empty();
        return Optional.of(stack.pop());
    }
}
