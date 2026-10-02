package com.fixmer.mared.gui2.framework.components.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * История undo/redo для EditorDocument.
 *
 * 0.3.0 (Phase D1): вынесена из MaredMultiLineEditBox.
 *
 * API:
 *   history.pushBefore(doc)  — вызвать ДО мутации документа;
 *   history.undo(doc)        — откатить (возвращает true, если что-то было);
 *   history.redo(doc)        — повторить;
 *   history.clear()          — сброс (при setValue).
 */
public final class EditorHistory {

    private static final int MAX_UNDO = 100;

    private final Deque<Entry> undoStack = new ArrayDeque<>(MAX_UNDO);
    private final Deque<Entry> redoStack = new ArrayDeque<>(MAX_UNDO);

    private static final class Entry {
        final String[] lines;
        final int cursorLine;
        final int cursorCol;
        Entry(String[] lines, int cl, int cc) {
            this.lines = lines;
            this.cursorLine = cl;
            this.cursorCol = cc;
        }
    }

    /** Вызывать перед каждой edit-операцией. */
    public void pushBefore(EditorDocument doc) {
        undoStack.push(new Entry(
            doc.snapshotLines(), doc.cursorLine(), doc.cursorCol()));
        if (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        redoStack.clear();
    }

    public boolean undo(EditorDocument doc) {
        if (undoStack.isEmpty()) return false;
        Entry snap = undoStack.pop();
        redoStack.push(new Entry(
            doc.snapshotLines(), doc.cursorLine(), doc.cursorCol()));
        doc.applySnapshot(snap.lines, snap.cursorLine, snap.cursorCol);
        return true;
    }

    public boolean redo(EditorDocument doc) {
        if (redoStack.isEmpty()) return false;
        Entry snap = redoStack.pop();
        undoStack.push(new Entry(
            doc.snapshotLines(), doc.cursorLine(), doc.cursorCol()));
        doc.applySnapshot(snap.lines, snap.cursorLine, snap.cursorCol);
        return true;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}