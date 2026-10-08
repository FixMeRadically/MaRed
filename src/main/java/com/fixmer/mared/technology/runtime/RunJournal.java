package com.fixmer.mared.technology.runtime;

import java.util.ArrayDeque;
import java.util.List;

/** Thread-safe bounded run output; no GUI calls or per-line client task queue. */
public final class RunJournal {
    public record Entry(long sequence, long timeMillis, String message) {}
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long sequence;
    private long revision;
    public synchronized void add(String message) {
        String value = message == null ? "" : message;
        if (value.length() > 4096) value = value.substring(0, 4096) + "…";
        entries.addLast(new Entry(++sequence, System.currentTimeMillis(), value));
        revision++;
        while (entries.size() > 512) entries.removeFirst();
    }
    public synchronized List<Entry> snapshot() { return List.copyOf(entries); }
    public synchronized long revision() { return revision; }
    public synchronized void clear() { entries.clear(); revision++; }
    public synchronized long dropped() { return sequence - entries.size(); }
}
