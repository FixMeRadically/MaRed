package com.fixmer.genesis.startup;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Identity-keyed memoization is valid only for the JDK's immutable WindowsPath. */
public final class RootEquality {
    private RootEquality() {}
    private static final Class<?> DEFAULT = Path.of("").getClass();
    private static final boolean SUPPORTED = DEFAULT.getName().equals("sun.nio.fs.WindowsPath");
    private static final ConcurrentLinkedQueue<State> STATES = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final ThreadLocal<State> LOCAL = ThreadLocal.withInitial(() -> {
        State state = new State();
        if (COUNT.incrementAndGet() <= 256) STATES.add(state);
        return state;
    });
    private static final class State {
        final Object[] left = new Object[16], right = new Object[16];
        final boolean[] result = new boolean[16];
        int next;
        volatile long hits, misses;
    }
    public static boolean same(Path left, Object right) {
        if (!SUPPORTED || left == null || left.getClass() != DEFAULT || right == null || right.getClass() != DEFAULT)
            return left.equals(right);
        return memoized(left, right);
    }
    static boolean memoized(Path left, Object right) {
        State state = LOCAL.get();
        // One pair per archive root, retained by UnionFileSystem. Never cache by path text.
        for (int i = 0; i < 16; i++) if (state.left[i] == left && state.right[i] == right) {
            state.hits++; return state.result[i];
        }
        boolean result = left.equals(right);
        int index = state.next++ & 15;
        state.left[index] = left; state.right[index] = right; state.result[index] = result;
        state.misses++;
        return result;
    }
    public static long[] counts() {
        long hits = 0, misses = 0;
        for (State state : STATES) { hits += state.hits; misses += state.misses; }
        return new long[]{hits, misses};
    }
}
